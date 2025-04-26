# Dockerfile ── same directory as pom.xml
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY target/app.jar app.jar            # ← exact file name

EXPOSE 8080                            # HTTP server in App.java
ENTRYPOINT ["java", "-jar", "app.jar"]
