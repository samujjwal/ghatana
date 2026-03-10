package com.ghatana.yappc.agent.leads;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Result of architecture phase coordination.
 *
 * @param stepResults results for each architecture step
 * @param totalDurationMs total execution time
 * @param allStepsSuccessful true if all steps succeeded
 * @param metadata additional result metadata
 * @doc.type record
 * @doc.purpose Output from architecture phase lead
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ArchitectureResult(
    @NotNull Map<String, StepResult> stepResults,
    long totalDurationMs,
    boolean allStepsSuccessful,
    @NotNull Map<String, Object> metadata) {

  public ArchitectureResult {
    if (stepResults == null) {
      throw new IllegalArgumentException("stepResults cannot be null");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }

  /** Result for a single architecture step. */
  public record StepResult(
      @NotNull String stepName,
      @NotNull String status,
      long durationMs,
      boolean needsReview,
      @NotNull Map<String, Object> artifacts) {

    public StepResult {
      if (stepName == null || stepName.isEmpty()) {
        throw new IllegalArgumentException("stepName cannot be null or empty");
      }
      if (status == null || status.isEmpty()) {
        throw new IllegalArgumentException("status cannot be null or empty");
      }
      if (artifacts == null) {
        artifacts = Map.of();
      }
    }
  }
}
