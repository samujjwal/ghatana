/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkflowRun Tests")
class WorkflowRunTest {

    @Test
    void shouldCreateWithAllFields() { // GH-90000
        Instant now = Instant.now(); // GH-90000
        WorkflowRun run = new WorkflowRun( // GH-90000
            "run-1", "wf-1", "tenant-1", WorkflowKind.DURABLE,
            WorkflowRunStatus.PENDING, WorkflowOptions.durable(), // GH-90000
            now, null, "step-1", Map.of("key", "val"), null, null, List.of()); // GH-90000

        assertThat(run.runId()).isEqualTo("run-1");
        assertThat(run.workflowId()).isEqualTo("wf-1");
        assertThat(run.tenantId()).isEqualTo("tenant-1");
        assertThat(run.kind()).isEqualTo(WorkflowKind.DURABLE); // GH-90000
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.PENDING); // GH-90000
        assertThat(run.currentStepId()).isEqualTo("step-1");
        assertThat(run.errorMessage()).isNull(); // GH-90000
        assertThat(run.startedAt()).isEqualTo(now); // GH-90000
        assertThat(run.completedAt()).isNull(); // GH-90000
        assertThat(run.variables()).containsEntry("key", "val"); // GH-90000
    }

    @Test
    void shouldReturnDefensiveCopyOfVariables() { // GH-90000
        WorkflowRun run = new WorkflowRun( // GH-90000
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(), // GH-90000
            Instant.now(), null, null, Map.of("k", "v"), null, null, List.of()); // GH-90000

        assertThatThrownBy(() -> run.variables().put("x", "y")) // GH-90000
            .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    void shouldCreateWithStatus() { // GH-90000
        WorkflowRun run = new WorkflowRun( // GH-90000
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.PENDING, WorkflowOptions.durable(), // GH-90000
            Instant.now(), null, null, Map.of(), null, null, List.of()); // GH-90000

        WorkflowRun running = run.withStatus(WorkflowRunStatus.RUNNING); // GH-90000

        assertThat(running.status()).isEqualTo(WorkflowRunStatus.RUNNING); // GH-90000
        assertThat(running.runId()).isEqualTo(run.runId()); // GH-90000
    }

    @Test
    void shouldCreateWithError() { // GH-90000
        WorkflowRun run = new WorkflowRun( // GH-90000
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(), // GH-90000
            Instant.now(), null, null, Map.of(), null, null, List.of()); // GH-90000

        WorkflowRun failed = run.withError("something broke");

        assertThat(failed.errorMessage()).isEqualTo("something broke");
    }

    @Test
    void shouldCreateWithCompleted() { // GH-90000
        WorkflowRun run = new WorkflowRun( // GH-90000
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(), // GH-90000
            Instant.now(), null, null, Map.of(), null, null, List.of()); // GH-90000

        Instant completed = Instant.now(); // GH-90000
        WorkflowRun done = run.withCompleted(WorkflowRunStatus.COMPLETED, completed); // GH-90000

        assertThat(done.completedAt()).isEqualTo(completed); // GH-90000
        assertThat(done.status()).isEqualTo(WorkflowRunStatus.COMPLETED); // GH-90000
    }

    @Test
    void shouldCreateWithCurrentStep() { // GH-90000
        WorkflowRun run = new WorkflowRun( // GH-90000
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(), // GH-90000
            Instant.now(), null, "step-1", Map.of(), null, null, List.of()); // GH-90000

        WorkflowRun updated = run.withCurrentStep("step-2");

        assertThat(updated.currentStepId()).isEqualTo("step-2");
    }
}
