package com.ghatana.yappc.sdlc.agent;

import com.ghatana.yappc.sdlc.StepContext;
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
}
