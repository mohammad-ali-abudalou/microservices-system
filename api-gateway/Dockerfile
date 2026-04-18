FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 9000
# تقليل وقت انتظار DNS داخل Docker
ENTRYPOINT ["java", "-Dsun.net.inetaddr.ttl=60", "-jar", "app.jar"]