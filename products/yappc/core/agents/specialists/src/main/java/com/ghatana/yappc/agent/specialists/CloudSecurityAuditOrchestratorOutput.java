package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from CloudSecurityAuditOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates cloud security audits across providers and services output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudSecurityAuditOrchestratorOutput(@NotNull String auditId, @NotNull List<String> findings, @NotNull String complianceScore, @NotNull Map<String, Object> metadata) {
  public CloudSecurityAuditOrchestratorOutput {
    if (auditId == null || auditId.isEmpty()) {
      throw new IllegalArgumentException("auditId cannot be null or empty");
    }
    if (findings == null) {
      findings = List.of();
    }
    if (complianceScore == null || complianceScore.isEmpty()) {
      throw new IllegalArgumentException("complianceScore cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
