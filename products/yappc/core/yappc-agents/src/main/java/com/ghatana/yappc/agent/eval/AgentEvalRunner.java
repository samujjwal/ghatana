/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.yappc.agent.eval;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Runs evaluation tasks against agents via the {@link AgentDispatcher}.
 * Collects results into {@link AgentEvalReport} for CI reporting.
 *
 * <p>Supports filtering by category, tags, and agent ID.
 *
 * @doc.type class
 * @doc.purpose Agent evaluation runner for the evaluation flywheel
 * @doc.layer product
 * @doc.pattern Command, Pipeline
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public class AgentEvalRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentEvalRunner.class);

    private final AgentDispatcher dispatcher;

    public AgentEvalRunner(AgentDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /**
     * Runs all eval tasks and produces a report.
     *
     * @param tasks the golden test set
     * @param ctx   execution context for agent dispatch
     * @return a Promise of the evaluation report
     */
    public Promise<AgentEvalReport> runAll(List<AgentEvalTask> tasks, AgentContext ctx) {
        log.info("Starting agent evaluation: {} tasks", tasks.size());
        Instant start = Instant.now();

        List<Promise<AgentEvalReport.TaskResult>> resultPromises = tasks.stream()
                .map(task -> runSingle(task, ctx))
                .toList();

        return Promises.toList(resultPromises)
                .map(results -> {
                    long passed = results.stream().filter(AgentEvalReport.TaskResult::isPassed).count();
                    long failed = results.size() - passed;
                    Duration elapsed = Duration.between(start, Instant.now());

                    AgentEvalReport report = AgentEvalReport.builder()
                            .runId(UUID.randomUUID().toString())
                            .timestamp(start)
                            .totalTasks(results.size())
                            .passed((int) passed)
                            .failed((int) failed)
                            .totalDuration(elapsed)
                            .results(results)
                            .build();

                    log.info("Evaluation complete: {}/{} passed in {}ms",
                            passed, results.size(), elapsed.toMillis());
                    return report;
                });
    }

    /**
     * Runs eval tasks filtered by category.
     */
    public Promise<AgentEvalReport> runByCategory(
            List<AgentEvalTask> tasks, String category, AgentContext ctx) {
        List<AgentEvalTask> filtered = tasks.stream()
                .filter(t -> category.equals(t.getCategory()))
                .toList();
        return runAll(filtered, ctx);
    }

    /**
     * Runs eval tasks filtered by agent ID.
     */
    public Promise<AgentEvalReport> runByAgent(
            List<AgentEvalTask> tasks, String agentId, AgentContext ctx) {
        List<AgentEvalTask> filtered = tasks.stream()
                .filter(t -> agentId.equals(t.getAgentId()))
                .toList();
        return runAll(filtered, ctx);
    }

    private Promise<AgentEvalReport.TaskResult> runSingle(AgentEvalTask task, AgentContext ctx) {
        Instant taskStart = Instant.now();
        log.debug("Running eval task '{}' against agent '{}'", task.getId(), task.getAgentId());

        return dispatcher.<Object, Object>dispatch(task.getAgentId(), task.getInput(), ctx)
                .map(result -> evaluateResult(task, result, taskStart))
                .mapException(e -> {
                    log.warn("Eval task '{}' threw exception: {}", task.getId(), e.getMessage());
                    return e;
                });
    }

    private AgentEvalReport.TaskResult evaluateResult(
            AgentEvalTask task, AgentResult<Object> result, Instant taskStart) {

        Duration elapsed = Duration.between(taskStart, Instant.now());
        List<String> failures = new ArrayList<>();

        // Check status
        if (result.isFailed()) {
            failures.add("Agent returned FAILED status: " + result.getExplanation());
        }

        // Check latency
        if (elapsed.toMillis() > task.getMaxLatencyMs()) {
            failures.add("Latency " + elapsed.toMillis() + "ms exceeded max " + task.getMaxLatencyMs() + "ms");
        }

        // Run assertions
        for (EvalAssertion assertion : task.getAssertions()) {
            String failureMsg = checkAssertion(assertion, result);
            if (failureMsg != null) {
                failures.add(failureMsg);
            }
        }

        boolean passed = failures.isEmpty();
        return AgentEvalReport.TaskResult.builder()
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .category(task.getCategory())
                .passed(passed)
                .duration(elapsed)
                .confidence(result.getConfidence())
                .failures(failures)
                .build();
    }

    private String checkAssertion(EvalAssertion assertion, AgentResult<Object> result) {
        String type = assertion.getType();
        String expected = assertion.getExpected();
        Object output = result.getOutput();
        String outputStr = output != null ? output.toString() : "";

        return switch (type) {
            case "EXACT_MATCH" -> {
                if (!outputStr.equals(expected)) {
                    yield "EXACT_MATCH failed: expected '" + expected + "', got '" + truncate(outputStr) + "'";
                }
                yield null;
            }
            case "CONTAINS" -> {
                if (!outputStr.contains(expected)) {
                    yield "CONTAINS failed: output does not contain '" + expected + "'";
                }
                yield null;
            }
            case "REGEX" -> {
                if (!outputStr.matches(expected)) {
                    yield "REGEX failed: output does not match '" + expected + "'";
                }
                yield null;
            }
            case "CONFIDENCE_MIN" -> {
                double threshold = Double.parseDouble(expected);
                if (result.getConfidence() < threshold) {
                    yield "CONFIDENCE_MIN failed: " + result.getConfidence() + " < " + threshold;
                }
                yield null;
            }
            case "STATUS" -> {
                if (!result.getStatus().name().equals(expected)) {
                    yield "STATUS failed: expected " + expected + ", got " + result.getStatus();
                }
                yield null;
            }
            default -> null;
        };
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
