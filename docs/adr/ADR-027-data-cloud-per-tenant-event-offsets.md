# ADR-027: Data Cloud Per-Tenant Event Offsets

**Status:** Accepted  
**Date:** 2026-04-12  
**Deciders:** Data Cloud Platform Team

---

## Context

The Data Cloud event log (`DataCloudClient.tailEvents` / `appendEvent`) must support multi-tenant deployments where event streams from one tenant must be completely invisible to and unaffected by another tenant.

Two options were evaluated:

1. **Global offsets** — a single monotonic sequence across all tenants. Every tenant sees events from the same global log, filtered by tenant ID.
2. **Per-tenant offsets** — each tenant owns an independent event sequence. Offset `0` means the first event _for that tenant_.

---

## Decision

**Per-tenant offsets are the canonical model.**

Each `(tenantId, offset)` pair addresses an event within that tenant's private stream. The `Offset` type (`Offset.zero()`, `Offset.latest()`) is always resolved relative to the calling tenant's stream.

---

## Rationale

| Criterion | Global Offsets | Per-Tenant Offsets |
| --- | --- | --- |
| Tenant isolation | Requires filter at every read | Enforced structurally |
| Offset leakage | Global offset hints at tenant activity rates | None |
| Compaction / archival | Must compact across tenants together | Independent per tenant |
| Horizontal scale | Global sequence is a coordination bottleneck | Each tenant can shard independently |
| Tail-from-beginning | Must scan global log and filter | Efficient: start at tenant stream head |
| GDPR / right to erasure | Must tombstone globally | Delete tenant stream entirely |

Per-tenant offsets also align with the `TenantContext` isolation model used throughout Data Cloud (entity store, cache, audit log).

---

## Consequences

- `DataCloudClient.tailEvents(tenantId, TailRequest, Consumer<Event>)` always scopes to `tenantId`.
- `TailRequest.fromBeginning()` means offset 0 _within that tenant's stream_.
- `TailRequest.fromLatest()` means the current head of _that tenant's stream_.
- Cross-tenant event fan-out is not supported at the API layer. Services that need cross-tenant aggregation must hold appropriate admin credentials and issue separate per-tenant calls.
- `InMemoryEventLogStore` maintains a `Map<tenantId, List<Event>>` — one list per tenant.
- `WarmTierEventLogStore` (RocksDB) uses a key prefix of `tenantId/` before offset bytes.
- Storage backends that use a shared table/index must include `tenantId` as part of the primary partition key, not merely as a filter column.

---

## Alternatives Considered

**Global offset with tenant filter**: Rejected because a global offset leaks information about the relative activity levels of other tenants, requires full-log scans for per-tenant tailing, and complicates per-tenant compaction and GDPR erasure.

**Hybrid (global for admin, per-tenant for users)**: Rejected as unnecessarily complex with no clear consumer for a global admin stream in the current platform.
