# Multi-stage Dockerfile for YAPPC: Unified Platform Creator
# Builds and packages YAPPC backend + frontend as a production-ready OCI image
# Usage: docker build -t yappc:latest -f Dockerfile .

ARG JAVA_VERSION=21
ARG NODE_VERSION=20
ARG PNPM_VERSION=8

# ============================================================================
# STAGE 1: Backend Build (Java)
# ============================================================================
FROM gradle:8.10.2-jdk${JAVA_VERSION}-alpine AS backend-builder

WORKDIR /workspace

# Copy Gradle configuration
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts gradle.properties settings.gradle.kts ./

# Copy platform libraries (dependencies)
COPY libs libs

# Copy YAPPC product modules
COPY products/yappc products/yappc
COPY contracts contracts

# Build YAPPC backend services (run tests for production safety)
RUN ./gradlew :core:yappc-services:build \
              :core:yappc-api:build \
              :core:scaffold:build \
              --no-daemon -q

# Verify backend build artifacts exist
RUN ls -la core/yappc-services/build/libs/ && \
    test -f core/yappc-services/build/libs/yappc-services.jar || (echo "ERROR: Backend JAR not found" && exit 1)

# ============================================================================
# STAGE 2: Frontend Build (Node)
# ============================================================================
FROM node:${NODE_VERSION}-alpine AS frontend-builder

# Install pnpm globally
RUN npm install -g pnpm@${PNPM_VERSION}

WORKDIR /workspace

# Copy pnpm monorepo config
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json ./

# Copy frontend packages
COPY products/yappc/frontend products/yappc/frontend
COPY platform/typescript platform/typescript
COPY products/audio-video/libs/typescript products/audio-video/libs/typescript

# Install dependencies and build frontend
RUN pnpm install --frozen-lockfile && \
    cd products/yappc/frontend && \
    pnpm build:all

# Verify frontend build artifacts exist
RUN test -d products/yappc/frontend/dist || (echo "ERROR: Frontend dist directory not found" && exit 1) && \
    ls -la products/yappc/frontend/dist/

# ============================================================================
# STAGE 3: Runtime Image (Production)
# ============================================================================
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

LABEL maintainer="Ghatana Platform <platform@ghatana.ai>" \
      description="YAPPC: Autonomous Programming Pair Companion" \
      version="1.0.0"

# Install runtime dependencies
RUN apk add --no-cache \
    curl \
    ca-certificates \
    bash \
    tzdata \
    && rm -rf /var/cache/apk/*

# Create non-root user for processes
RUN addgroup -g 1000 yappc && \
    adduser -u 1000 -G yappc -s /sbin/nologin -D yappc

WORKDIR /app

# Copy backend JAR from builder (verification done in builder stage)
COPY --from=backend-builder /workspace/core/yappc-services/build/libs/yappc-services.jar \
     /app/yappc-backend.jar

# Copy frontend static assets (verification done in builder stage)
COPY --from=frontend-builder /workspace/products/yappc/frontend/dist \
     /app/public

# Copy configuration templates
COPY products/yappc/infrastructure/docker/.env.template /app/.env.example
COPY products/yappc/infrastructure/docker/docker-compose.yml /app/

# Set ownership
RUN chown -R yappc:yappc /app

# Verify non-root user can write to application directories (task 2.1.5)
RUN su yappc -s /bin/sh -c "touch /app/.write-test && rm /app/.write-test" || \
    (echo "ERROR: Non-root user cannot write to /app" && exit 1)
RUN su yappc -s /bin/sh -c "touch /app/public/.write-test && rm /app/public/.write-test" || \
    (echo "ERROR: Non-root user cannot write to /app/public" && exit 1)

# Switch to non-root user
USER yappc

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8082/health || exit 1

# Expose ports
EXPOSE 8082 8081 3000

# Default environment
ENV JAVA_OPTS="-Xmx2G -Xms512M" \
    LOG_LEVEL=INFO \
    NODE_ENV=production

# Startup script
ENTRYPOINT ["sh", "-c"]
CMD ["exec java $JAVA_OPTS -jar /app/yappc-backend.jar"]
