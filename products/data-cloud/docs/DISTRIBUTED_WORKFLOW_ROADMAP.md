# Distributed Workflow Orchestration — Roadmap

> **Status:** Design phase
> **Owner:** Data Cloud / AEP shared (see [Ownership](#ownership))
> **Last Updated:** 2026-05-02

---

## 1. Current Limitation — Single-Process Plugin Execution

Data Cloud workflow execution today runs plugin steps sequentially inside a single JVM process on the ActiveJ event loop.

**Constraints this imposes:**

- No horizontal scale-out of workflow execution across multiple nodes.
- A long-running or CPU-intensive plugin blocks throughput for all other workflows in the same tenant.
- Failure of the host process terminates all in-flight runs with no automatic resumption.
- There is no way to co-locate a plugin step with a remote data source for locality-aware execution.

This model is acceptable for development and low-volume tenants but does not meet production SLAs for Data Cloud customers with high-cardinality pipeline portfolios.

---

## 2. Distributed Scheduler Requirements

The replacement scheduler must satisfy:

| Requirement | Detail |
|---|---|
| **Tenant isolation** | Each tenant's queue and execution budget must be independently bounded. |
| **Step-level parallelism** | Independent pipeline stages within a single run may execute concurrently. |
| **At-least-once delivery** | Every queued step must be dispatched at least once; idempotent plugins must tolerate re-delivery. |
| **Ordered execution within a run** | Steps that share an explicit dependency edge must execute in dependency order. |
| **Deadline-aware scheduling** | SLA deadlines declared in `WorkflowTemplate.slaDeadlineSeconds` must be propagated to the scheduler and used to prioritise dispatch. |
| **Backpressure** | Scheduler must shed load gracefully when worker capacity is exhausted rather than accepting unbounded queue growth. |
| **Observability** | Queue depth, dispatch latency, step execution latency, and error rate must be exposed as Prometheus metrics. |

---

## 3. Multi-Worker Coordination

Workers are stateless execution nodes. The scheduler assigns a step to exactly one worker at a time via a central dispatch queue (initially backed by a persistent message broker — see §5 for the lease model used before a full broker is available).

### Worker protocol

1. **Registration** — on startup a worker registers its capabilities (supported plugin types, resource budget) with the scheduler.
2. **Heartbeat** — worker emits a heartbeat every `N` seconds; scheduler marks the worker unavailable after two missed heartbeats.
3. **Step claim** — worker requests a step from the dispatch queue; scheduler assigns one step at a time unless the worker has declared a higher concurrency limit.
4. **Result reporting** — on completion (success or failure) the worker posts the step result to the scheduler, which updates the run state and enqueues downstream steps.
5. **Graceful drain** — on SIGTERM the worker stops claiming new steps and completes in-progress steps within a configurable drain window before exiting.

### Affinity hints

A plugin may declare `@DataLocalityHint` to request co-location with a named data region. The scheduler respects these hints as soft constraints when assigning workers.

---

## 4. Lease and Lock Model

Distributed step execution requires a lease to prevent two workers from executing the same step concurrently after a worker slow-heartbeat scenario.

### Lease lifecycle

```
CREATED → LEASED → [COMPLETED | FAILED | EXPIRED]
```

| Transition | Trigger |
|---|---|
| `CREATED → LEASED` | Worker successfully acquires the step lease via a conditional write (compare-and-swap or Postgres advisory lock). |
| `LEASED → COMPLETED` | Worker posts a successful result within the lease TTL. |
| `LEASED → FAILED` | Worker posts a failure result; scheduler applies the retry policy (§5). |
| `LEASED → EXPIRED` | Lease TTL passes without a result; scheduler re-enqueues the step as a retry candidate. |

### Lease TTL

`lease_ttl = step.timeoutSeconds + grace_period_seconds`

Grace period is configurable per tenant with a default of 30 s. Expired leases are detected by a lightweight janitor task that runs every `lease_ttl / 2` seconds.

### Preventing double-execution

Steps that have side effects (e.g., writing to an external sink) must implement the `IdempotentPlugin` interface, which accepts a `runId + stepId + attemptNumber` idempotency key. The platform passes this key; the plugin is responsible for deduplication.

---

## 5. Retry and Dead-Letter Model

### Retry policy per step

```json
{
  "maxAttempts": 3,
  "backoffStrategy": "EXPONENTIAL",
  "initialDelayMs": 1000,
  "maxDelayMs": 30000,
  "retryableErrors": ["TRANSIENT_IO", "TIMEOUT", "LEASE_EXPIRED"],
  "nonRetryableErrors": ["VALIDATION_FAILURE", "AUTHORIZATION_DENIED"]
}
```

Each `WorkflowTemplate` may override the per-step retry policy. Tenant-level defaults apply when no override is present.

### Dead-letter queue (DLQ)

A step that exhausts `maxAttempts` is moved to the tenant-scoped DLQ. The DLQ entry records:

- `tenantId`, `runId`, `stepId`, `templateId`
- Last error message and stack excerpt
- All attempt records (timestamp, duration, worker id, error class)

DLQ entries are retained for 30 days (configurable). An operator may replay a DLQ entry manually via the Data Cloud UI or the `POST /api/v1/workflows/runs/{runId}/steps/{stepId}/replay` endpoint.

Automatic DLQ replay is not supported in the initial distributed implementation to avoid replay storms.

---

## 6. High-Availability Topology

### Scheduler HA

The scheduler process runs as an active/standby pair. Leadership election uses the same Postgres advisory lock used for lease coordination, avoiding a dependency on an additional consensus system in the initial deployment.

| Role | Behaviour |
|---|---|
| **Active** | Handles all dispatch, heartbeat tracking, and lease janitor work. |
| **Standby** | Subscribes to the same event bus; promotes itself if the active node's leadership lock lapses for more than `2 × heartbeat_interval`. |

Planned future improvement: move to a Raft-based scheduler cluster when tenant count exceeds the single-node capacity envelope.

### Worker HA

Workers are stateless and horizontally scalable. The recommended minimum is two workers per tenant tier:

| Tier | Min Workers | Max Workers (auto-scale target) |
|---|---|---|
| Free | 1 (shared) | 2 (shared) |
| Standard | 2 | 10 |
| Enterprise | 4 | 50+ |

Auto-scaling is triggered when the 5-minute average dispatch queue depth exceeds `target_workers × 0.7`.

### Data persistence

All scheduling state (run records, step records, leases, DLQ) is persisted in the Data Cloud Postgres schema. The scheduler does not hold authoritative state in memory beyond an in-flight step cache.

---

## 7. Ownership {#ownership}

| Concern | Owner | Notes |
|---|---|---|
| Scheduler core, lease model, DLQ | **Data Cloud** | Lives in `products/data-cloud/workflow/` |
| Plugin execution runtime | **Data Cloud** | Extends existing `WorkflowExecutionService` |
| AEP pipeline execution integration | **AEP** | AEP wraps Data Cloud scheduler via the standard workflow API; AEP does not own the scheduler |
| Cross-product workflow events | **AEP** (via `@ghatana/events`) | AEP publishes `WorkflowStepCompleted` / `WorkflowRunCompleted` platform events |
| Shared broker infrastructure | **Platform infra** | Kafka or equivalent provisioned by the infra team |

**Decision:** Data Cloud owns the distributed scheduler. AEP consumes it. Neither product embeds a second scheduler implementation.

---

## 8. Implementation Phases

| Phase | Scope | Target |
|---|---|---|
| **Phase 1** | Lease-based single-node coordination with Postgres advisory locks. Worker pool on same host. DLQ foundation. | Q3 2026 |
| **Phase 2** | Out-of-process worker deployment. Broker-backed dispatch queue. Horizontal auto-scaling. | Q4 2026 |
| **Phase 3** | Active/standby scheduler HA. Tenant-level resource quotas. Replay API for DLQ. | Q1 2027 |
| **Phase 4** | Data-locality affinity scheduling. Cross-region federation. Raft-based scheduler cluster. | Q2 2027+ |

---

## 9. Open Questions

1. **Broker selection**: Kafka vs. Postgres `LISTEN/NOTIFY` for Phase 2 dispatch queue — decision needed before Phase 2 kickoff.
2. **Plugin sandboxing**: Should distributed workers sandbox plugin JVM code in a separate ClassLoader or process? Impacts security isolation and upgrade velocity.
3. **Tenant quota enforcement**: Enforce at the dispatch queue (reject enqueue) or at the scheduler (delay dispatch)? Affects SLA fairness model.
4. **Observability ownership**: Who owns the Grafana dashboard for distributed scheduler metrics — Data Cloud team or central infra?
