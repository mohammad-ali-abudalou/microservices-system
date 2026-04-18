FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# إنشاء مجلد الإعدادات لضمان عدم حدوث Error: File not found
RUN mkdir -p /app/config-repo

COPY build/libs/*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]