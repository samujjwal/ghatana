package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @doc.type    Service
 * @doc.purpose Evaluates CEL (Common Expression Language) expressions used in:
 *              DECISION step conditions, EVENT trigger filter expressions, and loop conditions.
 *              Provides type-safe, sandbox-safe evaluation with type checking at definition time
 *              (fail-fast).  Offers built-in functions: strings, dates, math, collections.
 *              Custom functions can be registered per service.
 *              Target: under 1ms for 99th-percentile evaluation.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class CelExpressionEvaluatorService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface CelEnginePort {
        /**
         * Evaluate a CEL expression against a JSON context.
         * @throws CelEvaluationException if the expression is syntactically invalid or type-check fails.
         */
        boolean evaluate(String celExpression, String contextJson);

        /**
         * Type-check a CEL expression against a declared type schema.
         * This is called at definition time, before the expression is stored.
         * @throws CelEvaluationException if types don't match.
         */
        void typeCheck(String celExpression, String typeSchemaJson);

        class CelEvaluationException extends RuntimeException {
            public CelEvaluationException(String msg) { super(msg); }
            public CelEvaluationException(String msg, Throwable cause) { super(msg, cause); }
        }
    }

    public interface CustomFunctionRegistryPort {
        /** Register a named custom function available in CEL expressions. */
        void register(String functionName, CustomFunction function);

        @FunctionalInterface
        interface CustomFunction {
            Object invoke(List<Object> args);
        }
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    public record TypeCheckResult(boolean valid, List<String> errors) {}

    public record EvaluationResult(boolean result, long evalNanos, boolean cached) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final CelEnginePort celEngine;
    private final CustomFunctionRegistryPort functionRegistry;
    private final Timer evalTimer;
    private final Counter evalTotal;
    private final Counter typeCheckFailureTotal;
    private final Counter evalFastPathTotal;  // sub-1ms eval

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public CelExpressionEvaluatorService(MeterRegistry meterRegistry,
                                          CelEnginePort celEngine,
                                          CustomFunctionRegistryPort functionRegistry) {
        this.celEngine        = celEngine;
        this.functionRegistry = functionRegistry;

        this.evalTimer            = Timer.builder("workflow.cel.eval_duration_ns")
                .description("CEL expression evaluation duration in nanoseconds")
                .register(meterRegistry);
        this.evalTotal            = Counter.builder("workflow.cel.eval_total")
                .description("Total CEL evaluations")
                .register(meterRegistry);
        this.typeCheckFailureTotal = Counter.builder("workflow.cel.type_check_failure_total")
                .description("CEL expressions rejected at type-check time")
                .register(meterRegistry);
        this.evalFastPathTotal    = Counter.builder("workflow.cel.eval_sub_1ms_total")
                .description("CEL evaluations completing in under 1ms")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Type-check a CEL expression at definition time.
     * Should be called when storing a workflow definition; failure means reject the definition.
     */
    public TypeCheckResult typeCheck(String celExpression, String typeSchemaJson) {
        try {
            celEngine.typeCheck(celExpression, typeSchemaJson);
            return new TypeCheckResult(true, List.of());
        } catch (CelEnginePort.CelEvaluationException e) {
            typeCheckFailureTotal.increment();
            return new TypeCheckResult(false, List.of(e.getMessage()));
        }
    }

    /**
     * Evaluate a DECISION step condition.
     * The contextJson is the current workflow instance context payload.
     */
    public EvaluationResult evaluateCondition(String celExpression, String contextJson) {
        return evaluate(celExpression, contextJson);
    }

    /**
     * Evaluate an EVENT trigger filter expression.
     * Returns true if the event should trigger the workflow.
     */
    public boolean evaluateEventFilter(String celExpression, String eventPayloadJson) {
        if (celExpression == null || celExpression.isBlank()) return true;
        return evaluate(celExpression, eventPayloadJson).result();
    }

    /**
     * Evaluate a loop/wait condition in the workflow execution context.
     */
    public EvaluationResult evaluateLoopCondition(String celExpression, String contextJson) {
        return evaluate(celExpression, contextJson);
    }

    /**
     * Register a custom domain function into the CEL engine so it can be used in expressions.
     * Example: register("isBusinessDay", args -> calendarService.isBusinessDay((String) args.get(0)))
     */
    public void registerCustomFunction(String functionName, CustomFunctionRegistryPort.CustomFunction fn) {
        functionRegistry.register(functionName, fn);
    }

    // -----------------------------------------------------------------------
    // Built-in helper expressions (convenience constants for common patterns)
    // -----------------------------------------------------------------------

    /** CEL snippet: check if the amount exceeds a threshold from context. */
    public static final String SNIPPET_AMOUNT_EXCEEDS_THRESHOLD =
        "context.amount > context.threshold";

    /** CEL snippet: check if a client is marked as PEP. */
    public static final String SNIPPET_IS_PEP =
        "context.clientProfile.pepStatus == 'PEP'";

    /** CEL snippet: check if a document set is complete (all required docs uploaded). */
    public static final String SNIPPET_DOCS_COMPLETE =
        "context.requiredDocs.all(doc, context.uploadedDocs.exists(u, u.docType == doc))";

    /** CEL snippet: check if a risk score warrants EDD. */
    public static final String SNIPPET_EDD_REQUIRED =
        "context.riskScore >= 70";

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private EvaluationResult evaluate(String celExpression, String contextJson) {
        long start = System.nanoTime();
        try {
            boolean result = celEngine.evaluate(celExpression, contextJson);
            long elapsed = System.nanoTime() - start;
            evalTimer.record(elapsed, TimeUnit.NANOSECONDS);
            evalTotal.increment();
            if (elapsed < 1_000_000L) evalFastPathTotal.increment();  // under 1ms
            return new EvaluationResult(result, elapsed, false);
        } catch (CelEnginePort.CelEvaluationException e) {
            throw new RuntimeException("CEL evaluation failed for expression: " + celExpression, e);
        }
    }
}
