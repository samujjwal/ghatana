/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowLifecycleEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MetricsWorkflowListener Tests")
class MetricsWorkflowListenerTest {

    private MeterRegistry registry;
    private MetricsWorkflowListener listener;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new MetricsWorkflowListener(registry);
    }

    @Test
    void shouldCountWorkflowStarted() {
        listener.onEvent(WorkflowLifecycleEvent.of(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED));

        assertThat(registry.counter("workflow.runs.started").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountWorkflowCompleted() {
        listener.onEvent(WorkflowLifecycleEvent.of(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED));

        assertThat(registry.counter("workflow.runs.completed").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountWorkflowFailed() {
        listener.onEvent(WorkflowLifecycleEvent.of(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_FAILED));

        assertThat(registry.counter("workflow.runs.failed").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountWorkflowCompensated() {
        listener.onEvent(WorkflowLifecycleEvent.of(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_COMPENSATED));

        assertThat(registry.counter("workflow.runs.compensated").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountStepRetries() {
        listener.onEvent(WorkflowLifecycleEvent.forStep(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_RETRYING, "s1"));

        assertThat(registry.counter("workflow.step.retries").count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordStepDuration() {
        // Start step
        listener.onEvent(WorkflowLifecycleEvent.forStep(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_STARTED, "s1"));

        // Complete step (small delay)
        listener.onEvent(WorkflowLifecycleEvent.forStep(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_COMPLETED, "s1"));

        assertThat(registry.timer("workflow.step.duration", "step", "s1", "status", "STEP_COMPLETED")
            .count()).isEqualTo(1);
    }

    @Test
    void shouldCountStepFailures() {
        listener.onEvent(WorkflowLifecycleEvent.forStep(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_STARTED, "s1"));

        listener.onEvent(WorkflowLifecycleEvent.forStep(
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_FAILED, "s1"));

        assertThat(registry.counter("workflow.step.failures", "step", "s1").count())
            .isEqualTo(1.0);
    }
}
