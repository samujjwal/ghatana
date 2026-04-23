/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InMemoryWorkflowStateStore Tests")
class InMemoryWorkflowStateStoreTest extends EventloopTestBase {

    private InMemoryWorkflowStateStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryWorkflowStateStore(); // GH-90000
    }

    private WorkflowRun newRun(String runId, WorkflowRunStatus status) { // GH-90000
        return new WorkflowRun( // GH-90000
            runId, "wf-1", "tenant-1", WorkflowKind.DURABLE,
            status, WorkflowOptions.durable(), // GH-90000
            Instant.now(), null, "step-1", Map.of(), null, null, List.of()); // GH-90000
    }

    @Test
    void shouldSaveAndFindByRunId() { // GH-90000
        WorkflowRun run = newRun("run-1", WorkflowRunStatus.PENDING); // GH-90000

        runPromise(() -> store.save(run) // GH-90000
            .then(v -> store.findByRunId("run-1"))
            .whenResult(opt -> { // GH-90000
                assertThat(opt).isPresent(); // GH-90000
                assertThat(opt.get().runId()).isEqualTo("run-1");
            }));
    }

    @Test
    void shouldReturnEmptyForMissingRunId() { // GH-90000
        Optional<WorkflowRun> result = runPromise(() -> store.findByRunId("nope"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    void shouldFindByWorkflowId() { // GH-90000
        WorkflowRun run1 = newRun("run-1", WorkflowRunStatus.RUNNING); // GH-90000
        WorkflowRun run2 = new WorkflowRun( // GH-90000
            "run-2", "wf-1", "tenant-1", WorkflowKind.DURABLE,
            WorkflowRunStatus.COMPLETED, WorkflowOptions.durable(), // GH-90000
            Instant.now(), null, null, Map.of(), null, null, List.of()); // GH-90000

        runPromise(() -> store.save(run1).then(v -> store.save(run2))); // GH-90000

        List<WorkflowRun> found = runPromise(() -> store.findByWorkflowId("wf-1"));
        assertThat(found).hasSize(2); // GH-90000
    }

    @Test
    void shouldFindByStatus() { // GH-90000
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING)) // GH-90000
            .then(v -> store.save(newRun("run-2", WorkflowRunStatus.COMPLETED))) // GH-90000
            .then(v -> store.save(newRun("run-3", WorkflowRunStatus.RUNNING)))); // GH-90000

        List<WorkflowRun> running = runPromise(() -> store.findByStatus(WorkflowRunStatus.RUNNING)); // GH-90000
        assertThat(running).hasSize(2); // GH-90000
    }

    @Test
    void shouldUpdateStatus() { // GH-90000
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING)) // GH-90000
            .then(v -> store.updateStatus("run-1", WorkflowRunStatus.COMPLETED))); // GH-90000

        Optional<WorkflowRun> updated = runPromise(() -> store.findByRunId("run-1"));
        assertThat(updated).isPresent(); // GH-90000
        assertThat(updated.get().status()).isEqualTo(WorkflowRunStatus.COMPLETED); // GH-90000
    }

    @Test
    void shouldDelete() { // GH-90000
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING))); // GH-90000
        store.delete("run-1");

        Optional<WorkflowRun> deleted = runPromise(() -> store.findByRunId("run-1"));
        assertThat(deleted).isEmpty(); // GH-90000
    }

    @Test
    void shouldTrackSize() { // GH-90000
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING)) // GH-90000
            .then(v -> store.save(newRun("run-2", WorkflowRunStatus.RUNNING)))); // GH-90000

        assertThat(store.size()).isEqualTo(2); // GH-90000
    }

    @Test
    void shouldClear() { // GH-90000
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING))); // GH-90000
        store.clear(); // GH-90000
        assertThat(store.size()).isZero(); // GH-90000
    }
}
