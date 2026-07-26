# Stage 1: Build application with Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy pom.xml and download dependencies first (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build package
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Runtime Environment
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/target/resume-analyzer-backend-1.0.0.jar app.jar

# Expose default port 8000
EXPOSE 8000

# Set default PORT environment variable if not passed by host/Render
ENV PORT=8000

# Run Java application
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
