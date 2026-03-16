/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;

/**
 * SPI for evaluating conditional expressions in DECISION workflow steps.
 *
 * <p>Implementations provide language-specific expression evaluation (e.g. CEL,
 * SpEL, JEXL). The platform ships a CEL implementation in {@code workflow-runtime}.
 * Products may provide domain-specific evaluators with custom functions.
 *
 * @doc.type interface
 * @doc.purpose SPI for workflow conditional expression evaluation
 * @doc.layer core
 * @doc.pattern Strategy
 */
public interface WorkflowExpressionEvaluator {

    /**
     * Evaluates an expression and returns the result as an object.
     *
     * @param expression the expression string
     * @param context    the workflow context providing variables for evaluation
     * @return the evaluation result
     * @throws WorkflowDefinitionException if the expression is invalid
     */
    Object evaluate(@NotNull String expression, @NotNull WorkflowContext context);

    /**
     * Evaluates an expression and returns the result as a boolean.
     *
     * @param expression the expression string (must evaluate to a boolean)
     * @param context    the workflow context providing variables for evaluation
     * @return the boolean result
     * @throws WorkflowDefinitionException if the expression is invalid or not boolean
     */
    boolean evaluateBoolean(@NotNull String expression, @NotNull WorkflowContext context);

    /**
     * Validates an expression for syntactic and type correctness without executing it.
     *
     * @param expression the expression string to validate
     * @throws WorkflowDefinitionException if the expression is invalid
     */
    void validate(@NotNull String expression);
}
