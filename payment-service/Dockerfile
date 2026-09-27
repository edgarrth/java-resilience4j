FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY psp-simulator/pom.xml ./psp-simulator/pom.xml
RUN mvn -B -pl payment-service -am dependency:go-offline
COPY payment-service/src ./payment-service/src
RUN mvn -B -pl payment-service -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
