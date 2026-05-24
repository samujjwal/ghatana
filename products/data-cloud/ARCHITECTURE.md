# Data-Cloud Architecture

**Status:** Canonical boundary summary  
**Owner:** Data-Cloud maintainers  
**Last reviewed:** 2026-05-23  
**Detailed architecture:** `docs/architecture/PLANE_ARCHITECTURE.md`

Data-Cloud is the governed data/storage substrate. It owns governed entity storage, metadata, schemas, audit, retention, encryption support, queryable historical metadata, and pluggable persistence.

## Non-Goals

Data-Cloud does not own:

- Complex event processing.
- Pattern matching semantics.
- Pattern learning/adaptation.
- EventCloud subscriptions, tailing, or windowing.
- Agent orchestration.
- PatternSpec/EPL.
- Predictive/recommended pattern lifecycle.

Those belong to AEP.

## Core Responsibilities

- Durable entity metadata.
- Schemas and schema metadata.
- Data quality and queryable metadata.
- Audit logging.
- Retention and encryption support.
- Pluggable persistence backends.
- Data-Cloud EventLog for simple storage-plane append, replay, and audit records.
- Storage plugin SPI used by downstream products and AEP.

## Data-Cloud Responsibilities for AEP

For AEP, Data-Cloud provides:

1. Durable entity metadata.
2. Pattern registry metadata storage when called through AEP services.
3. Storage plugins for EventCloud persistence.
4. Schema metadata storage.
5. Audit logging.
6. Retention and encryption support.
7. Queryable historical metadata.

## EventCloud Boundary

EventCloud is AEP-owned.

Data-Cloud may provide storage plugins used by AEP's EventCloud. These plugins are persistence implementations only. They must not leak CEP, PatternSpec, operator, or learning semantics into Data-Cloud.

## Dependency Rule

```text
Data-Cloud must not depend on AEP.
AEP may use Data-Cloud storage plugins through stable SPI.
Data-Cloud storage plugin examples must not include pattern matching logic.
```

## Architecture Checks

Boundary tests should enforce:

- Data-Cloud must not import AEP modules.
- Data-Cloud must not import PatternSpec, EPL, or EventOperator runtime.
- AEP EventCloud SPI must not depend on Data-Cloud implementation classes.
- Data-Cloud EventLog remains a storage-plane primitive, not EventCloud.
