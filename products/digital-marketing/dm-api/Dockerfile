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

# Copy full monorepo source required by root settings/includes
COPY . .

# Create stub directories for any Gradle-included projects excluded from Docker context.
# settings.gradle.kts applies config/generated/settings-gradle-includes.kts which
# registers all product subprojects. Gradle requires each registered directory to exist
# at configuration time, even when no tasks from those projects are being run.
RUN grep 'include(' config/generated/settings-gradle-includes.kts | \
    sed "s/.*include(\"://; s/\").*//" | \
    tr ':' '/' | \
    xargs -I{} mkdir -p {} 2>/dev/null; \
    grep 'include(' settings.gradle.kts | \
    sed "s/.*include(\"://; s/\").*//" | \
    tr ':' '/' | \
    xargs -I{} mkdir -p {} 2>/dev/null; \
    true

# Build the shadow/fat JAR (Gradle task: shadowJar or jar; depends on plugin)
# Tests run during build to ensure lifecycle correctness
RUN ./gradlew \
  -Dorg.gradle.jvmargs="-Xms512m -Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseG1GC" \
  -Dorg.gradle.workers.max=2 \
  :products:digital-marketing:dm-api:build \
  --no-daemon \
  --build-cache

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime

# Runtime diagnostics and container health checks depend on curl.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Run as a dedicated non-root system user
RUN groupadd --system dmos && useradd --system --gid dmos --no-create-home dmos

WORKDIR /app

# Copy the assembled artifact and runtime dependencies from the build stage
COPY --from=builder /workspace/products/digital-marketing/dm-api/build/libs/*.jar app.jar
COPY --from=builder /workspace/products/digital-marketing/dm-api/build/libs/dependencies/ /app/dependencies/

RUN chown -R dmos:dmos /app

USER dmos

# HTTP and admin ports
EXPOSE 8080

# Inline health check — matches /health/live and /health/ready paths
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -sf http://localhost:8080/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -cp /app/app.jar:/app/dependencies/* com.ghatana.digitalmarketing.api.DmosApiServer"]
