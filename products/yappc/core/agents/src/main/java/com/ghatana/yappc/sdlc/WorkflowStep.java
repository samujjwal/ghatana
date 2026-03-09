package com.ghatana.yappc.sdlc;

import io.activej.promise.Promise;

/**
 * Canonical AEP workflow step contract for SDLC agents.
 *
 * <p>MUST: strict input/output validation, deterministic gates, audit + event emission.
 *
 * <p>Per Ghatana coding standards, all async operations use ActiveJ Promise. NEVER use
 * CompletableFuture or Reactor mixed with ActiveJ.
 *
 * @param <I> Input type for this step
 * @param <O> Output type for this step
 * @doc.type interface
 * @doc.purpose Canonical workflow step contract for all SDLC phase agents
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface WorkflowStep<I, O> {

  /**
   * Returns the unique step name (e.g., "architecture.intake").
   *
   * @return fully qualified step name
   */
  String stepName();

  /**
   * Returns the contract definition (input/output schemas, capabilities).
   *
   * @return immutable step contract
   */
  StepContract contract();

  /**
   * Validates input before execution. Called deterministically before execute().
   *
   * @param input the input to validate
   * @return validation result with errors if invalid
   */
  ValidationResult validateInput(I input);

  /**
   * Executes this workflow step asynchronously using ActiveJ Promise.
   *
   * <p>CRITICAL: Never block the eventloop. Use Promise.ofBlocking() for IO.
   *
   * @param input validated input data
   * @param context execution context with tenant, run, and configuration
   * @return Promise of step result (never null)
   */
  Promise<StepResult<O>> execute(I input, StepContext context);
}
