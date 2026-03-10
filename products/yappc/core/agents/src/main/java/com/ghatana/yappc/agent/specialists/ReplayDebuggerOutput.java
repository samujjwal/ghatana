package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ReplayDebugger agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that replays request flows for debugging output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReplayDebuggerOutput(@NotNull String replayId, @NotNull List<String> divergences, @NotNull Map<String, Object> timeline, @NotNull Map<String, Object> metadata) {
  public ReplayDebuggerOutput {
    if (replayId == null || replayId.isEmpty()) {
      throw new IllegalArgumentException("replayId cannot be null or empty");
    }
    if (divergences == null) {
      divergences = List.of();
    }
    if (timeline == null) {
      timeline = Map.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
