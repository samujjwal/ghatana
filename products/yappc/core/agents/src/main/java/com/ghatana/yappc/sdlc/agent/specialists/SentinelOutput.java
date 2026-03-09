package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from Sentinel agent.
 *
 * @doc.type record
 * @doc.purpose Expert security sentinel monitoring threats and enforcing security posture output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SentinelOutput(@NotNull String scanId, @NotNull List<String> findings, @NotNull String riskLevel, @NotNull Map<String, Object> metadata) {
  public SentinelOutput {
    if (scanId == null || scanId.isEmpty()) {
      throw new IllegalArgumentException("scanId cannot be null or empty");
    }
    if (findings == null) {
      findings = List.of();
    }
    if (riskLevel == null || riskLevel.isEmpty()) {
      throw new IllegalArgumentException("riskLevel cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
