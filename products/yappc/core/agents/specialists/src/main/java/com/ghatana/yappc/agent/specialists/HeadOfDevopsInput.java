package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for HeadOfDevops agent.
 *
 * @doc.type record
 * @doc.purpose Strategic head of DevOps overseeing infrastructure and delivery pipelines input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HeadOfDevopsInput(@NotNull String infrastructureId, @NotNull String challenge, @NotNull Map<String, Object> metrics) {
  public HeadOfDevopsInput {
    if (infrastructureId == null || infrastructureId.isEmpty()) {
      throw new IllegalArgumentException("infrastructureId cannot be null or empty");
    }
    if (challenge == null || challenge.isEmpty()) {
      throw new IllegalArgumentException("challenge cannot be null or empty");
    }
    if (metrics == null) {
      metrics = Map.of();
    }
  }
}
