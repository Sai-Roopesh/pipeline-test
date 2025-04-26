# Dockerfile – package the 1.0.1 jar
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# use the exact jar name from the build
COPY target/my-app-1.0.1.jar my-app-1.0.1.jar

EXPOSE 15000
ENTRYPOINT ["java", "-jar", "my-app-1.0.1.jar"]
