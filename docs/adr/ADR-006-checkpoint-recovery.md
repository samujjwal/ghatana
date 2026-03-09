# ADR-006: Checkpoint-Based Pipeline Recovery with Exactly-Once Semantics

**Status:** Accepted  
**Date:** 2026-01-28  
**Decision Makers:** Platform Team  
**Phase:** 4 — Production Hardening  

## Context

Pipeline executions can fail mid-flight due to operator errors, network issues, or resource exhaustion. Without a recovery mechanism, the entire pipeline must re-execute from the beginning, wasting compute and potentially producing duplicate side effects.

## Decision

Implement a **step-level checkpoint system** with idempotency guarantees:

**`CheckpointStore` interface** — 13 methods providing full lifecycle management:

| Category | Methods |
|----------|---------|
| **Create** | `createExecution(tenantId, pipelineId, instanceId, idempotencyKey, initialState)` |
| **Update** | `updateCheckpoint(instanceId, stepId, stepName, status, result, state)` |
| **Query** | `findByInstanceId`, `findByIdempotencyKey`, `findByPipelineId`, `findActive`, `findStale` |
| **Complete** | `completeExecution(instanceId, status, finalResult)` |
| **Recovery** | `getLastSuccessfulStep(instanceId)`, `isExecutionAllowed(instanceId)` |
| **Housekeeping** | `cleanupOldCheckpoints(completedBefore)`, `isDuplicate(tenantId, idempotencyKey)` |
| **Step-level** | `recordStepCheckpoint(instanceId, stepCheckpoint)` |

**Key design choices:**
1. **Idempotency keys** are tenant-scoped: `tenantId + "::" + idempotencyKey` composite key
2. **Step checkpoints** record per-step state, enabling resume from last successful step
3. **Status progression**: `CREATED → RUNNING → STEP_SUCCESS* → COMPLETED/FAILED`
4. **Stale detection**: `findStale(Instant)` identifies stuck executions for recovery
5. **Cleanup**: `cleanupOldCheckpoints(Instant)` removes completed checkpoints older than threshold

**Implementations:**
- `InMemoryCheckpointStore` — ConcurrentHashMap-backed, for integration testing
- Flyway migration `V001__create_pipeline_checkpoint_tables.sql` provides the JDBC schema

## Rationale

- **Step-level** granularity minimizes re-computation on failure
- **Idempotency keys** prevent duplicate processing of the same event
- **Tenant-scoped idempotency** allows the same logical key across different tenants
- **Stale detection** enables automatic recovery of orphaned executions
- The interface is storage-agnostic — implementations can be in-memory, JDBC, or distributed

## Consequences

- Every pipeline step must be checkpointed — adds I/O overhead per stage
- Checkpoints grow linearly with executions — requires periodic cleanup
- Resume-from-checkpoint assumes operator idempotency for replayed steps
- The `InMemoryCheckpointStore` is not durable — production must use JDBC implementation

## Alternatives Considered

1. **Pipeline-level checkpoint only** — rejected; too coarse, wastes compute on partial failures
2. **Event sourcing for recovery** — rejected; adds complexity for marginal benefit at this stage
3. **External orchestrator (Temporal/Cadence)** — deferred; heavy dependency, not needed for current scale
