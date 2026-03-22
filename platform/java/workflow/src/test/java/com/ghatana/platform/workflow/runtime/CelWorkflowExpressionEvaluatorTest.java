/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowDefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CelWorkflowExpressionEvaluator Tests")
class CelWorkflowExpressionEvaluatorTest {

    /** Simple mock CEL engine that handles basic patterns for testing */
    private final CelWorkflowExpressionEvaluator.CelEnginePort mockEngine =
        new CelWorkflowExpressionEvaluator.CelEnginePort() {
            @Override
            public Object evaluate(String expression, Map<String, Object> context) {
                if (expression.equals("true")) return true;
                if (expression.equals("false")) return false;
                if (expression.equals("ctx.amount > 100")) {
                    Object amount = context.get("amount");
                    if (amount instanceof Number n) return n.doubleValue() > 100;
                    return false;
                }
                throw new IllegalArgumentException("Unknown expression: " + expression);
            }

            @Override
            public void validate(String expression) {
                if (expression.equals("invalid!!!")) {
                    throw new IllegalArgumentException("Syntax error");
                }
            }
        };

    private final CelWorkflowExpressionEvaluator evaluator =
        new CelWorkflowExpressionEvaluator(mockEngine);

    @Test
    void shouldEvaluateToTrue() {
        WorkflowContext ctx = WorkflowContext.forWorkflow("w", "t");
        boolean result = evaluator.evaluateBoolean("true", ctx);
        assertThat(result).isTrue();
    }

    @Test
    void shouldEvaluateToFalse() {
        WorkflowContext ctx = WorkflowContext.forWorkflow("w", "t");
        boolean result = evaluator.evaluateBoolean("false", ctx);
        assertThat(result).isFalse();
    }

    @Test
    void shouldEvaluateContextExpression() {
        WorkflowContext ctx = new MapWorkflowContext("w", "t", "c", Map.of("amount", 200));
        boolean result = evaluator.evaluateBoolean("ctx.amount > 100", ctx);
        assertThat(result).isTrue();
    }

    @Test
    void shouldEvaluateContextExpressionFalse() {
        WorkflowContext ctx = new MapWorkflowContext("w", "t", "c", Map.of("amount", 50));
        boolean result = evaluator.evaluateBoolean("ctx.amount > 100", ctx);
        assertThat(result).isFalse();
    }

    @Test
    void shouldValidateGoodExpression() {
        assertThatNoException().isThrownBy(() -> evaluator.validate("true"));
    }

    @Test
    void shouldRejectInvalidExpression() {
        assertThatThrownBy(() -> evaluator.validate("invalid!!!"))
            .isInstanceOf(WorkflowDefinitionException.class);
    }

    @Test
    void shouldThrowOnNonBooleanResult() {
        CelWorkflowExpressionEvaluator.CelEnginePort stringEngine =
            new CelWorkflowExpressionEvaluator.CelEnginePort() {
                @Override
                public Object evaluate(String expression, Map<String, Object> context) {
                    return "not a boolean";
                }
                @Override
                public void validate(String expression) {}
            };

        CelWorkflowExpressionEvaluator eval = new CelWorkflowExpressionEvaluator(stringEngine);
        WorkflowContext ctx = WorkflowContext.forWorkflow("w", "t");
        assertThatThrownBy(() -> eval.evaluateBoolean("expr", ctx))
            .isInstanceOf(WorkflowDefinitionException.class)
            .hasMessageContaining("did not return a boolean");
    }

    @Test
    void shouldRejectNullEngine() {
        assertThatThrownBy(() -> new CelWorkflowExpressionEvaluator(null))
            .isInstanceOf(NullPointerException.class);
    }
}
