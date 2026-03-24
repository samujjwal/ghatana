# Owner: Data-Cloud Platform Module

**Team:** Data-Cloud Platform Team  
**Slack:** #platform-data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-03-23  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Core domain types, service interfaces, and platform integration for Data-Cloud products.
This module is the primary dependency for all other Data-Cloud sub-modules and external consumers.

Key responsibilities:
- `DataCloudService` — top-level domain service interface
- `EntityStore` — entity persistence abstraction
- `QueryEngine` — query execution abstraction
- Tenant isolation contracts
- Governance and metadata domain types

## Dependency Constraints

- MUST NOT depend on `launcher`, `agent-registry`, or `feature-store-ingest`
- MAY depend on `spi` and `contracts/*`
- External products depend on this module via `products:data-cloud:platform`

## Consumers

| Consumer | Usage |
|----------|-------|
| `products:data-cloud:launcher` | All HTTP handler domain calls |
| `products:agentic-event-processor` | Event pipeline |
| `products:yappc` | Entity read path |
| `products:data-cloud:agent-registry` | EntityRecord persistence |
| `products:data-cloud:feature-store-ingest` | Feature vector writes |
