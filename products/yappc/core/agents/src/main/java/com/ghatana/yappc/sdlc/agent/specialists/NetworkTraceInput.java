package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for NetworkTrace agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes network traces for latency and connectivity issues input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record NetworkTraceInput(@NotNull String traceId, @NotNull String protocol, @NotNull Map<String, Object> traceData) {
  public NetworkTraceInput {
    if (traceId == null || traceId.isEmpty()) {
      throw new IllegalArgumentException("traceId cannot be null or empty");
    }
    if (protocol == null || protocol.isEmpty()) {
      throw new IllegalArgumentException("protocol cannot be null or empty");
    }
    if (traceData == null) {
      traceData = Map.of();
    }
  }
}
