/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.spec;

/**
 * A single structured validation issue produced by {@link AgentSpecValidator}.
 *
 * <p>Each issue names the field that failed validation, the value that was
 * supplied (may be {@code null} for missing required fields), and a
 * human-readable message explaining what is wrong.
 *
 * @param field   the dotted-path field name from the spec (e.g., {@code "identity.agentType"})
 * @param value   the value that failed validation, or {@code null} if the field was absent
 * @param message human-readable explanation of the validation failure
 *
 * @doc.type record
 * @doc.purpose Structured validation issue for a single field in an AgentSpec
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ValidationIssue(String field, String value, String message) {

    /**
     * Creates a {@code ValidationIssue} for a present but invalid value.
     *
     * @param field   the field path
     * @param value   the invalid value
     * @param message the explanation
     */
    public ValidationIssue {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("ValidationIssue.field must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("ValidationIssue.message must not be blank");
        }
    }

    @Override
    public String toString() {
        return "[" + field + "=" + (value != null ? "'" + value + "'" : "<absent>") + "] " + message;
    }
}
