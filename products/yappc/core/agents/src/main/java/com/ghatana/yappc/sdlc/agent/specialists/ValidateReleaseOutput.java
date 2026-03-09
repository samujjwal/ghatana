package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from validate release specialist.
 *
 * @doc.type record
 * @doc.purpose Release validation output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ValidateReleaseOutput(
    @NotNull String validationId,
    @NotNull boolean valid,
    @NotNull Map<String, String> checkResults,
    @NotNull Map<String, Object> metadata) {

  public ValidateReleaseOutput {
    if (validationId == null || validationId.isEmpty()) {
      throw new IllegalArgumentException("validationId cannot be null or empty");
    }
    if (checkResults == null) {
      checkResults = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
