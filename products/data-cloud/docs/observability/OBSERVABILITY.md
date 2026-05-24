# Data-Cloud Observability

**Status:** Target standard  
**Owner:** Data-Cloud maintainers

## Scope

Data-Cloud observability covers storage, metadata, schemas, audit, retention, encryption, plugin lifecycle, query paths, and storage-plane events. AEP owns EventCloud, PatternSpec, operator runtime, pattern lifecycle, and adaptive event intelligence metrics.

## Requirements

- Every write emits an audit event.
- Sensitive payload access is traceable.
- Storage plugin calls include tenant, principal, correlation ID, operation type, latency, outcome, and error category.
- Retention and encryption decisions emit structured audit evidence.
- Cross-tenant access attempts fail closed and are observable.

## Boundary

Data-Cloud may emit storage, audit, schema, and plugin metrics consumed by AEP. Data-Cloud must not emit AEP pattern-match, PatternSpec compiler, operator-runtime, or adaptive learning metrics as Data-Cloud-owned signals.
