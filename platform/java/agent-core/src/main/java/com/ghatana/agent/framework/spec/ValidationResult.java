/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.spec;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated result of {@link AgentSpecValidator#validate(AgentSpec)}.
 *
 * <p>Contains an ordered list of {@link ValidationIssue}s. An empty list means the
 * spec is valid. Call {@link #throwIfInvalid()} to throw an
 * {@link AgentSpecValidationException} with all issues listed if any exist.
 *
 * @doc.type class
 * @doc.purpose Aggregated validation result for an AgentSpec
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class ValidationResult {

    private final List<ValidationIssue> issues;

    ValidationResult(List<ValidationIssue> issues) {
        this.issues = Collections.unmodifiableList(issues);
    }

    /**
     * Returns {@code true} if there are no validation issues.
     *
     * @return {@code true} if the spec is valid
     */
    public boolean isValid() {
        return issues.isEmpty();
    }

    /**
     * Returns the list of validation issues (never {@code null}, may be empty).
     *
     * @return unmodifiable list of issues
     */
    public List<ValidationIssue> getIssues() {
        return issues;
    }

    /**
     * Throws {@link AgentSpecValidationException} if this result is not valid.
     *
     * @throws AgentSpecValidationException when {@link #isValid()} returns {@code false}
     */
    public void throwIfInvalid() {
        if (!isValid()) {
            throw new AgentSpecValidationException(issues);
        }
    }

    @Override
    public String toString() {
        if (isValid()) {
            return "ValidationResult[VALID]";
        }
        return "ValidationResult[INVALID, issues=[\n  "
                + issues.stream().map(ValidationIssue::toString)
                        .collect(Collectors.joining(",\n  "))
                + "\n]]";
    }
}
