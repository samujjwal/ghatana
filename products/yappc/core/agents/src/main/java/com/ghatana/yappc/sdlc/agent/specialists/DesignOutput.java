package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from design specialist.
 *
 * @doc.type record
 * @doc.purpose Architecture design output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DesignOutput(
    @NotNull String architectureId,
    @NotNull List<String> components,
    @NotNull Map<String, String> patterns,
    @NotNull Map<String, Object> metadata) {

  public DesignOutput {
    if (architectureId == null || architectureId.isEmpty()) {
      throw new IllegalArgumentException("architectureId cannot be null or empty");
    }
    if (components == null) {
      components = List.of();
    }
    if (patterns == null) {
      patterns = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
