package com.ghatana.yappc.sdlc.agent.coordinator;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Result of platform delivery coordination.
 *
 * @param phaseResults results for each executed phase
 * @param totalExecutionTimeMs total time taken in milliseconds
 * @param overallSuccess true if all phases succeeded
 * @param metadata additional result metadata
 * @doc.type record
 * @doc.purpose Output from platform delivery coordinator
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeliveryResult(
    @NotNull Map<String, PhaseResult> phaseResults,
    long totalExecutionTimeMs,
    boolean overallSuccess,
    @NotNull Map<String, Object> metadata) {

  public DeliveryResult {
    if (phaseResults == null) {
      throw new IllegalArgumentException("phaseResults cannot be null");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }

  /** Result for a single phase execution. */
  public record PhaseResult(
      @NotNull String phase,
      @NotNull String status,
      long executionTimeMs,
      @NotNull Map<String, Object> output) {

    public PhaseResult {
      if (phase == null || phase.isEmpty()) {
        throw new IllegalArgumentException("phase cannot be null or empty");
      }
      if (status == null || status.isEmpty()) {
        throw new IllegalArgumentException("status cannot be null or empty");
      }
      if (output == null) {
        output = Map.of();
      }
    }
  }
}
