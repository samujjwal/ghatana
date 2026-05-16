# Multi-stage build for Digital Marketing UI (Node + nginx).
# Stage 1: Build the Vite/React app inside a pnpm workspace context.
# Stage 2: Serve the static bundle with nginx.
#
# Build from repo root (as used by DockerBuildxAdapter):
#   docker buildx build --load \
#     -f products/digital-marketing/ui/Dockerfile \
#     -t ghatana/digital-marketing-web:local \
#     .

# ── Build stage ──────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

# Enable pnpm via corepack (matches packageManager field in package.json)
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate

WORKDIR /workspace

# Copy full monorepo workspace for deterministic filtered build
COPY . .

# Install all dependencies (frozen for reproducibility)
RUN pnpm install --frozen-lockfile

ENV NODE_ENV=production
RUN pnpm --dir products/digital-marketing/ui run build:docker

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# Remove default nginx content
RUN rm -rf /usr/share/nginx/html/*

# Copy built assets from builder stage
COPY --from=builder /workspace/products/digital-marketing/ui/dist /usr/share/nginx/html

# Copy product nginx config from repository path
COPY products/digital-marketing/ui/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --quiet --tries=1 -O /dev/null http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

