package com.ghatana.digitalmarketing.domain.validation;

import java.util.Objects;

/**
 * A single finding from content validation — severity, rule violated, affected block,
 * human-readable reason, and the action required to resolve.
 *
 * @doc.type class
 * @doc.purpose DMOS content validation finding value object for compliance enforcement
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ContentValidationFinding(
        ValidationSeverity severity,
        String ruleCode,
        String affectedBlockId,
        String reason,
        String requiredAction,
        String approverRole) {

    /**
     * Compact constructor — validates required fields.
     */
    public ContentValidationFinding {
        Objects.requireNonNull(severity,       "severity must not be null");
        Objects.requireNonNull(ruleCode,       "ruleCode must not be null");
        Objects.requireNonNull(reason,         "reason must not be null");
        Objects.requireNonNull(requiredAction, "requiredAction must not be null");
        if (ruleCode.isBlank())       throw new IllegalArgumentException("ruleCode must not be blank");
        if (reason.isBlank())         throw new IllegalArgumentException("reason must not be blank");
        if (requiredAction.isBlank()) throw new IllegalArgumentException("requiredAction must not be blank");
        // affectedBlockId and approverRole are nullable / optional
    }
}
