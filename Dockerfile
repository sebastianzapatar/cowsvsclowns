# =========================== ETAPA 1: BUILD =============================
# Se compila adentro del contenedor para no depender de la versión de Java ni
# de Gradle que tengas instalada en tu máquina.
#
# El builder es Alpine a propósito: el JRE recortado que arma la etapa 2 se
# genera con ESTE JDK, y un runtime hecho con glibc no arranca sobre Alpine
# (que usa musl). Las tres etapas tienen que compartir la misma libc.
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# 1) Primero SOLO los archivos de build: Docker cachea esta capa y no la vuelve
#    a ejecutar mientras no toques build.gradle.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# 2) Después el código fuente, para que al cambiar una clase no se invaliden
#    las capas anteriores.
#    Este orden es lo que hace que "docker compose down && up" sea rápido: si
#    no tocaste el código, Docker reutiliza todas las capas; si lo tocaste,
#    solo se rehace de acá para abajo, sin volver a bajar las dependencias.
COPY src src

# --mount=type=cache guarda el caché de Gradle (~/.gradle) entre builds, así no
# se vuelven a descargar todas las dependencias cada vez. Ese caché vive en
# Docker y sobrevive a "docker compose down".
# -x test omite los tests en la imagen (se corren aparte con ./gradlew test).
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon build -x test

# Spring Boot sabe partir su propio jar en capas. Sin --launcher el resultado
# son dos piezas: las librerías sueltas en dependencies/lib/ (57 MB, cambian
# solo si tocás build.gradle) y un jar "delgado" de 80 KB en application/ que
# lleva tu código y un Class-Path en el manifiesto apuntando a lib/.
#
# Copiadas al runtime como capas separadas, un redespliegue a Render solo sube
# esos 80 KB en vez de los 57 MB del jar completo.
RUN java -Djarmode=tools -jar build/libs/app.jar extract --layers --destination extracted

# ==================== ETAPA 2: JRE A LA MEDIDA (jlink) ==================
# Acá está el ahorro grande. La imagen anterior usaba un JDK completo de Oracle
# sobre Oracle Linux: 1.16 GB, de los cuales ~400 MB eran una capa de "dnf
# update" y el resto un JDK entero (con compilador, jshell, herramientas de
# depuración...) del que la app en runtime no usa casi nada.
#
# jlink arma un Java que trae SOLO los módulos que se listan abajo.
FROM eclipse-temurin:25-jdk-alpine AS jre-builder

# Por qué esta lista y no la que calcula "jdeps": jdeps sigue las referencias
# escritas en el bytecode, y Spring resuelve casi todo por reflexión en tiempo
# de ejecución. Sobre este jar además falla, porque los jars de Jakarta son
# modulares y arrastran dependencias que no están en el classpath. Una lista
# generada así compila bien y revienta en producción.
#
# Los que no son obvios:
#   java.desktop      -> NO es por gráficos: Spring usa java.beans.Introspector
#                        para resolver los beans. Sin esto no arranca.
#   java.sql          -> JDBC, o sea Postgres.
#   java.transaction.xa -> lo exige Hibernate.
#   jdk.unsupported   -> sun.misc.Unsafe, que usan Netty, Hibernate y otros.
#   jdk.crypto.ec     -> TLS por curva elíptica. Render (y Neon, Supabase, RDS)
#                        obligan a conectarse a Postgres por SSL: sin este
#                        módulo la app arranca local y falla en la nube con un
#                        handshake roto. Es el que más caro sale olvidar.
#   java.naming       -> JNDI, lo pide el pool de conexiones.
RUN jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.httpserver,jdk.jfr,jdk.management,jdk.naming.dns,jdk.unsupported,jdk.zipfs \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --compress=zip-9 \
      --output /javaruntime

# =========================== ETAPA 3: RUNTIME ===========================
# Alpine pelado (~8 MB). No lleva JDK, ni Gradle, ni el código fuente: solo el
# JRE recortado de la etapa 2 y las capas del jar.
FROM alpine:3.21

# Alpine no trae los certificados raíz, y sin ellos falla la validación del
# certificado del Postgres gestionado al conectarse por SSL.
RUN apk add --no-cache ca-certificates tzdata

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-builder /javaruntime ${JAVA_HOME}

WORKDIR /app

# No correr como root: si alguien logra ejecutar código dentro del contenedor,
# queda limitado a un usuario sin privilegios. En Alpine se usa adduser de
# BusyBox, que no tiene las mismas banderas que el useradd de las distros GNU.
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring

# El orden importa: primero lo que casi nunca cambia (las librerías), después
# tu código. Así al redesplegar Docker reutiliza la capa pesada y solo rehace
# la de 80 KB.
COPY --from=builder --chown=spring:spring /app/extracted/dependencies/lib ./lib
COPY --from=builder --chown=spring:spring /app/extracted/application/app.jar ./app.jar

EXPOSE 8080

# Se arranca el jar delgado directamente: su manifiesto ya trae el Class-Path
# con todos los jars de lib/, resueltos relativo a la ubicación del jar. No hace
# falta el JarLauncher de Spring Boot, que solo aplica al jar sin extraer.
#
# MaxRAMPercentage es lo que evita el problema clásico en Render: la JVM calcula
# su heap contra la memoria que ve, y si el contenedor tiene un límite más bajo
# el kernel mata el proceso por OOM (aparece como un reinicio sin explicación).
# Con esto el heap se ajusta solo al límite real del contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
