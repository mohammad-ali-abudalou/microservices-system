FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 9000
# Shorter JVM DNS cache TTL so container IP changes propagate faster on Docker networks.
ENTRYPOINT ["java", "-Dsun.net.inetaddr.ttl=60", "-jar", "app.jar"]