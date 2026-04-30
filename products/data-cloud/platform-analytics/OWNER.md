# Owner: Data-Cloud Platform Analytics

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Analytics and reporting services for the Data-Cloud product.
Provides query engines, aggregation pipelines, time-series analytics,
and reporting surfaces over the four-tier event log and entity store.

## Key Interfaces

| Interface/Class | Purpose |
|-----------------|---------|
| `AnalyticsService` | High-level analytics query API |
| `QueryEngine` | SQL and structured query execution |
| `ReportingPipeline` | Scheduled and on-demand report generation |

## Dependencies

- `products:data-cloud:spi` — EventLogStore, EntityStore
- `products:data-cloud:platform-entity` — entity schemas
- `platform:java:observability` — metrics and tracing

## Consumers

- `products:data-cloud:launcher` — HTTP analytics endpoints
- Internal dashboards and reporting surfaces
