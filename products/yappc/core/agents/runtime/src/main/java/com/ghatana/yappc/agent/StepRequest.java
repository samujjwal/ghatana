package com.ghatana.yappc.agent;

import com.ghatana.yappc.agent.StepContext;
import org.jetbrains.annotations.NotNull;

/**
 * Wrapper combining step input with execution context for agent processing.
 *
 * <p>This class bridges YAPPC's separate input/context model with the agent framework's
 * single-input model by bundling them together.
 *
 * @param <I> The type of the workflow step input
 * @doc.type record
 * @doc.purpose Bundle step input with execution context
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StepRequest<I>(@NotNull I input, @NotNull StepContext context) {
  /**
   * Creates a new step request.
   *
   * @param input the workflow step input
   * @param context the execution context
   */
  public StepRequest {
    if (input == null) {
      throw new IllegalArgumentException("input cannot be null");
    }
    if (context == null) {
      throw new IllegalArgumentException("context cannot be null");
    }
  }

  /**
   * Factory method creating a step request with a default execution context.
   *
   * <p>Uses a minimal context suitable for testing or single-step evaluation.
   * The stepId parameter is used as the run ID in the context.
   *
   * @param stepId the step identifier (used as run ID in context)
   * @param input  the step input
   * @param <I>    input type
   * @return new StepRequest with default context
   */
  public static <I> StepRequest<I> of(@NotNull String stepId, @NotNull I input) {
    StepContext defaultContext = new StepContext(
        "system",       // tenantId
        stepId,         // runId
        "default",      // phase
        "default",      // configSnapshotId
        new Budget(0L, 1.0, 60_000L), // budget
        null,           // flags
        null            // trace
    );
    return new StepRequest<>(input, defaultContext);
  }
}

