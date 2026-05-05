# Data Fabric — Production Promotion Plan

> **Status:** Design / Alpha
> **Owner:** Data Cloud
> **Last Updated:** 2026-05-02

---

## 1. Current State

The Data Fabric subsystem provides a read-only view of the connectors, lineage, and health signals wired into a tenant's Data Cloud deployment. The current implementation renders topology data from a static/mocked API layer — the UI and domain model are complete, but the backend does not yet expose live connector health or lineage freshness from the real data infrastructure.

**Scope of current alpha gaps:**

| Concern | Gap |
|---|---|
| Connector health | Simulated in-memory status; not derived from real connector I/O |
| Lineage / freshness | Schema-level lineage exists; field-level freshness is not tracked |
| Fabric metrics API | No live Prometheus counters exposed per connector |
| Topology diffing | No baseline snapshot; cannot detect drift from last known good state |
| Anomaly overlays | No ML or threshold-based anomaly detection integrated |
| Governance overlays | Policy evaluation results not projected onto the topology graph |
| E2E coverage | No integration test exercises the live fabric API path end-to-end |

---

## 2. Real Fabric Metrics API

### Goals

- Expose per-connector and aggregate Prometheus counters at `/api/v1/fabric/metrics`.
- Integrate with the existing `platform:java:observability` module for counter registration.
- Allow the UI `DataFabricPage` to replace mock data with live metrics without a frontend change.

### Required counters

| Metric | Labels | Description |
|---|---|---|
| `fabric_connector_health` (gauge 0/1) | `tenantId`, `connectorId`, `connectorType` | 1 = healthy, 0 = degraded or unavailable |
| `fabric_connector_records_ingested_total` (counter) | `tenantId`, `connectorId` | Cumulative records ingested since last restart |
| `fabric_connector_errors_total` (counter) | `tenantId`, `connectorId`, `errorType` | Ingestion errors by type |
| `fabric_connector_latency_seconds` (histogram) | `tenantId`, `connectorId` | End-to-end ingest latency distribution |
| `fabric_lineage_nodes_total` (gauge) | `tenantId` | Number of tracked lineage nodes |
| `fabric_lineage_edges_total` (gauge) | `tenantId` | Number of tracked lineage edges |

### Implementation path

1. Add `ConnectorHealthCollector` in `products/data-cloud/fabric/src/main/java/.../fabric/metrics/` that registers the above gauges using `platform:java:observability`.
2. Wire `ConnectorHealthCollector` into the `DataFabricService` lifecycle.
3. Expose via the existing `/metrics` endpoint (Prometheus format) — no new endpoint needed.
4. Add a REST `GET /api/v1/fabric/metrics/summary` that returns a JSON snapshot for the UI (mirrors the Prometheus data in a structured form).

---

## 3. Connector Health Model

### ConnectorHealth domain object

```java
/**
 * @doc.type class
 * @doc.purpose Immutable snapshot of a connector's health at a point in time.
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConnectorHealth(
    String connectorId,
    String connectorType,
    ConnectorStatus status,
    String tenantId,
    Instant checkedAt,
    @Nullable String errorMessage,
    long recordsIngested,
    long errorCount
) {}

public enum ConnectorStatus { HEALTHY, DEGRADED, UNAVAILABLE, UNKNOWN }
```

### Health check protocol

Each connector plugin implements `ConnectorHealthCheck`:

```java
public interface ConnectorHealthCheck {
    Promise<ConnectorHealth> check(String tenantId, String connectorId);
}
```

The `DataFabricService` aggregates health checks on a configurable interval (default: 60 s). Results are cached for the UI and published as metric updates.

---

## 4. Lineage and Freshness Model

### Lineage nodes and edges

- **Node**: represents a data entity (table, stream, file collection, API resource).
- **Edge**: represents a flow (connector ingestion, pipeline transformation, export).

