# ADR-DC-002: Runtime Capability Truth — `/api/v1/capabilities` is Mandatory Gating Authority

**Status:** Accepted
**Date:** 2026-04-26
**Authors:** Data Cloud Architecture Team

## Context

Data Cloud exposes a broad surface of features: entity CRUD, event streaming, AI assist, governance, connectors, tier migration, and more. Many of these features depend on optional backend services (OpenSearch, Trino, Kafka, policy engine, audit log) that may be unavailable in a given deployment profile. The UI and SDK historically showed controls for unavailable features, leading to user confusion and false readiness claims.

## Decision

1. `/api/v1/capabilities` is the **single, mandatory source of runtime truth** for all feature gating.
2. Every capability entry MUST expose a unified schema:
   - `status`: `ACTIVE` | `DEGRADED` | `NOT_CONFIGURED` (legacy)
   - `mode` / `maturity`: `live` | `degraded` | `preview` | `unavailable`
   - `dependency`: probe source (`healthCheck` | `runtime`)
   - `probe`: probe type
   - `lastCheckedAt`: ISO-8601 timestamp of last probe
   - `degradedReason`: human-readable explanation when degraded
   - `docsLink`: documentation URL
3. The UI MUST NOT render controls for capabilities whose `mode` is `unavailable`.
4. The SDK MUST check capabilities before enabling feature flags.
5. Health endpoints are split:
   - `GET /live` — liveness (process-only)
   - `GET /ready` — readiness (dependency-aware, returns 503 when critical deps down)
   - `GET /health/detail` — per-subsystem SLO status

## Consequences

- **Positive:** Users see only features that are actually available. Support burden drops. Marketing claims can be verified against the live registry.
- **Negative:** Every new feature must register its capability. This is a small tax on feature development.

## Related

- P0.1 in `data-cloud-implementation-tasks.md`
- `CapabilityRegistryHandler.java`
- `RouteCapabilityRegistry.ts`
