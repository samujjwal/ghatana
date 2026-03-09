package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DocumentationWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates documentation from code and specifications input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DocumentationWriterInput(@NotNull String sourceId, @NotNull String docType, @NotNull Map<String, Object> context) {
  public DocumentationWriterInput {
    if (sourceId == null || sourceId.isEmpty()) {
      throw new IllegalArgumentException("sourceId cannot be null or empty");
    }
    if (docType == null || docType.isEmpty()) {
      throw new IllegalArgumentException("docType cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
