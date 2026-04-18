# استخدام نسخة تشغيل خفيفة فقط
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# نسخ ملف الـ JAR الذي بنيناه يدوياً في الخطوة السابقة
COPY build/libs/*.jar app.jar

# المنفذ (تأكد من تغييره حسب كل خدمة: 8888 للـ config، 8761 للـ discovery، إلخ)
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]