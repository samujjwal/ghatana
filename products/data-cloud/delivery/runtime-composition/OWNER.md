# Owner: Data-Cloud Platform Launcher

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Runtime composition module for the Data-Cloud product. Owns all domain service
wiring, DI module registration, plugin lifecycle, storage tier initialisation,
and the `WarmTierEventLogStore` PostgreSQL-backed event log implementation.

`platform-launcher` is the heavyweight core that `launcher` (HTTP transport) depends on.
`feature-store-ingest` also depends on this module for `WarmTierEventLogStore`; that
dependency is tracked as tech debt (DC-A10) pending extraction of a lighter-weight
event-store contract module.

## Key Interfaces

| Interface/Class | Purpose |
|-----------------|---------|
| `EmbeddedDataCloudClient` | Embedded client for in-process consumers |
| `WarmTierEventLogStore` | PostgreSQL-backed `EventLogStore` implementation |
| `DataCloudStorageModule` | ActiveJ DI module that wires all storage services |
| `EntityStore` impls | Tenant-isolated entity persistence implementations |

## Dependencies

- `products:data-cloud:spi` — all SPI port interfaces
- `products:data-cloud:platform-entity` — entity domain model
- `products:data-cloud:platform-event` — event domain model
- `products:data-cloud:platform-analytics` — analytics services
- `products:data-cloud:platform-governance` — governance services
- `products:data-cloud:platform-config` — configuration
- `products:data-cloud:platform-plugins` — plugin lifecycle
- `platform:java:database` — JDBC, connection pooling, Flyway migrations

## Consumers

- `products:data-cloud:launcher` — HTTP transport wiring
- `products:data-cloud:agent-registry` — entity persistence
- `products:data-cloud:feature-store-ingest` — `WarmTierEventLogStore`
- `products:data-cloud:kernel-bridge` — adapter registration
