# Multi-stage build for the Software-Org management API.

FROM node:22-bookworm-slim AS base

RUN npm install -g pnpm@10 && \
    apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

FROM base AS deps

WORKDIR /app

ARG DATABASE_URL=postgresql://postgres:postgres@localhost:5432/software_org
ENV DATABASE_URL=${DATABASE_URL}

COPY products/software-org/services/management-api/package.json ./
COPY products/software-org/services/management-api/tsconfig.json ./
COPY products/software-org/services/management-api/prisma ./prisma
COPY products/software-org/services/management-api/prisma.config.ts ./

RUN pnpm install --no-frozen-lockfile
RUN pnpm exec prisma generate --schema prisma/schema.prisma

FROM deps AS builder

WORKDIR /app

COPY products/software-org/services/management-api/src ./src
COPY products/software-org/services/management-api/scripts ./scripts

RUN pnpm build

FROM base AS runtime

RUN groupadd -r softwareorg && useradd -r -g softwareorg -s /bin/false softwareorg

WORKDIR /app

COPY --from=builder --chown=softwareorg:softwareorg /app/dist ./dist
COPY --from=builder --chown=softwareorg:softwareorg /app/generated ./generated
COPY --from=builder --chown=softwareorg:softwareorg /app/node_modules ./node_modules
COPY --from=builder --chown=softwareorg:softwareorg /app/package.json ./package.json
COPY --from=builder --chown=softwareorg:softwareorg /app/prisma ./prisma
COPY --from=builder --chown=softwareorg:softwareorg /app/prisma.config.ts ./prisma.config.ts

USER softwareorg

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3001 \
    LOG_LEVEL=info

EXPOSE 3001

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["sh", "-c", "pnpm exec prisma migrate deploy --schema prisma/schema.prisma && exec node dist/server.js"]