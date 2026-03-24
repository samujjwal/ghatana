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

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Aggregated report from an evaluation run. Contains per-task results,
 * pass/fail counts, and total duration. Serializable to JSON for CI reporting.
 *
 * @doc.type record
 * @doc.purpose Evaluation run report for CI and dashboard consumption
 * @doc.layer product
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
@Value
@Builder
public class AgentEvalReport {

    /** Unique run identifier. */
    String runId;

    /** When the evaluation started. */
    Instant timestamp;

    /** Total number of eval tasks executed. */
    int totalTasks;

    /** Number of tasks that passed all assertions. */
    int passed;

    /** Number of tasks that failed one or more assertions. */
    int failed;

    /** Total wall-clock duration of the evaluation run. */
    Duration totalDuration;

    /** Per-task results. */
    List<TaskResult> results;

    /** Whether the entire run is considered passing. */
    public boolean isAllPassed() {
        return failed == 0;
    }

    /** Pass rate as a percentage [0.0, 100.0]. */
    public double passRate() {
        return totalTasks == 0 ? 100.0 : (passed * 100.0) / totalTasks;
    }

    /**
     * Result for a single evaluation task.
     */
    @Value
    @Builder
    public static class TaskResult {

        /** The eval task ID. */
        String taskId;

        /** The agent that was evaluated. */
        String agentId;

        /** Evaluation category (unit, integration, regression, safety, cost, drift). */
        String category;

        /** Whether this task passed all assertions. */
        boolean passed;

        /** Agent processing duration. */
        Duration duration;

        /** Agent confidence score. */
        double confidence;

        /** List of failure messages (empty if passed). */
        @Builder.Default
        List<String> failures = List.of();
    }
}
