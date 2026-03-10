package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for ComplianceAuditOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates compliance audits against regulatory frameworks input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ComplianceAuditOrchestratorInput(@NotNull String frameworkId, @NotNull String auditScope, @NotNull Map<String, Object> controls) {
  public ComplianceAuditOrchestratorInput {
    if (frameworkId == null || frameworkId.isEmpty()) {
      throw new IllegalArgumentException("frameworkId cannot be null or empty");
    }
    if (auditScope == null || auditScope.isEmpty()) {
      throw new IllegalArgumentException("auditScope cannot be null or empty");
    }
    if (controls == null) {
      controls = Map.of();
    }
  }
}
