
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
COPY src src

# --mount=type=cache guarda el caché de Gradle (~/.gradle) entre builds, así no
# se vuelven a descargar todas las dependencias cada vez.
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

COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]