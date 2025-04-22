# Use a lightweight Java 21 runtime image
FROM eclipse-temurin:21-jre-jammy

# Create a working directory
WORKDIR /app

# Copy the built jar (assumes jenkins/Maven output to target/*.jar)
COPY target/*.jar app.jar

# (Optional) expose a port if your app listens on one
# EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
