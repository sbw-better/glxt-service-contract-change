FROM eclipse-temurin:8-jre
WORKDIR /app
COPY target/glxt-service-contract-change-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
