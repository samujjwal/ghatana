package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from BusinessAnalyst agent.
 *
 * @doc.type record
 * @doc.purpose Expert business analyst for requirements elicitation and domain modeling output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BusinessAnalystOutput(@NotNull String analysisId, @NotNull List<String> requirements, @NotNull Map<String, Object> domainModel, @NotNull Map<String, Object> metadata) {
  public BusinessAnalystOutput {
    if (analysisId == null || analysisId.isEmpty()) {
      throw new IllegalArgumentException("analysisId cannot be null or empty");
    }
    if (requirements == null) {
      requirements = List.of();
    }
    if (domainModel == null) {
      domainModel = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
