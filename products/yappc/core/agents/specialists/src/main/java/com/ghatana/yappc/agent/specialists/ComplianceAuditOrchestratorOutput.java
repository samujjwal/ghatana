package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from ComplianceAuditOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates compliance audits against regulatory frameworks output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ComplianceAuditOrchestratorOutput(@NotNull String auditId, @NotNull List<String> findings, @NotNull String complianceStatus, @NotNull Map<String, Object> metadata) {
  public ComplianceAuditOrchestratorOutput {
    if (auditId == null || auditId.isEmpty()) {
      throw new IllegalArgumentException("auditId cannot be null or empty");
    }
    if (findings == null) {
      findings = List.of();
    }
    if (complianceStatus == null || complianceStatus.isEmpty()) {
      throw new IllegalArgumentException("complianceStatus cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
