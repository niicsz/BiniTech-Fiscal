FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

ENV PORT=8083
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:${PORT}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
