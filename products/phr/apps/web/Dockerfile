# Runtime package for the PHR web surface.
# The Kernel build phase owns producing dist; package wraps that artifact.
FROM nginx:1.27-alpine AS runtime

RUN rm -rf /usr/share/nginx/html/*
COPY dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --quiet --tries=1 -O /dev/null http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
