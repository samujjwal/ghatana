package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from UxDirector agent.
 *
 * @doc.type record
 * @doc.purpose Strategic UX director ensuring coherent user experience output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record UxDirectorOutput(@NotNull String directiveId, @NotNull String uxStrategy, @NotNull List<String> guidelines, @NotNull Map<String, Object> metadata) {
  public UxDirectorOutput {
    if (directiveId == null || directiveId.isEmpty()) {
      throw new IllegalArgumentException("directiveId cannot be null or empty");
    }
    if (uxStrategy == null || uxStrategy.isEmpty()) {
      throw new IllegalArgumentException("uxStrategy cannot be null or empty");
    }
    if (guidelines == null) {
      guidelines = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
