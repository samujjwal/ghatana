/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void shouldCreateWithAllFields() {
        Instant now = Instant.now();
        WorkflowRun run = new WorkflowRun(
            "run-1", "wf-1", "tenant-1", WorkflowKind.DURABLE,
            WorkflowRunStatus.PENDING, WorkflowOptions.durable(),
            now, null, "step-1", Map.of("key", "val"), null, null, List.of());

        assertThat(run.runId()).isEqualTo("run-1");
        assertThat(run.workflowId()).isEqualTo("wf-1");
        assertThat(run.tenantId()).isEqualTo("tenant-1");
        assertThat(run.kind()).isEqualTo(WorkflowKind.DURABLE);
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.PENDING);
        assertThat(run.currentStepId()).isEqualTo("step-1");
        assertThat(run.errorMessage()).isNull();
        assertThat(run.startedAt()).isEqualTo(now);
        assertThat(run.completedAt()).isNull();
        assertThat(run.variables()).containsEntry("key", "val");
    }

    @Test
    void shouldReturnDefensiveCopyOfVariables() {
        WorkflowRun run = new WorkflowRun(
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(),
            Instant.now(), null, null, Map.of("k", "v"), null, null, List.of());

        assertThatThrownBy(() -> run.variables().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldCreateWithStatus() {
        WorkflowRun run = new WorkflowRun(
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.PENDING, WorkflowOptions.durable(),
            Instant.now(), null, null, Map.of(), null, null, List.of());

        WorkflowRun running = run.withStatus(WorkflowRunStatus.RUNNING);

        assertThat(running.status()).isEqualTo(WorkflowRunStatus.RUNNING);
        assertThat(running.runId()).isEqualTo(run.runId());
    }

    @Test
    void shouldCreateWithError() {
        WorkflowRun run = new WorkflowRun(
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(),
            Instant.now(), null, null, Map.of(), null, null, List.of());

        WorkflowRun failed = run.withError("something broke");

        assertThat(failed.errorMessage()).isEqualTo("something broke");
    }

    @Test
    void shouldCreateWithCompleted() {
        WorkflowRun run = new WorkflowRun(
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(),
            Instant.now(), null, null, Map.of(), null, null, List.of());

        Instant completed = Instant.now();
        WorkflowRun done = run.withCompleted(WorkflowRunStatus.COMPLETED, completed);

        assertThat(done.completedAt()).isEqualTo(completed);
        assertThat(done.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
    }

    @Test
    void shouldCreateWithCurrentStep() {
        WorkflowRun run = new WorkflowRun(
            "r", "w", "t", WorkflowKind.DURABLE,
            WorkflowRunStatus.RUNNING, WorkflowOptions.durable(),
            Instant.now(), null, "step-1", Map.of(), null, null, List.of());

        WorkflowRun updated = run.withCurrentStep("step-2");

        assertThat(updated.currentStepId()).isEqualTo("step-2");
    }
}
