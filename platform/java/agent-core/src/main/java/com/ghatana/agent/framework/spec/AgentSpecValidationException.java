/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.spec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown by {@link ValidationResult#throwIfInvalid()} when an {@link AgentSpec}
 * fails validation.
 *
 * <p>The exception message contains all validation issues in a human-readable format
 * so that CI failures and logs are actionable without opening a debugger.
 *
 * @doc.type class
 * @doc.purpose Runtime exception carrying all AgentSpec validation issues
 * @doc.layer platform
 * @doc.pattern Exception
 */
public final class AgentSpecValidationException extends RuntimeException {

    private final List<ValidationIssue> issues;

    /**
     * Creates an exception for the given list of validation issues.
     *
     * @param issues the non-empty list of issues
     */
    public AgentSpecValidationException(List<ValidationIssue> issues) {
        super("AgentSpec validation failed with " + issues.size() + " issue(s):\n"
                + issues.stream()
                        .map(i -> "  - " + i)
                        .collect(Collectors.joining("\n")));
        this.issues = List.copyOf(issues);
    }

    /**
     * Returns the list of validation issues that caused this exception.
     *
     * @return unmodifiable list of issues
     */
    public List<ValidationIssue> getIssues() {
        return issues;
    }
}
