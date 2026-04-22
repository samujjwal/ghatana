/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowLifecycleEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.*;

@DisplayName("MetricsWorkflowListener Tests [GH-90000]")
class MetricsWorkflowListenerTest {

    private MeterRegistry registry;
    private MetricsWorkflowListener listener;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        listener = new MetricsWorkflowListener(registry); // GH-90000
    }

    @Test
    void shouldCountWorkflowStarted() { // GH-90000
        listener.onEvent(WorkflowLifecycleEvent.of( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED));

        assertThat(registry.counter("workflow.runs.started [GH-90000]").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountWorkflowCompleted() { // GH-90000
        listener.onEvent(WorkflowLifecycleEvent.of( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED));

        assertThat(registry.counter("workflow.runs.completed [GH-90000]").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountWorkflowFailed() { // GH-90000
        listener.onEvent(WorkflowLifecycleEvent.of( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_FAILED));

        assertThat(registry.counter("workflow.runs.failed [GH-90000]").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountWorkflowCompensated() { // GH-90000
        listener.onEvent(WorkflowLifecycleEvent.of( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_COMPENSATED));

        assertThat(registry.counter("workflow.runs.compensated [GH-90000]").count()).isEqualTo(1.0);
    }

    @Test
    void shouldCountStepRetries() { // GH-90000
        listener.onEvent(WorkflowLifecycleEvent.forStep( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_RETRYING, "s1"));

        assertThat(registry.counter("workflow.step.retries [GH-90000]").count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordStepDuration() { // GH-90000
        // Start step
        listener.onEvent(WorkflowLifecycleEvent.forStep( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_STARTED, "s1"));

        // Complete step (small delay) // GH-90000
        listener.onEvent(WorkflowLifecycleEvent.forStep( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_COMPLETED, "s1"));

        assertThat(registry.timer("workflow.step.duration", "step", "s1", "status", "STEP_COMPLETED") // GH-90000
            .count()).isEqualTo(1); // GH-90000
    }

    @Test
    void shouldCountStepFailures() { // GH-90000
        listener.onEvent(WorkflowLifecycleEvent.forStep( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_STARTED, "s1"));

        listener.onEvent(WorkflowLifecycleEvent.forStep( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.STEP_FAILED, "s1"));

        assertThat(registry.counter("workflow.step.failures", "step", "s1").count()) // GH-90000
            .isEqualTo(1.0); // GH-90000
    }
}
