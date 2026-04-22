/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowDefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CelWorkflowExpressionEvaluator Tests [GH-90000]")
class CelWorkflowExpressionEvaluatorTest {

    /** Simple mock CEL engine that handles basic patterns for testing */
    private final CelWorkflowExpressionEvaluator.CelEnginePort mockEngine =
        new CelWorkflowExpressionEvaluator.CelEnginePort() { // GH-90000
            @Override
            public Object evaluate(String expression, Map<String, Object> context) { // GH-90000
                if (expression.equals("true [GH-90000]")) return true;
                if (expression.equals("false [GH-90000]")) return false;
                if (expression.equals("ctx.amount > 100 [GH-90000]")) {
                    Object amount = context.get("amount [GH-90000]");
                    if (amount instanceof Number n) return n.doubleValue() > 100; // GH-90000
                    return false;
                }
                throw new IllegalArgumentException("Unknown expression: " + expression); // GH-90000
            }

            @Override
            public void validate(String expression) { // GH-90000
                if (expression.equals("invalid!!! [GH-90000]")) {
                    throw new IllegalArgumentException("Syntax error [GH-90000]");
                }
            }
        };

    private final CelWorkflowExpressionEvaluator evaluator =
        new CelWorkflowExpressionEvaluator(mockEngine); // GH-90000

    @Test
    void shouldEvaluateToTrue() { // GH-90000
        WorkflowContext ctx = WorkflowContext.forWorkflow("w", "t"); // GH-90000
        boolean result = evaluator.evaluateBoolean("true", ctx); // GH-90000
        assertThat(result).isTrue(); // GH-90000
    }

    @Test
    void shouldEvaluateToFalse() { // GH-90000
        WorkflowContext ctx = WorkflowContext.forWorkflow("w", "t"); // GH-90000
        boolean result = evaluator.evaluateBoolean("false", ctx); // GH-90000
        assertThat(result).isFalse(); // GH-90000
    }

    @Test
    void shouldEvaluateContextExpression() { // GH-90000
        WorkflowContext ctx = new MapWorkflowContext("w", "t", "c", Map.of("amount", 200)); // GH-90000
        boolean result = evaluator.evaluateBoolean("ctx.amount > 100", ctx); // GH-90000
        assertThat(result).isTrue(); // GH-90000
    }

    @Test
    void shouldEvaluateContextExpressionFalse() { // GH-90000
        WorkflowContext ctx = new MapWorkflowContext("w", "t", "c", Map.of("amount", 50)); // GH-90000
        boolean result = evaluator.evaluateBoolean("ctx.amount > 100", ctx); // GH-90000
        assertThat(result).isFalse(); // GH-90000
    }

    @Test
    void shouldValidateGoodExpression() { // GH-90000
        assertThatNoException().isThrownBy(() -> evaluator.validate("true [GH-90000]"));
    }

    @Test
    void shouldRejectInvalidExpression() { // GH-90000
        assertThatThrownBy(() -> evaluator.validate("invalid!!! [GH-90000]"))
            .isInstanceOf(WorkflowDefinitionException.class); // GH-90000
    }

    @Test
    void shouldThrowOnNonBooleanResult() { // GH-90000
        CelWorkflowExpressionEvaluator.CelEnginePort stringEngine =
            new CelWorkflowExpressionEvaluator.CelEnginePort() { // GH-90000
                @Override
                public Object evaluate(String expression, Map<String, Object> context) { // GH-90000
                    return "not a boolean";
                }
                @Override
                public void validate(String expression) {} // GH-90000
            };

        CelWorkflowExpressionEvaluator eval = new CelWorkflowExpressionEvaluator(stringEngine); // GH-90000
        WorkflowContext ctx = WorkflowContext.forWorkflow("w", "t"); // GH-90000
        assertThatThrownBy(() -> eval.evaluateBoolean("expr", ctx)) // GH-90000
            .isInstanceOf(WorkflowDefinitionException.class) // GH-90000
            .hasMessageContaining("did not return a boolean [GH-90000]");
    }

    @Test
    void shouldRejectNullEngine() { // GH-90000
        assertThatThrownBy(() -> new CelWorkflowExpressionEvaluator(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
