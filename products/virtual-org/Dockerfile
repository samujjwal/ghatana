# Multi-stage build for Virtual Org Agent
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Copy dependency declarations first (better caching)
COPY build.gradle settings.gradle ./
COPY gradle.properties ./

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build application
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install required packages
RUN apk add --no-cache \
    curl \
    bash \
    git \
    && rm -rf /var/cache/apk/*

# Create app user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Create directories with proper permissions
RUN mkdir -p /app/data /app/logs /app/config && \
    chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Expose ports
EXPOSE 8080 8081

# JVM options
ENV JAVA_OPTS="-Xms512m -Xmx2048m \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:MaxGCPauseMillis=200 \
    -Djava.security.egd=file:/dev/./urandom"

# Application options
ENV APP_OPTS=""

# Entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar $APP_OPTS"]

# Labels
LABEL maintainer="ghatana-team"
LABEL version="1.0.0"
LABEL description="Virtual Organization Agent - AI-powered autonomous agent system"
