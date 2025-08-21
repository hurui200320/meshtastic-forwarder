# docker build . -t test -f mf-server.Dockerfile
# docker run --rm -e MESHTASTIC_SERVER_RWTOKENS=token1,token2 -p 8080:8080 --device=/dev/ttyACM0 -e MESHTASTIC_CLIENT_PORTURI=serial:///dev/ttyACM0 test
# TODO: docker compose?

# Build stage
FROM gradle:8-jdk21 AS builder
WORKDIR /code
COPY . .
RUN gradle clean :server:bootJar --no-daemon

# Run stage
FROM azul/zulu-openjdk:21
WORKDIR /app
COPY --from=builder /code/server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
