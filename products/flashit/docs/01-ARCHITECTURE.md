# FlashIt Architecture

FlashIt consumes kernel boundary policy validation, audit, approval, and observability capabilities. Product-specific business logic lives in the FlashIt backend gateway, agent service, client web/mobile apps, and FlashIt-owned domain packs.

## Kernel Ownership Boundary

Kernel owns boundary-policy APIs, shared route contracts, observability conventions, and product/runtime guardrails. FlashIt must consume those public contracts rather than extending kernel implementation classes or baking platform concerns into product-local infrastructure.

## Product-Owned Services

- `backend/gateway`: FlashIt API composition, auth/session behavior, product workflows, and policy enforcement entrypoints
- `backend/agent`: reflection, embedding, search, and AI-adjacent domain behavior
- `domain-pack-manifest.yaml` and `policy-packs/*`: product-owned policy/compliance declarations
- `client/web` and `client/mobile`: FlashIt-owned UX on shared shell/token/API conventions

## UI Surfaces

FlashIt exposes a web surface and a mobile surface. Both must derive route/navigation metadata from product-owned manifests, use shared token or shell contracts where available, and keep entitlement-sensitive behavior out of ad hoc hardcoded navigation arrays.

## Runtime And Observability

FlashIt's local runtime inherits shared compose and launcher template guidance, with product-local overrides only for domain services, ports, alerts, and dashboards. Product observability assets under `monitoring/` remain FlashIt-specific overlays rather than a separate platform stack definition.
