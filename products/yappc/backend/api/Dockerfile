# YAPPC API Module
# Multi-stage build for production deployment

# ========== Build Stage ==========
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy source code
COPY buildSrc/ buildSrc/
COPY libs/ libs/
COPY products/data-cloud/ products/data-cloud/
COPY products/yappc/backend/api/ products/yappc/backend/api/

# Build the API module
RUN chmod +x gradlew && \
    ./gradlew :products:yappc:backend:api:build -x test --no-daemon

# ========== Runtime Stage ==========
FROM eclipse-temurin:21-jre-alpine

# Add labels for container registry
LABEL org.opencontainers.image.title="YAPPC API"
LABEL org.opencontainers.image.description="YAPPC Dashboard REST API"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.vendor="Ghatana Technologies"

# Create non-root user for security
RUN addgroup -S yappc && adduser -S yappc -G yappc

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/products/yappc/backend/api/build/libs/*.jar app.jar

# Copy OpenAPI spec for documentation
COPY --from=builder /app/products/yappc/backend/api/openapi.yaml openapi.yaml

# Set ownership
RUN chown -R yappc:yappc /app

# Switch to non-root user
USER yappc

# Expose HTTP port
EXPOSE 8080

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Application configuration
ENV APP_ENV=production
ENV SERVER_PORT=8080
ENV LOG_LEVEL=INFO

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
