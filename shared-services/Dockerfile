# Multi-stage build for Ghatana Java shared services
# Base image: Eclipse Temurin JDK 21 (matches Gradle toolchain)

# ── Stage 1: Build ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files first for layer caching
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/

# Copy platform libraries (needed at compile time)
COPY platform/ platform/

# Copy the target service module
ARG SERVICE_MODULE
COPY shared-services/${SERVICE_MODULE}/ shared-services/${SERVICE_MODULE}/

# Build only the target module (skip tests for Docker builds)
RUN chmod +x gradlew && \
    ./gradlew :shared-services:${SERVICE_MODULE}:shadowJar -x test --no-daemon --stacktrace

# ── Stage 2: Runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S ghatana && adduser -S ghatana -G ghatana

# Labels
ARG SERVICE_MODULE
ARG VERSION=1.0.0-SNAPSHOT
LABEL org.opencontainers.image.title="ghatana-${SERVICE_MODULE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.vendor="Ghatana Inc."

WORKDIR /app

# Copy the fat JAR from builder
COPY --from=builder /workspace/shared-services/${SERVICE_MODULE}/build/libs/*-all.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC"

USER ghatana

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
