# Module Ownership: platform-event-store

> **ADR Reference**: [ADR-DC-001-MODULE-OWNERSHIP](../docs-generated/07-architecture-decisions/adr-dc-001-module-ownership.md)

| Field           | Value                                               |
|-----------------|-----------------------------------------------------|
| **Team**        | Data Cloud Platform                                 |
| **Slack**       | `#data-cloud-platform`                              |
| **On-call**     | Data Cloud Platform On-call                         |
| **Tech Lead**   | Data Cloud Platform Lead                            |
| **Last Reviewed** | 2026-04-29                                        |

## Responsibility

Provides the PostgreSQL-backed warm-tier implementation of the `EventLogStore` SPI (`WarmTierEventLogStore`). This module was extracted from `platform-launcher` (DC-A10) to remove the heavyweight storage-plugin transitive dependencies from consumers such as `feature-store-ingest` that only need the event log capability.

**In scope:**
- `WarmTierEventLogStore` — durable, queryable event log backed by PostgreSQL.
- Tenant-safe, offset-based, and time-range reads.
- Non-blocking JDBC wrapping via `Promise.ofBlocking` on a virtual-thread executor.
- Polling tail subscription for real-time consumers.

**Out of scope:**
- Hot-tier (Redis), cool-tier (Iceberg), cold-tier (S3) — those remain in `platform-plugins` / `platform-launcher`.
- Event transformation or enrichment — belongs in `feature-store-ingest` or an application layer.

## Key Interfaces

| Type | Package | Role |
|------|---------|------|
| `WarmTierEventLogStore` | `com.ghatana.datacloud.storage` | Primary export |
| `EventLogStore` (SPI) | `com.ghatana.datacloud.spi` | Implemented interface (from `spi` module) |

## Dependencies

| Module | Reason |
|--------|--------|
| `products:data-cloud:spi` | `EventLogStore` SPI and supporting types |
| `platform:java:core` | ActiveJ Promise, Offset, TenantContext |

## Consumers

| Consumer | Reason |
|----------|--------|
| `products:data-cloud:platform-launcher` | Re-exports via `api` — used in DI module wiring |
| `products:data-cloud:feature-store-ingest` | Direct dependency for PostgreSQL ingest path |
