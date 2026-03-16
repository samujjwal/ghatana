package com.ghatana.appplatform.compliance.domain;

import java.time.Instant;
import java.util.List;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Result of a compliance check pipeline run (D07-001).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record ComplianceCheckResult(
        String checkId,
        String orderId,
        ComplianceStatus status,
        List<RuleEvaluationDetail> rulesEvaluated,
        List<String> reasons,           // human-readable failure/review reasons
        long evaluationDurationMs,
        String jurisdiction,
        Instant evaluatedAt
) {
    /**
     * Individual rule evaluation record.
     */
    public record RuleEvaluationDetail(
            String ruleId,
            String ruleName,
            ComplianceStatus result,
            String reason
    ) {}
}
