package com.ghatana.digitalmarketing.domain.audit;

import java.util.Objects;

/**
 * Single evidence-backed website audit finding.
 *
 * @doc.type class
 * @doc.purpose DMOS website audit finding with rationale and recommendation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record WebsiteAuditFinding(
    AuditSeverity severity,
    String category,
    String evidence,
    String rationale,
    String recommendedAction,
    String sourceUrl
) {
    public WebsiteAuditFinding {
        Objects.requireNonNull(severity, "severity must not be null");
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("evidence must not be blank");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        if (recommendedAction == null || recommendedAction.isBlank()) {
            throw new IllegalArgumentException("recommendedAction must not be blank");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
    }
}
