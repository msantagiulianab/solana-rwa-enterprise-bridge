# ---- Build Stage ----
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

# Copy Maven wrapper and POM first for dependency caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make the wrapper executable (it may already be, but ensure it)
RUN chmod +x mvnw

# Download dependencies (cache layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Package the application, skipping tests (tests run in CI, not in the Docker build)
RUN ./mvnw package -DskipTests -B

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the executable JAR from the build stage
COPY --from=builder /workspace/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Expose the application port (Render injects PORT via env var; server.port reads SERVER_PORT)
EXPOSE 8080

# Health check using the Spring Boot actuator (if available) or a simple curl to a known endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]