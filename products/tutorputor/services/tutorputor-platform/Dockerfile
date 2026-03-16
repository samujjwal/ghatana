# =============================================================================
# TutorPutor Platform Service - Multi-stage Dockerfile
# =============================================================================

# ---- Base image with pnpm ----
FROM node:22-bookworm-slim AS base
RUN npm install -g pnpm@9 && \
    apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# ---- Dependency install stage ----
FROM base AS deps
WORKDIR /workspace

# Copy workspace manifests first for layer caching
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY products/tutorputor/services/tutorputor-platform/package.json \
     products/tutorputor/services/tutorputor-platform/
COPY products/tutorputor/services/tutorputor-db/package.json \
     products/tutorputor/services/tutorputor-db/
COPY products/tutorputor/libs/learning-kernel/package.json \
     products/tutorputor/libs/learning-kernel/ 2>/dev/null || true

RUN pnpm install --frozen-lockfile --filter @ghatana/tutorputor-platform... \
    --filter @ghatana/tutorputor-db

# ---- Build stage ----
FROM deps AS builder
WORKDIR /workspace

COPY products/tutorputor/services/tutorputor-db/ products/tutorputor/services/tutorputor-db/
COPY products/tutorputor/services/tutorputor-platform/ products/tutorputor/services/tutorputor-platform/
COPY products/tutorputor/libs/ products/tutorputor/libs/ 2>/dev/null || true
COPY tsconfig.base.json ./

RUN pnpm --filter @ghatana/tutorputor-platform build

# ---- Production runtime stage ----
FROM node:22-bookworm-slim AS runtime

# Install pnpm (for prisma generate at startup) and curl (health probe)
RUN npm install -g pnpm@9 && \
    apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Non-root user for security
RUN groupadd -r tutorputor && useradd -r -g tutorputor -s /bin/false tutorputor

WORKDIR /app

# Copy built output and production dependencies
COPY --from=builder --chown=tutorputor:tutorputor \
     /workspace/products/tutorputor/services/tutorputor-platform/dist ./dist
COPY --from=builder --chown=tutorputor:tutorputor \
     /workspace/products/tutorputor/services/tutorputor-platform/package.json ./
COPY --from=builder --chown=tutorputor:tutorputor \
     /workspace/products/tutorputor/services/tutorputor-db/prisma ./prisma
COPY --from=deps --chown=tutorputor:tutorputor \
     /workspace/node_modules ./node_modules

# Health check via /health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:${PORT:-3000}/health || exit 1

USER tutorputor

EXPOSE 3000 9090

ENV NODE_ENV=production \
    PORT=3000 \
    METRICS_PORT=9090 \
    LOG_LEVEL=warn

# Run Prisma migrations then start the server
ENTRYPOINT ["node", "--enable-source-maps"]
CMD ["dist/server.js"]
