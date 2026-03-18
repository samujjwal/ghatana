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

# Build YAPPC backend services (skip tests for speed)
RUN ./gradlew :products:yappc:backend:api:build \
              :products:yappc:core:lifecycle:build \
              :products:yappc:core:scaffold:core:build \
              -x test --no-daemon -q

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

# Copy backend JAR from builder
COPY --from=backend-builder /workspace/products/yappc/backend/api/build/libs/*.jar \
     /app/yappc-backend.jar

# Copy frontend static assets
COPY --from=frontend-builder /workspace/products/yappc/frontend/dist \
     /app/public

# Copy configuration templates
COPY products/yappc/infrastructure/docker/.env.template /app/.env.example
COPY products/yappc/infrastructure/docker/docker-compose.yml /app/

# Set ownership
RUN chown -R yappc:yappc /app

# Switch to non-root user
USER yappc

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Expose ports
EXPOSE 8080 8081 3000

# Default environment
ENV JAVA_OPTS="-Xmx2G -Xms512M" \
    LOG_LEVEL=INFO \
    NODE_ENV=production

# Startup script
ENTRYPOINT ["sh", "-c"]
CMD ["exec java $JAVA_OPTS -jar /app/yappc-backend.jar"]
