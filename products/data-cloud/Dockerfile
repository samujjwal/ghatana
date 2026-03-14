# Multi-stage build for Data-Cloud standalone service.
#
# Build:
#   docker build -t ghatana/data-cloud:latest .
#
# Run (with shared-services stack running):
#   docker run -p 8082:8082 -p 9090:9090 \
#     -e DATACLOUD_HTTP_PORT=8082 \
#     -e DATACLOUD_GRPC_PORT=9090 \
#     -e DB_HOST=postgres \
#     -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
#     -e CLICKHOUSE_HOST=clickhouse \
#     ghatana/data-cloud:latest

# ---------------------------------------------------------------------------
# Stage 1 – Build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Gradle wrapper and dependency manifests first (layer-cache friendly)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY buildSrc/ buildSrc/
COPY config/ config/
COPY platform/ platform/
COPY products/data-cloud/ products/data-cloud/

# Resolve dependencies (separate layer — only re-runs when build files change)
RUN ./gradlew :products:data-cloud:launcher:dependencies --no-daemon --quiet 2>/dev/null || true

# Build the fat/shadow jar for the launcher
RUN ./gradlew :products:data-cloud:launcher:jar \
    --no-daemon \
    -x test \
    -x checkstyleMain \
    -x pmdMain \
    --stacktrace

# ---------------------------------------------------------------------------
# Stage 2 – Runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Security: run as non-root
RUN groupadd --gid 1001 datacloud \
    && useradd --uid 1001 --gid datacloud --shell /bin/bash --create-home datacloud

WORKDIR /app

# Copy assembled jars from builder stage
COPY --from=builder /build/products/data-cloud/launcher/build/libs/*.jar app.jar

# Copy Log4j2 config if present
COPY --from=builder /build/products/data-cloud/launcher/src/main/resources/ /app/resources/ 2>/dev/null || true

# Ensure correct permissions
RUN chown -R datacloud:datacloud /app

USER datacloud

# -------------------------------------------------------------------------
# Runtime configuration (all overridable via environment variables)
# -------------------------------------------------------------------------
ENV DATACLOUD_HTTP_PORT=8082
ENV DATACLOUD_GRPC_PORT=9090
ENV DATACLOUD_MAX_CONNECTIONS=100
ENV DATACLOUD_CACHE_ENABLED=true
ENV DATACLOUD_METRICS_ENABLED=true

# Database (PostgreSQL warm tier)
ENV DB_HOST=postgres
ENV DB_PORT=5432
ENV DB_NAME=datacloud
ENV DB_USER=datacloud
ENV DB_PASSWORD=changeme

# Kafka (event log)
ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9092
ENV KAFKA_ENABLED=false

# ClickHouse (time-series analytics)
ENV CLICKHOUSE_HOST=clickhouse
ENV CLICKHOUSE_PORT=8123
ENV CLICKHOUSE_ENABLED=false

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseZGC \
  -XX:InitialRAMPercentage=50.0 \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Dfile.encoding=UTF-8 \
  -Djava.net.preferIPv4Stack=true"

EXPOSE 8082 9090

# Health check using the built-in /health endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:${DATACLOUD_HTTP_PORT}/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -cp /app/resources:app.jar com.ghatana.datacloud.launcher.DataCloudLauncher"]
