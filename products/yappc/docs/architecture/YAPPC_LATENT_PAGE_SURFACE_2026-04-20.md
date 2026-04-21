# Yappc Latent Page Surface

Date: 2026-04-20
Authoritative route source: `products/yappc/frontend/web/src/routes.ts`
Purpose: Document the large unmounted `web/src/pages/**` surface so it is not confused with shipped product behavior.

## Scope

- This document covers the latent page tree under `products/yappc/frontend/web/src/pages/**`.
- These files are present in the repository but are not mounted in the current Yappc route tree.
- Presence in `src/pages/**` is not evidence of supported product capability.

## Why this matters

- The mounted product route tree is intentionally much smaller than the historical page surface.
- The 2026-04-20 product audit identified file-presence drift as a recurring source of false scope assumptions.
- Product, QA, and architecture reviews should treat mounted routes as the source of truth and this latent inventory as an explicit exclusion list.

## Latent surface categories observed during audit

- `pages/operations/**`
- `pages/development/**`
- `pages/security/**`
- `pages/collaboration/**`
- `pages/bootstrapping/**`
- `pages/initialization/**`
- `pages/admin/**`

## Working rule

- A latent page remains non-product until it is mounted in `web/src/routes.ts`, validated against backed contracts, and added to the mounted route inventory.
- If a latent page is still valuable for future work, keep it documented as latent instead of implying it is active.
- If a latent page is obsolete, archive or delete it rather than leaving the product boundary ambiguous.

## Relationship to mounted inventory

- Mounted route truth lives in `YAPPC_MOUNTED_ROUTE_INVENTORY_2026-04-20.md`.
- This document exists so latent surfaces are explicitly named rather than silently ignored.

## Maintenance rule

- Any future route-audit update should review both this latent document and the mounted route inventory together.
- If a page under `src/pages/**` becomes mounted, remove it from latent assumptions and update the mounted inventory in the same change.