# ============================================================================
# FlashIt Java Agent Service – Multi-stage Docker build
#
# Build context is the MONOREPO ROOT (set in docker-compose.local.yml).
# This gives access to gradle wrapper + version catalog.
#
# Manual build from products/flashit/:
#   docker build -f backend/agent/Dockerfile -t flashit-agent ../../
# ============================================================================

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy gradle wrapper from monorepo root
COPY gradlew /build/gradlew
COPY gradle/wrapper /build/gradle/wrapper

# Copy version catalog
COPY gradle/libs.versions.toml /build/gradle/libs.versions.toml

# Copy agent project files
COPY products/flashit/backend/agent/settings.gradle.kts /build/settings.gradle.kts
COPY products/flashit/backend/agent/build.gradle.kts /build/build.gradle.kts
COPY products/flashit/backend/agent/src/ /build/src/

# The agent settings.gradle.kts resolves the catalog via:
#   rootDir.parentFile^4 / gradle / libs.versions.toml
# In Docker rootDir = /build, so we symlink so the path resolves correctly.
RUN mkdir -p /build/../../../.. 2>/dev/null || true && \
    ln -sf /build/gradle /gradle

# Make gradlew executable and build (skip tests in Docker)
RUN chmod +x gradlew && \
    ./gradlew clean build -x test --no-daemon && \
    mkdir -p /build/dist/lib && \
    cp build/libs/*.jar /build/dist/ && \
    cp -r build/libs/dependencies/* /build/dist/lib/ 2>/dev/null || true

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/dist/*.jar /app/
COPY --from=builder /build/dist/lib/ /app/lib/

# Create a launch script
RUN echo '#!/bin/sh' > /app/run.sh && \
    echo 'exec java \' >> /app/run.sh && \
    echo '  -XX:+UseZGC \' >> /app/run.sh && \
    echo '  -XX:MaxRAMPercentage=75 \' >> /app/run.sh && \
    echo '  -Djava.security.egd=file:/dev/./urandom \' >> /app/run.sh && \
    echo '  -cp "/app/*:/app/lib/*" \' >> /app/run.sh && \
    echo '  com.ghatana.flashit.agent.FlashItAgentApplication' >> /app/run.sh && \
    chmod +x /app/run.sh

EXPOSE 8090

USER appuser

HEALTHCHECK --interval=15s --timeout=10s --start-period=30s --retries=3 \
  CMD wget --spider -q http://localhost:8090/health || exit 1

ENTRYPOINT ["/app/run.sh"]
