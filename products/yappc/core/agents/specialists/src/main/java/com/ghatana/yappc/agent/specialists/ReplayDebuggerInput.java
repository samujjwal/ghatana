package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ReplayDebugger agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that replays request flows for debugging input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReplayDebuggerInput(@NotNull String requestId, @NotNull String targetService, @NotNull Map<String, Object> replayConfig) {
  public ReplayDebuggerInput {
    if (requestId == null || requestId.isEmpty()) {
      throw new IllegalArgumentException("requestId cannot be null or empty");
    }
    if (targetService == null || targetService.isEmpty()) {
      throw new IllegalArgumentException("targetService cannot be null or empty");
    }
    if (replayConfig == null) {
      replayConfig = Map.of();
    }
  }
}
