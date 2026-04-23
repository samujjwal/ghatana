/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkflowLifecycleEvent Tests")
class WorkflowLifecycleEventTest {

    @Test
    void shouldCreateWithFactoryMethod() { // GH-90000
        WorkflowLifecycleEvent event = WorkflowLifecycleEvent.of( // GH-90000
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED);

        assertThat(event.runId()).isEqualTo("run-1");
        assertThat(event.workflowId()).isEqualTo("wf-1");
        assertThat(event.phase()).isEqualTo(WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED); // GH-90000
        assertThat(event.timestamp()).isBeforeOrEqualTo(Instant.now()); // GH-90000
        assertThat(event.stepId()).isNull(); // GH-90000
    }

    @Test
    void shouldCreateStepEvent() { // GH-90000
        WorkflowLifecycleEvent event = WorkflowLifecycleEvent.forStep( // GH-90000
            "run-1", "wf-1",
            WorkflowLifecycleEvent.Phase.STEP_STARTED, "step-validate");

        assertThat(event.stepId()).isEqualTo("step-validate");
        assertThat(event.phase()).isEqualTo(WorkflowLifecycleEvent.Phase.STEP_STARTED); // GH-90000
    }

    @Test
    void shouldHaveAllPhases() { // GH-90000
        assertThat(WorkflowLifecycleEvent.Phase.values()) // GH-90000
            .hasSizeGreaterThanOrEqualTo(10); // GH-90000
    }

    @Test
    void shouldRejectNullPhase() { // GH-90000
        assertThatThrownBy(() -> WorkflowLifecycleEvent.of("r", "w", null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
