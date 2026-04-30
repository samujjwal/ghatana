# Owner: Data-Cloud Platform Governance

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Data governance, privacy, audit, and policy enforcement for the Data-Cloud product.
Owns PII masking, data retention enforcement, redaction pipelines, rate limiting,
tenant-level access controls, and audit trail emission.

All governance-sensitive operations in the product must route through this module's
services rather than implementing governance inline.

## Key Interfaces

| Interface/Class | Purpose |
|-----------------|---------|
| `GovernanceService` | Policy evaluation and enforcement |
| `RedactionPipeline` | PII masking and field-level redaction |
| `RetentionService` | Data retention rule evaluation and enforcement |
| `AuditEmitter` | Structured audit event emission |

## Dependencies

- `platform:java:audit` — audit event infrastructure
- `platform:java:security` — tenant access control primitives
- `products:data-cloud:spi` — `EventLogStore` (audit event persistence)

## Consumers

- `products:data-cloud:platform-launcher` — governance wiring at startup
- `products:data-cloud:platform-api` — governance middleware in request handlers
