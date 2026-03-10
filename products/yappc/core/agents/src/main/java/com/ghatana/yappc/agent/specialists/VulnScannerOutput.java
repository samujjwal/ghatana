package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from VulnScanner agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that scans code and dependencies for vulnerabilities output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record VulnScannerOutput(@NotNull String scanId, @NotNull List<String> vulnerabilities, @NotNull String riskScore, @NotNull Map<String, Object> metadata) {
  public VulnScannerOutput {
    if (scanId == null || scanId.isEmpty()) {
      throw new IllegalArgumentException("scanId cannot be null or empty");
    }
    if (vulnerabilities == null) {
      vulnerabilities = List.of();
    }
    if (riskScore == null || riskScore.isEmpty()) {
      throw new IllegalArgumentException("riskScore cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
