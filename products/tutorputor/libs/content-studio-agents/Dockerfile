# Multi-stage Dockerfile for the Tutorputor Content Studio Agents gRPC server.
#
# The server exposes:
#   - grpc: GRPC_PORT (default 50051) — ContentGenerationService
#   - http: HEALTH_PORT (default 8096) — /health endpoint for liveness probes
#
# Build (from monorepo root):
#   docker build \
#     -f products/tutorputor/libs/content-studio-agents/Dockerfile \
#     -t ghcr.io/ghatana/content-studio-agents:latest .
#
# Run:
#   docker run -p 50051:50051 \
#     -e OPENAI_API_KEY=sk-... \
#     ghcr.io/ghatana/content-studio-agents:latest

# ---------------------------------------------------------------------------
# Stage 1 – Build (Gradle + JDK 21)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Gradle wrapper + build scripts (separate cache layer)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY buildSrc/ buildSrc/
COPY config/ config/

# Copy platform libraries required by content-studio-agents
COPY platform/ platform/

# Copy Tutorputor product (including the lib being built)
COPY products/tutorputor/ products/tutorputor/

# Pre-warm Gradle dependency cache (fails silently if offline)
RUN ./gradlew \
    :products:tutorputor:libs:content-studio-agents:dependencies \
    --no-daemon --quiet 2>/dev/null || true

# Build the runnable shadow (fat) JAR
RUN ./gradlew \
    :products:tutorputor:libs:content-studio-agents:shadowJar \
    --no-daemon \
    -x test \
    -x checkstyleMain \
    -x pmdMain \
    --stacktrace

# ---------------------------------------------------------------------------
# Stage 2 – Minimal runtime image (JRE 21)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Security: non-root user (matches K8s securityContext.runAsUser: 1000)
RUN groupadd --gid 1000 contentstudio \
    && useradd --uid 1000 --gid contentstudio --shell /bin/false --create-home contentstudio

WORKDIR /app

# Copy the shadow JAR from the build stage
COPY --from=builder \
    /build/products/tutorputor/libs/content-studio-agents/build/libs/*-all.jar \
    app.jar

# Writable tmp for JVm (needed when ReadOnlyRootFilesystem=true in K8s)
RUN mkdir -p /tmp && chown contentstudio:contentstudio /tmp
RUN chown -R contentstudio:contentstudio /app

USER contentstudio

# gRPC service port
EXPOSE 50051
# Health check HTTP port (serves /health for liveness/readiness probes)
EXPOSE 8096

# JVM tuning:
#   ZGC         — low-latency GC for interactive LLM-backed request handling
#   MaxRAMPercentage — respect container memory limits
#   ExitOnOutOfMemoryError — fast-fail so K8s restarts the pod promptly
ENV JAVA_OPTS="\
    -XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

# gRPC server port (override with GRPC_PORT env in docker run / K8s)
ENV GRPC_PORT=50051
# LLM provider config — set at deployment time via K8s secrets
ENV OPENAI_API_KEY=""
ENV OPENAI_MODEL="gpt-4o"
ENV OLLAMA_BASE_URL=""
ENV LLM_PRIMARY_PROVIDER="openai"

# Health check — requires a lightweight HTTP health endpoint.
# See ContentGenerationServerConfig for port binding.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${HEALTH_PORT:-8096}/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
