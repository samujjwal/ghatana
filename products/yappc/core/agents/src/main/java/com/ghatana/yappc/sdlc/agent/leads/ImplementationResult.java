package com.ghatana.yappc.sdlc.agent.leads;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Result of implementation phase.
 *
 * @doc.type record
 * @doc.purpose Output from implementation phase lead
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ImplementationResult(
    int stepCount,
    boolean buildSuccessful,
    boolean qualityGatePassed,
    @NotNull Map<String, Object> artifacts) {

  public ImplementationResult {
    if (artifacts == null) {
      artifacts = Map.of();
    }
  }
}
