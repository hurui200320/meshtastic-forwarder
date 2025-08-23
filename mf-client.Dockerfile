# docker build . -t test -f mf-client.Dockerfile
# docker run TODO
# TODO: docker compose?

# Build stage
FROM gradle:8-jdk21 AS builder
WORKDIR /code
COPY . .
RUN gradle clean :client:bootJar --no-daemon

# Run stage
FROM azul/zulu-openjdk:21

# install curl for health check
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /code/client/build/libs/*.jar app.jar
EXPOSE 8081
HEALTHCHECK CMD curl -fs http://localhost:8081/actuator/health

ENTRYPOINT ["java", "-jar", "app.jar"]



