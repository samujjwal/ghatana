# Owner: Data-Cloud SPI (Service Provider Interface)

**Team:** Data-Cloud Platform Team  
**Slack:** #platform-data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-03-23  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Defines the stable storage provider interfaces consumed by all Data-Cloud modules
and external products. Implementations are provided by the split Data-Cloud runtime modules,
primarily `products:data-cloud:platform-launcher`.

Key interfaces:
- `StorageProvider` — entity persistence SPI
- `IndexProvider` — search/query SPI
- `EventLogStore` — append-only event log SPI

## Stability Contract

All interfaces in this module are **stable API**. Breaking changes require:
1. A new interface version (e.g., `StorageProviderV2`)
2. A deprecation notice with migration period (minimum one sprint)
3. Architecture Review approval

## Consumers

| Consumer | Interface Used |
|----------|---------------|
| `products:data-cloud:platform-launcher` | All three providers |
| `products:agentic-event-processor` | `EventLogStore` |
| `products:yappc` | `StorageProvider`, `EventLogStore` |
| `products:data-cloud:agent-registry` | `EventLogStore` |
