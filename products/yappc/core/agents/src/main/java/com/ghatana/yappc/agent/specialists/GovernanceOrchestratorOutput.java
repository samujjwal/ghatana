package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from GovernanceOrchestrator agent.
 *
 * @doc.type record
 * @doc.purpose Governance orchestration decision output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GovernanceOrchestratorOutput(
    @NotNull String decisionId,
    @NotNull String verdict,
    @NotNull List<String> violations,
    @NotNull List<String> approvals,
    @NotNull String auditTrailId,
    @NotNull Map<String, Object> metadata) {

  /** Governance verdict constants. */
  public static final String VERDICT_APPROVED = "APPROVED";
  public static final String VERDICT_REJECTED = "REJECTED";
  public static final String VERDICT_PENDING_REVIEW = "PENDING_REVIEW";

  public GovernanceOrchestratorOutput {
    if (decisionId == null || decisionId.isEmpty()) {
      throw new IllegalArgumentException("decisionId cannot be null or empty");
    }
    if (verdict == null || verdict.isEmpty()) {
      throw new IllegalArgumentException("verdict cannot be null or empty");
    }
    if (violations == null) {
      violations = List.of();
    }
    if (approvals == null) {
      approvals = List.of();
    }
    if (auditTrailId == null || auditTrailId.isEmpty()) {
      auditTrailId = "audit-" + decisionId;
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
