# syntax=docker/dockerfile:1

# ------------------------------------------------------------------
# Stage 1: build
# Maven + JDK 25 (el pom fija java.version=25; un JDK menor falla con
# "release version 25 not supported").
# ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build

# Capa de dependencias: sólo se invalida si cambia el pom.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests clean package \
    && mv target/*.jar /build/app.jar

# ------------------------------------------------------------------
# Stage 2: runtime
# ------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Usuario sin privilegios.
RUN addgroup -S app && adduser -S -G app app

COPY --from=build --chown=app:app /build/app.jar /app/app.jar

USER app

ENV SERVER_PORT=8050 \
    TZ=UTC \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

EXPOSE 8050

# Actuator expone health (management.endpoints.web.exposure.include=health,info).
# wget viene con busybox en alpine: no hace falta instalar curl.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
