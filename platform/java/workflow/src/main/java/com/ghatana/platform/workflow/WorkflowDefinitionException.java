/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

/**
 * Thrown when a workflow definition fails validation.
 *
 * <p>Examples: invalid step references, circular dependencies, malformed expressions,
 * missing required fields in a {@code WorkflowDefinition}.
 *
 * @doc.type class
 * @doc.purpose Exception for invalid workflow definitions
 * @doc.layer core
 * @doc.pattern Exception
 */
public class WorkflowDefinitionException extends RuntimeException {

    public WorkflowDefinitionException(String message) {
        super(message);
    }

    public WorkflowDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
