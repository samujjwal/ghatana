# ADR-DC-004: Required Event Fields for Replay, Audit, Provenance, and Context

**Status:** Accepted
**Date:** 2026-04-26
**Authors:** Data Cloud Architecture Team

## Context

The event log is the source of truth for audit, replay, and temporal queries. Previously, event payloads were sparse: they contained a type, a timestamp, and an opaque payload. This was insufficient to reconstruct entity history, prove compliance, or ground AI agents in reliable business context.

## Decision

1. Every entity mutation (create, update, delete, redact, purge, classify) MUST emit a **rich canonical event**.
2. The event envelope MUST contain:
   - `eventId`: UUID
   - `tenantId`: scope
   - `type`: event type (e.g., `entity.mutated`)
   - `version`: schema version (`1.0`)
   - `occurredAt`: ISO-8601 timestamp
   - `actor`: `{ type: "user|system|agent|connector", id: "..." }`
   - `resource`: `{ type: "entity", collection: "...", id: "..." }`
   - `operation`: `create|update|delete|redact|purge|classify|execute`
   - `before`: previous state (empty for create)
   - `after`: resulting state (empty for delete)
   - `patch`: optional computed diff
   - `policyDecision`: `{ decisionId, result, obligations[] }` (when policy engine available)
   - `traceId`: correlation trace
   - `correlationId`: request correlation
   - `provenance`: `{ source: "api|connector|workflow", derivedFrom: [...] }`
3. Event replay MUST support offset-based tail, seek, and replay from checkpoint.
4. Point-in-time entity history (`GET /entities/:collection/:id/history`) MUST reconstruct state from event snapshots.

## Consequences

- **Positive:** Full audit reconstruction. Agent context grounded in provenance. Compliance evidence built from the event log.
- **Negative:** Larger event payloads (~2-5x). Must ensure event store compaction handles growth.

## Related

- P0.3 in `data-cloud-implementation-tasks.md`
- `EntityCrudHandler.java` (`buildCdcEnvelope`)
- `EventHandler.java`
