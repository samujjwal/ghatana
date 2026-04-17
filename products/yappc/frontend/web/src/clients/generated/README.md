This directory is reserved for generated contract artifacts.

Current source of truth:
- `products/yappc/docs/api/openapi.yaml` for HTTP/OpenAPI types

Generation entrypoint:
- `pnpm codegen:openapi` from `products/yappc/frontend`

Expected output:
- `openapi.ts`

Do not hand-edit generated files in this directory.