package com.ghatana.digitalmarketing.domain.sow;

import java.time.LocalDate;
import java.util.Objects;

/**
 * An immutable clause from the DMOS SOW/MSA clause library.
 *
 * <p>Each clause belongs to a named type (e.g. {@code "SCOPE"}, {@code "PAYMENT"},
 * {@code "TERMINATION"}, {@code "DISCLAIMER"}), carries a version string, and has
 * a lifecycle status of either {@link SowClauseStatus#DRAFT} or
 * {@link SowClauseStatus#APPROVED}.</p>
 *
 * <p>Only {@code APPROVED} clauses are eligible for inclusion in a SOW draft without
 * triggering a {@link SowRiskType#MISSING_APPROVAL} risk flag.</p>
 *
 * @param clauseId      unique identifier for this clause entry
 * @param clauseType    short type code (e.g. {@code "SCOPE"}, {@code "PAYMENT"})
 * @param version       immutable version string (e.g. {@code "v1.0"})
 * @param content       full clause text
 * @param owner         the team or individual responsible for this clause
 * @param reviewer      the team or individual who last reviewed this clause
 * @param effectiveDate date from which this clause version is effective
 * @param status        current approval lifecycle status
 *
 * @doc.type class
 * @doc.purpose SOW clause library entry value object
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SowClause(
        String clauseId,
        String clauseType,
        String version,
        String content,
        String owner,
        String reviewer,
        LocalDate effectiveDate,
        SowClauseStatus status) {

    public SowClause {
        Objects.requireNonNull(clauseId, "clauseId must not be null");
        Objects.requireNonNull(clauseType, "clauseType must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(reviewer, "reviewer must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (clauseId.isBlank()) {
            throw new IllegalArgumentException("clauseId must not be blank");
        }
        if (clauseType.isBlank()) {
            throw new IllegalArgumentException("clauseType must not be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer must not be blank");
        }
    }

    /** Returns {@code true} if this clause has been formally approved. */
    public boolean isApproved() {
        return status == SowClauseStatus.APPROVED;
    }
}
