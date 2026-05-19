# Runtime package for Digital Marketing UI (nginx).
# The Kernel build phase owns producing dist; package wraps that artifact.
#
# Build from repo root (as used by DockerBuildxAdapter):
#   docker buildx build --load \
#     -f products/digital-marketing/ui/Dockerfile \
#     -t ghatana/digital-marketing-web:local \
#     .

FROM nginx:1.27-alpine AS runtime

# Remove default nginx content
RUN rm -rf /usr/share/nginx/html/*

# Copy built assets from builder stage
COPY dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --quiet --tries=1 -O /dev/null http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
