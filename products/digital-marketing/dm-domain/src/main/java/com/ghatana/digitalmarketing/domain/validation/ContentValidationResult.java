package com.ghatana.digitalmarketing.domain.validation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate result of a content validation run against a single content version.
 *
 * <p>A result contains all findings (INFO, WARN, FAIL). The overall outcome is
 * {@code PASS} when there are no FAIL findings, {@code WARN} when there is at least
 * one WARN finding but no FAIL findings, and {@code FAIL} when at least one FAIL
 * finding is present.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS content validation result domain object for approval workflow
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ContentValidationResult(
        String versionId,
        ValidationOutcome outcome,
        List<ContentValidationFinding> findings,
        Instant validatedAt,
        String validatedBy) {

    /**
     * Overall pass/warn/fail classification.
     */
    public enum ValidationOutcome {
        PASS, WARN, FAIL
    }

    /**
     * Compact constructor — validates required fields.
     */
    public ContentValidationResult {
        Objects.requireNonNull(versionId,   "versionId must not be null");
        Objects.requireNonNull(outcome,     "outcome must not be null");
        Objects.requireNonNull(findings,    "findings must not be null");
        Objects.requireNonNull(validatedAt, "validatedAt must not be null");
        Objects.requireNonNull(validatedBy, "validatedBy must not be null");
        if (versionId.isBlank())   throw new IllegalArgumentException("versionId must not be blank");
        if (validatedBy.isBlank()) throw new IllegalArgumentException("validatedBy must not be blank");
        findings = List.copyOf(findings);
    }

    /** Returns {@code true} when the version is approved for content launch (no FAIL findings). */
    public boolean isPassed() {
        return outcome != ValidationOutcome.FAIL;
    }

    /** Returns {@code true} when at least one finding has severity FAIL. */
    public boolean hasFails() {
        return findings.stream().anyMatch(f -> f.severity() == ValidationSeverity.FAIL);
    }

    /** Returns {@code true} when at least one finding has severity WARN or FAIL. */
    public boolean hasWarnings() {
        return findings.stream().anyMatch(
            f -> f.severity() == ValidationSeverity.WARN || f.severity() == ValidationSeverity.FAIL);
    }
}
