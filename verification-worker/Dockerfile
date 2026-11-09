# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY kyc-contracts/pom.xml kyc-contracts/
COPY onboarding-service/pom.xml onboarding-service/
COPY lambda-processor/pom.xml lambda-processor/
COPY verification-worker/pom.xml verification-worker/
RUN mvn -B dependency:go-offline -pl kyc-contracts,verification-worker
COPY kyc-contracts/src kyc-contracts/src
COPY verification-worker/src verification-worker/src
RUN mvn clean package -DskipTests -pl kyc-contracts,verification-worker

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/verification-worker/target/verification-worker-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
