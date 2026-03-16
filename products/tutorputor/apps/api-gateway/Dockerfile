# =============================================================================
# TutorPutor API Gateway - Multi-stage Dockerfile
# =============================================================================

FROM node:22-bookworm-slim AS base
RUN npm install -g pnpm@9 && \
    apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# ---- Dependency install stage ----
FROM base AS deps
WORKDIR /workspace
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY products/tutorputor/apps/api-gateway/package.json \
     products/tutorputor/apps/api-gateway/

RUN pnpm install --frozen-lockfile --filter @ghatana/tutorputor-api-gateway...

# ---- Build stage ----
FROM deps AS builder
WORKDIR /workspace
COPY products/tutorputor/apps/api-gateway/ products/tutorputor/apps/api-gateway/
COPY tsconfig.base.json ./

RUN pnpm --filter @ghatana/tutorputor-api-gateway build

# ---- Production runtime ----
FROM node:22-bookworm-slim AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -r apigateway && useradd -r -g apigateway -s /bin/false apigateway

WORKDIR /app

COPY --from=builder --chown=apigateway:apigateway \
     /workspace/products/tutorputor/apps/api-gateway/dist ./dist
COPY --from=builder --chown=apigateway:apigateway \
     /workspace/products/tutorputor/apps/api-gateway/package.json ./
COPY --from=deps --chown=apigateway:apigateway \
     /workspace/node_modules ./node_modules

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:${PORT:-3200}/health || exit 1

USER apigateway

EXPOSE 3200

ENV NODE_ENV=production \
    PORT=3200 \
    LOG_LEVEL=warn

ENTRYPOINT ["node", "--enable-source-maps"]
CMD ["dist/server.js"]
