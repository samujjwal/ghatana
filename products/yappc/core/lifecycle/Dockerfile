# Multi-stage Dockerfile for YAPPC Lifecycle Platform

# Stage 1: Build
FROM gradle:8.5-jdk21-alpine AS build

WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build the application
RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="ghatana-platform"
LABEL description="YAPPC Lifecycle Platform"
LABEL version="1.0.0"

# Create app user
RUN addgroup -S yappc && adduser -S yappc -G yappc

WORKDIR /app

# Copy built artifact from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Copy configuration
COPY --from=build /app/src/main/resources/application.yml application.yml

# Change ownership
RUN chown -R yappc:yappc /app

# Switch to non-root user
USER yappc

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