Node attributes include:
- `entityId`, `entityType` (table, stream, file, api)
- `lastSeenAt` (timestamp of last successful write)
- `freshnessSlaSeconds` (SLA declared in the workflow template or connector config)
- `freshnessStatus` (FRESH, STALE, UNKNOWN)

### Freshness computation

```
freshnessStatus =
  if now - lastSeenAt <= freshnessSlaSeconds → FRESH
  if now - lastSeenAt <= 2 × freshnessSlaSeconds → STALE
  else → UNKNOWN (data missing or pipeline not running)
```

Freshness is recomputed by the `LineageFreshnessEvaluator` on a background task triggered after each pipeline run completion.

---

## 5. Topology Diffing

Topology diffing detects when the live connector/lineage graph diverges from the last approved baseline.

### Diff algorithm

1. On demand (or on a nightly schedule), snapshot the current topology graph into a `TopologySnapshot` record.
2. Compare the snapshot against the most recently approved baseline using graph isomorphism at the node/edge ID level.
3. Surface additions (new connectors/edges), removals, and type changes as diff entries.
4. Expose diff via `GET /api/v1/fabric/topology/diff?baseline={snapshotId}`.

### Baseline approval flow

An operator may promote any snapshot to the approved baseline via `POST /api/v1/fabric/topology/baselines`. The previous baseline is retained for audit. Baselines older than 90 days are archived.

---

## 6. Anomaly and Failure Overlays

### Threshold-based anomaly detection (Phase 1)

| Signal | Anomaly condition |
|---|---|
| `fabric_connector_errors_total` delta | Error rate in last 5 min > 10 × 1-hour baseline |
| `fabric_connector_latency_seconds` P99 | P99 > 3 × 1-hour P99 baseline |
| `freshnessStatus` | UNKNOWN for any node with `freshnessSlaSeconds` set |
| Connector status | Any connector transitions from HEALTHY → UNAVAILABLE |

Anomalies are surfaced as `FabricAnomaly` events published to the AEP event bus and overlaid on the topology graph in the UI.

### ML-based anomaly detection (Phase 2)

Phase 2 replaces static thresholds with an isolation-forest model trained on tenant-scoped metric history. Model training is an AEP agent responsibility; Data Cloud exposes the raw metric history via an internal API.

---

## 7. Governance Overlays

Policy evaluation results from the AEP policy engine are projected onto the Data Fabric topology:

- Nodes and edges that violate active policies are highlighted in the UI.
- Policy overlay is fetched from `GET /api/v1/fabric/topology/governance-overlay`.
- The overlay is re-evaluated on demand or when the policy set changes (triggered via the AEP `PolicyUpdated` platform event).

This integration is one-directional: Data Cloud reads policy results; AEP does not read from Data Fabric directly.

---

## 8. End-to-End Tests

Each of the following scenarios must be covered by an integration test (`products/data-cloud/fabric/src/test/`):

| Test | Description |
|---|---|
| `ConnectorHealthCheckIT` | Real connector plugin returns HEALTHY; metrics counter incremented |
| `LineageFreshnessIT` | Pipeline run completes; lineage node freshness transitions to FRESH |
| `TopologyDiffIT` | Node added to live graph; diff endpoint reports it as an addition |
| `AnomalyDetectionIT` | Connector error rate exceeds threshold; anomaly event is published |
| `GovernanceOverlayIT` | Policy violation on a node appears in governance overlay response |
| `FabricMetricsEndpointIT` | `/api/v1/fabric/metrics/summary` returns all expected connector health fields |

---

## 9. Implementation Phases

| Phase | Scope | Target |
|---|---|---|
| **Phase 1** | Real connector health API, Prometheus metrics, freshness model, E2E coverage | Q3 2026 |
| **Phase 2** | Topology diffing, baseline approval flow, governance overlays | Q4 2026 |
| **Phase 3** | Threshold-based anomaly detection, anomaly event bus integration | Q1 2027 |
| **Phase 4** | ML-based anomaly detection (isolation forest), Phase 2 AEP agent handoff | Q2 2027+ |
