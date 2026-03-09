package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ProductsOfficer agent.
 *
 * @doc.type record
 * @doc.purpose Strategic products officer managing portfolio vision and priorities input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProductsOfficerInput(@NotNull String portfolioId, @NotNull List<String> objectives, @NotNull Map<String, Object> constraints) {
  public ProductsOfficerInput {
    if (portfolioId == null || portfolioId.isEmpty()) {
      throw new IllegalArgumentException("portfolioId cannot be null or empty");
    }
    if (objectives == null) {
      objectives = List.of();
    }
    if (constraints == null) {
      constraints = Map.of();
    }
  }
}
