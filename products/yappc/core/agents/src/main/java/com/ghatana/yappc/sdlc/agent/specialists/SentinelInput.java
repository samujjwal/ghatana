package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for Sentinel agent.
 *
 * @doc.type record
 * @doc.purpose Expert security sentinel monitoring threats and enforcing security posture input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SentinelInput(@NotNull String scanTargetId, @NotNull String scanType, @NotNull Map<String, Object> securityContext) {
  public SentinelInput {
    if (scanTargetId == null || scanTargetId.isEmpty()) {
      throw new IllegalArgumentException("scanTargetId cannot be null or empty");
    }
    if (scanType == null || scanType.isEmpty()) {
      throw new IllegalArgumentException("scanType cannot be null or empty");
    }
    if (securityContext == null) {
      securityContext = Map.of();
    }
  }
}
