# Owner: Data-Cloud Platform Event

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Event persistence, replay, and streaming for the Data-Cloud product.
Implements the four-tier event log (journal, hot, warm, cold), event
idempotency, and sustained replay flows.

Event publishing is append-only and idempotent by event ID to support
at-least-once delivery patterns safely.

## Key Interfaces

| Interface/Class | Purpose |
|-----------------|---------|
| `EventLogStore` | Append, tail, replay SPI (implemented by `WarmTierEventLogStore`) |
| `EventBus` | In-process publish/subscribe for domain events |
| `EventReplayService` | Offset-tracked replay for consumers |

## Dependencies

- `products:data-cloud:spi` — `EventLogStore` port interface
- `platform:java:database` — PostgreSQL-backed warm tier persistence
- `platform:java:observability` — event throughput metrics

## Consumers

- `products:data-cloud:platform-launcher` — event wiring at startup
- `products:data-cloud:feature-store-ingest` — event tailing
- `products:aep:*` — event-cloud integration (via SPI only)
