package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for VulnScanner agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that scans code and dependencies for vulnerabilities input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record VulnScannerInput(@NotNull String scanTarget, @NotNull String scanType, @NotNull Map<String, Object> config) {
  public VulnScannerInput {
    if (scanTarget == null || scanTarget.isEmpty()) {
      throw new IllegalArgumentException("scanTarget cannot be null or empty");
    }
    if (scanType == null || scanType.isEmpty()) {
      throw new IllegalArgumentException("scanType cannot be null or empty");
    }
    if (config == null) {
      config = Map.of();
    }
  }
}
