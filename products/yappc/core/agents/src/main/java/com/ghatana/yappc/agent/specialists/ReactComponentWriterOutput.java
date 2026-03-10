package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ReactComponentWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates React/TypeScript components from specifications output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReactComponentWriterOutput(@NotNull String generatedCode, @NotNull String filePath, @NotNull List<String> dependencies, @NotNull Map<String, Object> metadata) {
  public ReactComponentWriterOutput {
    if (generatedCode == null || generatedCode.isEmpty()) {
      throw new IllegalArgumentException("generatedCode cannot be null or empty");
    }
    if (filePath == null || filePath.isEmpty()) {
      throw new IllegalArgumentException("filePath cannot be null or empty");
    }
    if (dependencies == null) {
      dependencies = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
