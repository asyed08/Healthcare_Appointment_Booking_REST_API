# ============================================================
# Multi-stage Dockerfile for the Healthcare Spring Boot App
# ============================================================

# ---- Stage 1: Build ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom.xml first and resolve dependencies (cached layer unless pom changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the fat JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create a non-root user to run the application (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# Use exec form so the process receives OS signals properly (graceful shutdown)
ENTRYPOINT ["java", "-jar", "app.jar"]

