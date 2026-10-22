# --- Etapa 1: compilar ---------------------------------------------------------
# Necesita el JDK y Maven; ninguna de las dos cosas viaja a la imagen final.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# 1) Dependencias. Se copia solo lo que define el proyecto (no el código) para que esta capa
#    se reutilice mientras no cambie el pom.xml: descargar Maven y las librerías es lo más lento.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --quiet dependency:go-offline

# 2) Código. Es lo que más cambia, por eso va después.
#    Los tests no se ejecutan aquí: necesitan Docker (Testcontainers) y ya se ejecutan fuera.
COPY src src
RUN ./mvnw --batch-mode --quiet package -DskipTests

# --- Etapa 2: ejecutar ---------------------------------------------------------
# Solo el JRE y el jar: la imagen final no lleva compilador, ni Maven, ni el código fuente.
FROM eclipse-temurin:21-jre

# curl solo para el HEALTHCHECK; la imagen base no lo trae.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    # Usuario sin privilegios: el servicio no necesita ser root. Sin fijar el UID: la imagen
    # base ya trae un usuario con el 1000.
    && useradd --create-home app

WORKDIR /app

# "backend-*.jar" no coincide con el .jar.original que genera Spring Boot al empaquetar.
COPY --from=build --chown=app:app /build/target/backend-*.jar app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
