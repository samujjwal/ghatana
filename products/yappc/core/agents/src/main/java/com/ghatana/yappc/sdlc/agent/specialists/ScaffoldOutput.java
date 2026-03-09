package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from scaffold specialist.
 *
 * @doc.type record
 * @doc.purpose Code scaffolding output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ScaffoldOutput(
    @NotNull String scaffoldId,
    @NotNull List<String> generatedFiles,
    @NotNull Map<String, Integer> filesByType,
    @NotNull Map<String, Object> metadata) {

  public ScaffoldOutput {
    if (scaffoldId == null || scaffoldId.isEmpty()) {
      throw new IllegalArgumentException("scaffoldId cannot be null or empty");
    }
    if (generatedFiles == null) {
      generatedFiles = List.of();
    }
    if (filesByType == null) {
      filesByType = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
