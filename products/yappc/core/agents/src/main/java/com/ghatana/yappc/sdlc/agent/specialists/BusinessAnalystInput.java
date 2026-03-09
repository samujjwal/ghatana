package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for BusinessAnalyst agent.
 *
 * @doc.type record
 * @doc.purpose Expert business analyst for requirements elicitation and domain modeling input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BusinessAnalystInput(@NotNull String projectId, @NotNull String stakeholderInput, @NotNull Map<String, Object> domainContext) {
  public BusinessAnalystInput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (stakeholderInput == null || stakeholderInput.isEmpty()) {
      throw new IllegalArgumentException("stakeholderInput cannot be null or empty");
    }
    if (domainContext == null) {
      domainContext = Map.of();
    }
  }
}
