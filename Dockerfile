# Dockerfile – build a tiny Java 21 HTTP service
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
