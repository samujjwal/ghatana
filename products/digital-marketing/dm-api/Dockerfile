# Multi-stage build for Digital Marketing API (Java 21 + Gradle)
# Stage 1: Build the fat JAR using the full Gradle workspace.
# Stage 2: Run with a minimal JRE image as a non-root user.
#
# Build from repo root:
#   docker buildx build --load \
#     -f products/digital-marketing/dm-api/Dockerfile \
#     -t ghatana/digital-marketing-api:local \
#     .

# ── Build stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# Copy only Gradle wrapper and config first for layer caching
COPY gradlew gradlew.bat gradle.properties ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY settings.gradle.kts build.gradle.kts ./

# Resolve dependencies before copying full source to maximise cache hits
RUN ./gradlew :products:digital-marketing:dm-api:dependencies --no-daemon --quiet || true

# Copy product and platform source (everything needed for this module)
COPY platform/ platform/
COPY platform-kernel/ platform-kernel/
COPY platform-plugins/ platform-plugins/
COPY shared-services/ shared-services/
COPY products/ products/
COPY config/ config/

# Build the shadow/fat JAR (Gradle task: shadowJar or jar; depends on plugin)
# Tests run during build to ensure lifecycle correctness
RUN ./gradlew :products:digital-marketing:dm-api:build \
      --no-daemon \
      --parallel \
      --build-cache

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# Run as a dedicated non-root system user
RUN groupadd --system dmos && useradd --system --gid dmos --no-create-home dmos

WORKDIR /app

# Copy the assembled artifact from the build stage
COPY --from=builder /workspace/products/digital-marketing/dm-api/build/libs/*.jar app.jar

RUN chown dmos:dmos app.jar

USER dmos

# HTTP and admin ports
EXPOSE 8080

# Inline health check — matches /health/live and /health/ready paths
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -sf http://localhost:8080/health/ready || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
