package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ReleaseOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Release pipeline orchestration result output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReleaseOrchestratorOutput(
    @NotNull String releaseId,
    @NotNull String status,
    @NotNull List<String> completedGates,
    @NotNull List<String> pendingGates,
    @NotNull String sbomDigest,
    @NotNull Map<String, Object> metadata) {

  /** Release status constants. */
  public static final String STATUS_READY = "READY";
  public static final String STATUS_BLOCKED = "BLOCKED";
  public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
  public static final String STATUS_RELEASED = "RELEASED";

  public ReleaseOrchestratorOutput {
    if (releaseId == null || releaseId.isEmpty()) {
      throw new IllegalArgumentException("releaseId cannot be null or empty");
    }
    if (status == null || status.isEmpty()) {
      throw new IllegalArgumentException("status cannot be null or empty");
    }
    if (completedGates == null) {
      completedGates = List.of();
    }
    if (pendingGates == null) {
      pendingGates = List.of();
    }
    if (sbomDigest == null) {
      sbomDigest = "";
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
