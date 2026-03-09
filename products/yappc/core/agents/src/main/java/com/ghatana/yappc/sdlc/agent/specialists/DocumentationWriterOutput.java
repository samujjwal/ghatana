package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DocumentationWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates documentation from code and specifications output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DocumentationWriterOutput(@NotNull String docId, @NotNull String content, @NotNull String format, @NotNull Map<String, Object> metadata) {
  public DocumentationWriterOutput {
    if (docId == null || docId.isEmpty()) {
      throw new IllegalArgumentException("docId cannot be null or empty");
    }
    if (content == null || content.isEmpty()) {
      throw new IllegalArgumentException("content cannot be null or empty");
    }
    if (format == null || format.isEmpty()) {
      throw new IllegalArgumentException("format cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
