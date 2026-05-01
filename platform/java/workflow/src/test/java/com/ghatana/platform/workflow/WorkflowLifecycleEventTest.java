/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void shouldCreateWithFactoryMethod() { 
        WorkflowLifecycleEvent event = WorkflowLifecycleEvent.of( 
            "run-1", "wf-1", WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED);

        assertThat(event.runId()).isEqualTo("run-1");
        assertThat(event.workflowId()).isEqualTo("wf-1");
        assertThat(event.phase()).isEqualTo(WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED); 
        assertThat(event.timestamp()).isBeforeOrEqualTo(Instant.now()); 
        assertThat(event.stepId()).isNull(); 
    }

    @Test
    void shouldCreateStepEvent() { 
        WorkflowLifecycleEvent event = WorkflowLifecycleEvent.forStep( 
            "run-1", "wf-1",
            WorkflowLifecycleEvent.Phase.STEP_STARTED, "step-validate");

        assertThat(event.stepId()).isEqualTo("step-validate");
        assertThat(event.phase()).isEqualTo(WorkflowLifecycleEvent.Phase.STEP_STARTED); 
    }

    @Test
    void shouldHaveAllPhases() { 
        assertThat(WorkflowLifecycleEvent.Phase.values()) 
            .hasSizeGreaterThanOrEqualTo(10); 
    }

    @Test
    void shouldRejectNullPhase() { 
        assertThatThrownBy(() -> WorkflowLifecycleEvent.of("r", "w", null)) 
            .isInstanceOf(NullPointerException.class); 
    }
}
