# DMOS Production Hardening Todo (Simple)

This is the implementation checklist derived from the previous audit.

## Done
- [x] Fix capabilities contract shape mismatch (backend and frontend now use typed capability entries, not map-only contract assumptions).
- [x] Add missing capability key `dmos.ai_optimization` to canonical backend and frontend capability registries.
- [x] Normalize frontend capability display-name mapping to underscore keys (`self_marketing`, `market_research`, `advanced_channels`, `ai_optimization`).
- [x] Add route-manifest to capability-constants parity test in UI (`route-contracts.test.tsx`).
- [x] Enforce production startup failure when identity provider is not configured in API composition root.
- [x] Enforce production startup failure for fallback preflight provider and in-memory event log store.
- [x] Enforce production startup failure for null governed workflow service wiring.
- [x] Wire bootstrap validator with `DMOS_CONTACT_ENCRYPTION_KEY` and command runtime as outbox executor dependency input.
- [x] Standardize workspace servlet errors to `StandardErrorEnvelope` with `X-Correlation-ID` response header.
- [x] Standardize next-best-action servlet errors to `StandardErrorEnvelope` with `X-Correlation-ID` response header.

## In Progress
- [x] Run focused Java tests for changed DMOS modules in product-local Gradle execution.

## Next
- [x] Enforce server-side capability checks in every boundary servlet (not only UI gating).
- [x] Replace temporary production hard-fail placeholders with real production adapters for preflight and event store.
- [x] Complete production identity provider implementation (token verification + role/permission resolution).
- [x] Add servlet-level API validation matrix tests for optimization and capability endpoints.
- [x] Add direct API tests proving disabled capabilities return forbidden/locked responses.
- [x] Add end-to-end stable route suite (UI -> API -> Postgres) to CI.
