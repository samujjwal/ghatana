# Multi-stage build for the PHR web surface.
# Build from repo root so workspace packages resolve consistently.

FROM node:22-alpine AS builder

RUN corepack enable && corepack prepare pnpm@10.33.0 --activate

WORKDIR /workspace
COPY . .

RUN pnpm install --no-frozen-lockfile
ENV NODE_ENV=production
RUN pnpm --dir products/phr/apps/web run build

FROM nginx:1.27-alpine AS runtime

RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /workspace/products/phr/apps/web/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --quiet --tries=1 -O /dev/null http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
