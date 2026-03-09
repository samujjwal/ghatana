package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from SbomGenerator agent.
 *
 * @doc.type record
 * @doc.purpose Release governance agent that generates Software Bill of Materials output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SbomGeneratorOutput(@NotNull String sbomId, @NotNull String sbomContent, @NotNull String format, int componentCount, @NotNull Map<String, Object> metadata) {
  public SbomGeneratorOutput {
    if (sbomId == null || sbomId.isEmpty()) {
      throw new IllegalArgumentException("sbomId cannot be null or empty");
    }
    if (sbomContent == null || sbomContent.isEmpty()) {
      throw new IllegalArgumentException("sbomContent cannot be null or empty");
    }
    if (format == null || format.isEmpty()) {
      throw new IllegalArgumentException("format cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
