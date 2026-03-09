package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ReactExpertWorker agent.
 *
 * @doc.type record
 * @doc.purpose Stack expert worker agent for React component patterns and hooks output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReactExpertWorkerOutput(@NotNull String adviceId, @NotNull String recommendation, @NotNull List<String> codeExamples, @NotNull Map<String, Object> metadata) {
  public ReactExpertWorkerOutput {
    if (adviceId == null || adviceId.isEmpty()) {
      throw new IllegalArgumentException("adviceId cannot be null or empty");
    }
    if (recommendation == null || recommendation.isEmpty()) {
      throw new IllegalArgumentException("recommendation cannot be null or empty");
    }
    if (codeExamples == null) {
      codeExamples = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
