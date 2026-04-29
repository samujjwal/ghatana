# ADR-DC-008: Connector SPI — Source/Sink Lifecycle, Schema Inference, Credentials, Health, Tenancy

**Status:** Accepted  
**Date:** 2026-04-26  
**Authors:** Data Cloud Architecture Team  

## Context

Data Cloud must ingest and egress data from heterogeneous external systems: relational databases, object stores, REST APIs, message streams, and file systems. Each integration point historically required bespoke glue code, duplicate credential management, and ad-hoc health monitoring. This produced unmaintainable one-off connectors, inconsistent schema handling, and no unified tenancy boundary enforcement.

## Decision

1. **Unified Connector SPI**  
   All connectors MUST implement a single `DataCloudConnector` interface with the following lifecycle hooks:
   - `initialize(config)` — load configuration, validate credentials, establish connections.
   - `discover()` — return available datasets/tables/topics with metadata.
   - `inferSchema(dataset)` — return a canonical schema descriptor (field names, types, constraints, PII hints).
   - `readStream(dataset, offset, batchSize)` — produce a batched stream of records.
   - `writeStream(dataset, records)` — accept a batched stream for egress sinks.
   - `checkHealth()` — return structured health: `healthy | degraded | unavailable` with latency and last-success timestamp.
   - `close()` — release connections, flush buffers, emit audit event.

2. **Credential Vault & Rotation**  
   - Connector credentials MUST be stored in the tenant-scoped secret vault (not in connector config plaintext).
   - Each credential entry binds to a `tenantId + connectorId + credentialRole` triple.
   - Rotation is driven by the vault; connectors receive a `onCredentialRotated(newToken)` callback.

3. **Tenancy Isolation**  
   - Every connector instance is provisioned per tenant.
   - Cross-tenant connection pooling is prohibited.
   - Audit events (`connector.initialized`, `connector.read`, `connector.write`) MUST carry `tenantId` and `connectorId`.

4. **Schema Inference Contract**  
   - `inferSchema` returns a JSON Schema-compatible descriptor with Data Cloud-specific extensions:
     - `dataCloudType`: mapped Data Cloud type (`STRING`, `NUMBER`, `BOOLEAN`, `TIMESTAMP`, `JSON`, `REFERENCE`).
     - `piiHint`: `none | email | phone | ssn | custom`.
     - `nullable`, `unique`, `primaryKey` booleans.
   - Inferred schemas are versioned and stored in the collection registry as the initial `schemaVersion`.

5. **Health & Observability**  
   - Each connector exposes a Prometheus-compatible metrics endpoint:
     - `datacloud_connector_read_records_total` (counter, labels: `tenant`, `connector`, `dataset`).
     - `datacloud_connector_read_latency_seconds` (histogram).
     - `datacloud_connector_health_status` (gauge: `1=healthy`, `0=degraded`, `-1=unavailable`).
   - Health failures are surfaced in the Operations Console and feed the capability registry (`/api/v1/capabilities`).

6. **Source/Sink Lifecycle States**  
   - `REGISTERED` → `CONFIGURING` → `CONNECTING` → `HEALTH_CHECKING` → `ACTIVE`.
   - Degradation transitions: `ACTIVE` → `DEGRADED` (retry loop) → `PAUSED` (backoff) → `FAILED` (human review).
   - Terminal state `ARCHIVED` preserves audit trail and schema history.

7. **Asynchronous Backpressure**  
   - Read streams use bounded buffers with explicit backpressure signals (`pause`/`resume` tokens).
   - Write sinks support idempotent batch markers (`batchId`, `tenantId`, `sequence`).

## Consequences

- **Positive:** New sources/sinks can be added without touching core Data Cloud code. Schema inference reduces manual collection creation. Unified health and metrics improve operator trust.
- **Negative:** The SPI surface is strict; third-party connector authors must understand the lifecycle and observability contracts. Health check frequency must be tuned to avoid overwhelming external systems.

## Related

- P1.1 in `data-cloud-implementation-tasks.md`
- `DataCloudConnector.java` (platform SPI)
- `ConnectorHealthHandler.java`
- `SchemaInferenceService.java`
- ADR-DC-003 (Canonical Query Contract) for read/write batch semantics
- ADR-DC-005 (Governance Fail-Closed) for credential and PII handling
