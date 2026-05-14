/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy interface for executing a single {@link EvaluationTestCase} of a specific
 * {@link EvaluationType}.
 *
 * <p>Each executor is responsible for one or more evaluation types (declared via
 * {@link #supports(EvaluationType)}). The {@link DefaultEvaluationHarness} dispatches
 * test cases to the first matching executor in its registry. If no executor matches,
 * the test case is marked as skipped with an explanatory message.
 *
 * <p>Executors must be stateless and thread-safe.
 *
 * @doc.type interface
 * @doc.purpose Per-type evaluation executor strategy
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface EvaluationExecutor {

    /**
     * Returns {@code true} if this executor can handle the given evaluation type.
     *
     * @param type the evaluation type to check
     * @return true if this executor handles the type
     */
    boolean supports(@NotNull EvaluationType type);

    /**
     * Executes a single test case and returns its result.
     *
     * <p>Implementations must not throw unchecked exceptions for evaluation failures;
     * instead, they should return a failed {@link EvaluationResult.TestCaseResult} with
     * a descriptive {@code errorMessage}.
     *
     * @param testCase the test case to execute
     * @param context  the evaluation context (tenant, agent, skill, version info)
     * @return promise of the test case result; never null
     */
    @NotNull
    Promise<EvaluationResult.TestCaseResult> execute(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context);
}
