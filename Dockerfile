# StockFlow Backend Multi-stage Dockerfile
# Stage 1: Build & Package
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root system group and user
RUN addgroup -S stockflow && adduser -S stockflow -G stockflow

# Copy jar from builder
COPY --from=builder /workspace/target/stockflow-backend-1.0.0-SNAPSHOT.jar app.jar

# Enforce non-root ownership
RUN chown -R stockflow:stockflow /app
USER stockflow

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
