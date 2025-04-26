# Dockerfile ── same directory as pom.xml
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY target/my-app-1.0.1.jar my-app-1.0.1.jar            # ← exact file name

EXPOSE 8080                            # HTTP server in App.java
ENTRYPOINT ["java", "-jar", "app.jar"]
