# Owner: Data-Cloud Integration Tests

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Cross-module integration test suite for the Data-Cloud product.
Verifies end-to-end behaviour across module boundaries including entity
persistence, event streaming, analytics pipelines, governance flows, and
AI-assist backend interactions.

Tests in this module require a live PostgreSQL instance (via Testcontainers)
and optionally Redis and Kafka containers.

## Key Test Areas

| Area | Description |
|------|-------------|
| Entity + event flows | Create, update, and delete entities with event propagation |
| Analytics pipelines | Query and aggregation correctness across storage tiers |
| Governance flows | Redaction, retention, and audit trail validation |
| AI-assist backend | Integration with AI assist backend (27 integration tests) |
| Telemetry assertions | Metrics and tracing correctness for critical journeys |

## Dependencies

- `products:data-cloud:launcher` — full product runtime under test
- Testcontainers (PostgreSQL, Redis, Kafka)
- `platform:java:testing`

## Consumers

- CI — all integration tests must pass before merge
