FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create config directory to ensure no "File not found" error occurs
RUN mkdir -p /app/config-repo

COPY build/libs/*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]