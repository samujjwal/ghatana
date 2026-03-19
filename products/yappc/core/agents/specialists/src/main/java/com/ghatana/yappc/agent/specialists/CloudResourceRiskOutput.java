package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from CloudResourceRisk agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that assesses risk posture of cloud resources output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudResourceRiskOutput(@NotNull String riskId, @NotNull String riskLevel, @NotNull List<String> findings, @NotNull Map<String, Object> metadata) {
  public CloudResourceRiskOutput {
    if (riskId == null || riskId.isEmpty()) {
      throw new IllegalArgumentException("riskId cannot be null or empty");
    }
    if (riskLevel == null || riskLevel.isEmpty()) {
      throw new IllegalArgumentException("riskLevel cannot be null or empty");
    }
    if (findings == null) {
      findings = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
