# Multi-stage Dockerfile for YAPPC Backend
# Optimized for production deployment with minimal image size

# Stage 1: Build
FROM gradle:8.10.2-jdk21-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy source code
COPY products/yappc/backend products/yappc/backend
COPY products/yappc/core products/yappc/core
COPY libs libs

# Build application (skip tests for faster builds)
RUN ./gradlew :products:yappc:backend:api:build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Install runtime dependencies
RUN apk add --no-cache \
    curl \
    bash \
    tzdata

# Create application user (non-root)
RUN addgroup -g 1000 yappc && \
    adduser -u 1000 -G yappc -s /bin/sh -D yappc

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/products/yappc/backend/api/build/libs/*.jar app.jar

# Copy configuration files
COPY products/yappc/backend/api/src/main/resources application-default.properties

# Set ownership
RUN chown -R yappc:yappc /app

# Switch to application user
USER yappc

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# JVM optimization for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+ParallelRefProcEnabled \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+DisableExplicitGC \
               -XX:+AlwaysPreTouch \
               -XX:G1NewSizePercent=30 \
               -XX:G1MaxNewSizePercent=40 \
               -XX:G1HeapRegionSize=8M \
               -XX:G1ReservePercent=20 \
               -XX:InitiatingHeapOccupancyPercent=45 \
               -Djava.security.egd=file:/dev/./urandom"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
