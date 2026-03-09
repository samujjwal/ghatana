package com.ghatana.yappc.sdlc;

/**
 * Execution context passed to all workflow steps.
 *
 * @param tenantId tenant identifier for multi-tenancy isolation
 * @param runId unique run identifier
 * @param phase current SDLC phase
 * @param configSnapshotId pinned configuration snapshot for reproducibility
 * @param budget execution budget (tokens, cost, time)
 * @param flags feature flags for conditional behavior
 * @param trace distributed tracing context
 * @doc.type record
 * @doc.purpose Immutable execution context for workflow steps
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StepContext(
    String tenantId,
    String runId,
    String phase,
    String configSnapshotId,
    Budget budget,
    FeatureFlags flags,
    TraceContext trace) {}
