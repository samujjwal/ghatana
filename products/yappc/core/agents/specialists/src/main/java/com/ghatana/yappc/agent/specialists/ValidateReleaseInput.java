package com.ghatana.yappc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for validate release specialist.
 *
 * @doc.type record
 * @doc.purpose Release validation input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ValidateReleaseInput(
    @NotNull String deploymentId, @NotNull List<String> validationChecks) {
  public ValidateReleaseInput {
    if (deploymentId == null || deploymentId.isEmpty()) {
      throw new IllegalArgumentException("deploymentId cannot be null or empty");
    }
    if (validationChecks == null) {
      validationChecks = List.of("smoke-tests", "health-checks", "integration-tests");
    }
  }
}
