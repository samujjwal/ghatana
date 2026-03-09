package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Input for CloudSecurityAuditOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Orchestrates cloud security audits across providers and services input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloudSecurityAuditOrchestratorInput(@NotNull String cloudAccountId, @NotNull String provider, @NotNull List<String> auditScope) {
  public CloudSecurityAuditOrchestratorInput {
    if (cloudAccountId == null || cloudAccountId.isEmpty()) {
      throw new IllegalArgumentException("cloudAccountId cannot be null or empty");
    }
    if (provider == null || provider.isEmpty()) {
      throw new IllegalArgumentException("provider cannot be null or empty");
    }
    if (auditScope == null) {
      auditScope = List.of();
    }
  }
}
