package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from JavaClassWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates Java classes from specifications output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record JavaClassWriterOutput(@NotNull String generatedCode, @NotNull String filePath, @NotNull List<String> imports, @NotNull Map<String, Object> metadata) {
  public JavaClassWriterOutput {
    if (generatedCode == null || generatedCode.isEmpty()) {
      throw new IllegalArgumentException("generatedCode cannot be null or empty");
    }
    if (filePath == null || filePath.isEmpty()) {
      throw new IllegalArgumentException("filePath cannot be null or empty");
    }
    if (imports == null) {
      imports = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
