package com.ghatana.yappc.agent;

/**
 * Execution context passed to all workflow steps.
 *
 * @doc.type class
 * @doc.purpose Immutable execution context for workflow steps
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class StepContext {

  private final String tenantId;
  private final String runId;
  private final String phase;
  private final String configSnapshotId;
  private final Budget budget;
  private final FeatureFlags flags;
  private final TraceContext trace;

  /** Full constructor with all fields. */
  public StepContext(
      String tenantId,
      String runId,
      String phase,
      String configSnapshotId,
      Budget budget,
      FeatureFlags flags,
      TraceContext trace) {
    this.tenantId = tenantId;
    this.runId = runId;
    this.phase = phase;
    this.configSnapshotId = configSnapshotId;
    this.budget = budget;
    this.flags = flags;
    this.trace = trace;
  }

  /**
   * Convenience constructor without feature flags and trace context (defaults to null).
   * Accepts any {@link Budget} implementation including {@link StepBudget}.
   * Parameter order: runId, tenantId, phase, configSnapshotId, budget.
   */
  public StepContext(
      String runId,
      String tenantId,
      String phase,
      String configSnapshotId,
      Budget budget) {
    this(tenantId, runId, phase, configSnapshotId, budget, null, null);
  }

  /**
   * Convenience constructor that accepts a {@link StepBudget} (simplified budget).
   * Converts StepBudget to a full Budget with zero token limit.
   * Parameter order: runId, tenantId, phase, configSnapshotId, stepBudget.
   */
  public StepContext(
      String runId,
      String tenantId,
      String phase,
      String configSnapshotId,
      StepBudget stepBudget) {
    this(tenantId, runId, phase, configSnapshotId,
        stepBudget != null ? stepBudget.toBudget() : null, null, null);
  }

  public String tenantId() { return tenantId; }
  public String runId() { return runId; }
  public String phase() { return phase; }
  public String configSnapshotId() { return configSnapshotId; }
  public Budget budget() { return budget; }
  public FeatureFlags flags() { return flags; }
  public TraceContext trace() { return trace; }
}

