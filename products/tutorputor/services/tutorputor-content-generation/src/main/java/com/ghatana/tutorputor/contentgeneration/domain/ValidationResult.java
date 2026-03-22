package com.ghatana.tutorputor.contentgeneration.domain;

import java.util.List;
import java.util.Objects;

/**
 * Result of a content validation operation.
 *
 * @param passed whether validation passed
 * @param score confidence/quality score (0.0 to 1.0)
 * @param issues list of detected validation issues
 *
 * @doc.type record
 * @doc.purpose Content validation result value object
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ValidationResult(boolean passed, double score, List<String> issues) {
    public ValidationResult {
        Objects.requireNonNull(issues, "issues cannot be null");
    }
}
