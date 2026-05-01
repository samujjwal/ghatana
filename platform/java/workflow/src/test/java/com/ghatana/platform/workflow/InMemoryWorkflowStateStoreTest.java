/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        store = new InMemoryWorkflowStateStore(); 
    }

    private WorkflowRun newRun(String runId, WorkflowRunStatus status) { 
        return new WorkflowRun( 
            runId, "wf-1", "tenant-1", WorkflowKind.DURABLE,
            status, WorkflowOptions.durable(), 
            Instant.now(), null, "step-1", Map.of(), null, null, List.of()); 
    }

    @Test
    void shouldSaveAndFindByRunId() { 
        WorkflowRun run = newRun("run-1", WorkflowRunStatus.PENDING); 

        runPromise(() -> store.save(run) 
            .then(v -> store.findByRunId("run-1"))
            .whenResult(opt -> { 
                assertThat(opt).isPresent(); 
                assertThat(opt.get().runId()).isEqualTo("run-1");
            }));
    }

    @Test
    void shouldReturnEmptyForMissingRunId() { 
        Optional<WorkflowRun> result = runPromise(() -> store.findByRunId("nope"));
        assertThat(result).isEmpty(); 
    }

    @Test
    void shouldFindByWorkflowId() { 
        WorkflowRun run1 = newRun("run-1", WorkflowRunStatus.RUNNING); 
        WorkflowRun run2 = new WorkflowRun( 
            "run-2", "wf-1", "tenant-1", WorkflowKind.DURABLE,
            WorkflowRunStatus.COMPLETED, WorkflowOptions.durable(), 
            Instant.now(), null, null, Map.of(), null, null, List.of()); 

        runPromise(() -> store.save(run1).then(v -> store.save(run2))); 

        List<WorkflowRun> found = runPromise(() -> store.findByWorkflowId("wf-1"));
        assertThat(found).hasSize(2); 
    }

    @Test
    void shouldFindByStatus() { 
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING)) 
            .then(v -> store.save(newRun("run-2", WorkflowRunStatus.COMPLETED))) 
            .then(v -> store.save(newRun("run-3", WorkflowRunStatus.RUNNING)))); 

        List<WorkflowRun> running = runPromise(() -> store.findByStatus(WorkflowRunStatus.RUNNING)); 
        assertThat(running).hasSize(2); 
    }

    @Test
    void shouldUpdateStatus() { 
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING)) 
            .then(v -> store.updateStatus("run-1", WorkflowRunStatus.COMPLETED))); 

        Optional<WorkflowRun> updated = runPromise(() -> store.findByRunId("run-1"));
        assertThat(updated).isPresent(); 
        assertThat(updated.get().status()).isEqualTo(WorkflowRunStatus.COMPLETED); 
    }

    @Test
    void shouldDelete() { 
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING))); 
        store.delete("run-1");

        Optional<WorkflowRun> deleted = runPromise(() -> store.findByRunId("run-1"));
        assertThat(deleted).isEmpty(); 
    }

    @Test
    void shouldTrackSize() { 
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING)) 
            .then(v -> store.save(newRun("run-2", WorkflowRunStatus.RUNNING)))); 

        assertThat(store.size()).isEqualTo(2); 
    }

    @Test
    void shouldClear() { 
        runPromise(() -> store.save(newRun("run-1", WorkflowRunStatus.RUNNING))); 
        store.clear(); 
        assertThat(store.size()).isZero(); 
    }
}
