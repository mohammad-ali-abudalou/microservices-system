# Use a lightweight runtime-only version
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the JAR file that we built manually in the previous step
COPY build/libs/*.jar app.jar

# Port (make sure to change it for each service: 8888 for config, 8761 for discovery, etc.)
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]