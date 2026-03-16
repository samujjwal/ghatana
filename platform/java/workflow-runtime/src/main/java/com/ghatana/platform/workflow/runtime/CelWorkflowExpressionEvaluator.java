/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowDefinitionException;
import com.ghatana.platform.workflow.WorkflowExpressionEvaluator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Adapter that bridges the platform-level {@link WorkflowExpressionEvaluator} SPI
 * to a pluggable CEL engine port.
 *
 * <p>The actual CEL parsing/compilation/evaluation is delegated to a
 * {@link CelEnginePort} implementation, allowing the runtime to remain
 * independent of any specific CEL library.
 *
 * @doc.type class
 * @doc.purpose CEL expression evaluation adapter for workflow conditions
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class CelWorkflowExpressionEvaluator implements WorkflowExpressionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(CelWorkflowExpressionEvaluator.class);

    private final CelEnginePort celEngine;

    public CelWorkflowExpressionEvaluator(@NotNull CelEnginePort celEngine) {
        this.celEngine = Objects.requireNonNull(celEngine, "celEngine");
    }

    @Override
    public Object evaluate(@NotNull String expression, @NotNull WorkflowContext context) {
        try {
            return celEngine.evaluate(expression, context.getVariables());
        } catch (Exception e) {
            log.error("CEL evaluation failed for expression '{}': {}", expression, e.getMessage());
            throw new WorkflowDefinitionException("Expression evaluation failed: " + expression, e);
        }
    }

    @Override
    public boolean evaluateBoolean(@NotNull String expression, @NotNull WorkflowContext context) {
        Object result = evaluate(expression, context);
        if (result instanceof Boolean b) {
            return b;
        }
        throw new WorkflowDefinitionException(
            "Expression '%s' did not return a boolean, got: %s".formatted(expression, result));
    }

    @Override
    public void validate(@NotNull String expression) {
        try {
            celEngine.validate(expression);
        } catch (Exception e) {
            log.debug("Expression validation failed for '{}': {}", expression, e.getMessage());
            throw new WorkflowDefinitionException("Invalid expression: " + expression, e);
        }
    }

    /**
     * Port for pluggable CEL engine implementations.
     *
     * @doc.type interface
     * @doc.purpose Abstraction over CEL engine library (e.g. Google CEL-Java)
     * @doc.layer platform
     * @doc.pattern Port
     */
    public interface CelEnginePort {

        /**
         * Evaluates an expression against the given context variables.
         *
         * @param expression CEL expression string
         * @param context    variables available to the expression
         * @return the evaluation result
         */
        Object evaluate(@NotNull String expression, @NotNull Map<String, Object> context);

        /**
         * Validates that an expression is syntactically correct.
         *
         * @param expression CEL expression to validate
         * @throws IllegalArgumentException if the expression is invalid
         */
        void validate(@NotNull String expression);
    }
}
