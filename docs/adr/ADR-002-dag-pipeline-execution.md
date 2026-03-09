# ADR-002: DAG-Based Pipeline Execution with Topological Sort

**Status:** Accepted  
**Date:** 2026-01-15  
**Decision Makers:** Platform Team  
**Phase:** 1 — Foundation  

## Context

The AEP pipeline system needed to evolve from simple linear operator chains (`OperatorChain`) to a full directed acyclic graph (DAG) execution model supporting:
- Branching (one stage feeds multiple downstream stages)
- Error routing (failed stages route to error handlers)
- Conditional routing via edge labels (primary, error, fallback, broadcast)
- Cycle detection to prevent infinite loops

## Decision

Implement `PipelineExecutionEngine` using **Kahn's algorithm for topological sort**:

1. **Validate** pipeline structure — check for cycles via in-degree analysis
2. **Topologically sort** all stages using Kahn's algorithm (BFS-based)
3. **Execute** stages in sorted order:
   - Source stages (in-degree 0) receive the input event
   - Each stage resolves its operator via `OperatorCatalog`
   - Outputs route through labeled edges: `primary`, `error`, `fallback`, `broadcast`
4. **Collect** terminal stage outputs (out-degree 0) into `PipelineExecutionResult`

Edge labels control routing:
- `primary` — normal data flow
- `error` — exception path (stage failure)
- `fallback` — when primary stage returns empty/null
- `broadcast` — fan-out to all connected stages

## Rationale

- **Kahn's algorithm** is O(V+E), well-understood, and naturally detects cycles
- **Edge labels** decouple routing logic from operator implementation
- **Stateless engine** — all state scoped to `PipelineExecutionContext`, enabling safe concurrent execution
- **Promise-based** execution via ActiveJ aligns with the non-blocking architecture
- `PipelineExecutionContext` carries `tenantId`, `executionId`, `deadline`, and `operatorCatalog` per-execution

## Consequences

- Pipelines must be acyclic — feedback loops require explicit checkpoint-and-resubmit patterns
- Stage execution is sequential within a single pipeline instance (no intra-pipeline parallelism yet)
- The `OperatorCatalog` must be populated before pipeline execution — missing operators fail fast
- `PipelineExecutionResult` returns timing per stage for observability

## Alternatives Considered

1. **Linear chain only** — rejected; insufficient for complex event processing
2. **Parallel stage execution within a pipeline** — deferred; adds complexity with limited initial benefit
3. **Reactive Streams (Project Reactor / RxJava)** — rejected; ActiveJ Promise is the standard async primitive
