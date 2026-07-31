# =========================== ETAPA 1: BUILD =============================
# Se compila adentro del contenedor para no depender de la versión de Java ni
# de Gradle que tengas instalada en tu máquina.
FROM eclipse-temurin:25-jdk AS builder

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

# =========================== ETAPA 2: RUNTIME ===========================
# Imagen de Java 25 de Oracle. Solo lleva el .jar, no el código ni Gradle,
# por eso la imagen final es mucho más liviana que la de build.
FROM container-registry.oracle.com/java/openjdk:25-oraclelinux9

WORKDIR /app

# No correr como root: si alguien logra ejecutar código dentro del contenedor,
# queda limitado a un usuario sin privilegios.
RUN useradd --system --create-home --shell /sbin/nologin spring
USER spring

# Se copia por nombre exacto y no con *.jar. El plugin de Spring Boot genera
# dos jars (el ejecutable y uno "-plain"), y con el comodín Docker encontraría
# dos archivos para un destino único y fallaría. En build.gradle se desactivó
# el "-plain" y se le fijó el nombre app.jar al ejecutable.
COPY --from=builder --chown=spring:spring /app/build/libs/app.jar app.jar

EXPOSE 8080

# El puerto y la base salen de las variables de entorno que pone compose.yml,
# así que la misma imagen sirve para cualquier entorno.
ENTRYPOINT ["java", "-jar", "app.jar"]
