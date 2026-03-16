# Multi-stage build for the AEP (Agentic Event Processor) service.
#
# Build:
#   docker build -t ghcr.io/ghatana/aep-service-manager:latest .
#
# Run (with shared-services stack running):
#   docker run -p 8090:8090 -p 9091:9091 \
#     -e AEP_HTTP_PORT=8090 \
#     -e AEP_GRPC_PORT=9091 \
#     -e DB_HOST=postgres \
#     -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
#     -e DATACLOUD_URL=http://data-cloud:8082 \
#     ghcr.io/ghatana/aep-service-manager:latest

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
COPY products/aep/ products/aep/
COPY products/data-cloud/ products/data-cloud/

# Resolve dependencies only (separate layer — only re-runs when build files change)
RUN ./gradlew :products:aep:launcher:dependencies --no-daemon --quiet 2>/dev/null || true

# Build the launcher jar
RUN ./gradlew :products:aep:launcher:jar \
    --no-daemon \
    -x test \
    -x checkstyleMain \
    -x pmdMain \
    --stacktrace

# ---------------------------------------------------------------------------
# Stage 2 – Minimal runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Security: run as dedicated non-root user (matches K8s runAsUser: 1000)
RUN groupadd --gid 1000 aep \
    && useradd --uid 1000 --gid aep --shell /bin/bash --create-home aep

WORKDIR /app

# Application jar
COPY --from=builder /build/products/aep/launcher/build/libs/*.jar app.jar

# Log4j2 and other classpath resources
COPY --from=builder /build/products/aep/launcher/src/main/resources/ /app/resources/ 2>/dev/null || true

# Writeable tmp for JVM (ReadOnlyRootFilesystem = true needs this explicitly mounted in K8s)
RUN mkdir -p /tmp && chown aep:aep /tmp
RUN chown -R aep:aep /app

USER aep

# HTTP health + management port
EXPOSE 8090
# gRPC service port
EXPOSE 9091

# JVM flags:
#   ZGC         — low-latency GC suited for event-processing workloads
#   MaxRAMPercentage — honour container memory limits instead of hard-coding -Xmx
#   ExitOnOutOfMemoryError — fast-fail so Kubernetes restarts the pod promptly
#   HeapDumpOnOutOfMemoryError — capture OOM for post-mortem analysis
ENV JAVA_OPTS="-XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Dfile.encoding=UTF-8 \
    -Djava.security.egd=file:/dev/./urandom"

# Runtime environment defaults — overridden via K8s ConfigMap / Secret
ENV AEP_HTTP_PORT=8090 \
    AEP_GRPC_PORT=9091 \
    AEP_WORKER_THREADS=4 \
    AEP_DEV_MODE=false \
    LOG_LEVEL=INFO

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:${AEP_HTTP_PORT}/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
