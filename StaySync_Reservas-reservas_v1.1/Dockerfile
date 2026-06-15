# ─────────────────────────────────────────────────────────────────────────────
# StaySync — reservas-service  |  Puerto 8083
# ─────────────────────────────────────────────────────────────────────────────

# ── Etapa 1: Compilación ──────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Etapa 2: Imagen de ejecución ──────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S staysync && adduser -S staysync -G staysync

WORKDIR /app

COPY --from=build /app/target/reservas-service-1.0.0.jar app.jar
RUN chown staysync:staysync app.jar

USER staysync

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
