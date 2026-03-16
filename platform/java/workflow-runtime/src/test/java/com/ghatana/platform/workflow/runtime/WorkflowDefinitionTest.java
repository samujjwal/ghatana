/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.SagaPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkflowDefinition Tests")
class WorkflowDefinitionTest {

    @Test
    void shouldBuildMinimalDefinition() {
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "My Workflow")
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1").withNextStep("s2"))
            .addStep(WorkflowStepDefinition.action("s2", "Step 2", "op-2"))
            .build();

        assertThat(def.workflowId()).isEqualTo("wf-1");
        assertThat(def.name()).isEqualTo("My Workflow");
        assertThat(def.version()).isEqualTo(1);
        assertThat(def.steps()).hasSize(2);
        assertThat(def.entryStepId()).isEqualTo("s1");
        assertThat(def.sagaPolicy()).isEqualTo(SagaPolicy.NONE);
        assertThat(def.enabled()).isTrue();
    }

    @Test
    void shouldBuildWithAllOptions() {
        WorkflowDefinition def = WorkflowDefinition.builder("wf-2", "Complex WF")
            .version(3)
            .triggerType(WorkflowTriggerType.EVENT)
            .triggerFilter("event.type == 'order.created'")
            .addStep(WorkflowStepDefinition.action("s1", "Validate", "validate-op"))
            .entryStepId("s1")
            .timeout(Duration.ofHours(2))
            .sagaPolicy(SagaPolicy.BACKWARD_COMPENSATION)
            .metadata("team", "platform")
            .enabled(false)
            .build();

        assertThat(def.version()).isEqualTo(3);
        assertThat(def.triggerType()).isEqualTo(WorkflowTriggerType.EVENT);
        assertThat(def.triggerFilter()).isEqualTo("event.type == 'order.created'");
        assertThat(def.timeout()).isEqualTo(Duration.ofHours(2));
        assertThat(def.sagaPolicy()).isEqualTo(SagaPolicy.BACKWARD_COMPENSATION);
        assertThat(def.metadata()).containsEntry("team", "platform");
        assertThat(def.enabled()).isFalse();
    }

    @Test
    void shouldFindStepById() {
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF")
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1"))
            .addStep(WorkflowStepDefinition.action("s2", "Step 2", "op-2"))
            .build();

        Optional<WorkflowStepDefinition> found = def.findStep("s2");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Step 2");
    }

    @Test
    void shouldReturnEmptyForMissingStep() {
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF")
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1"))
            .build();

        assertThat(def.findStep("nope")).isEmpty();
    }

    @Test
    void shouldCreateDefensiveCopyOfSteps() {
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF")
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1"))
            .build();

        assertThatThrownBy(() -> def.steps().add(
            WorkflowStepDefinition.action("s2", "Step 2", "op-2")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldDefaultEntryStepToFirstStep() {
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF")
            .addStep(WorkflowStepDefinition.action("first", "First", "op"))
            .addStep(WorkflowStepDefinition.action("second", "Second", "op"))
            .build();

        assertThat(def.entryStepId()).isEqualTo("first");
    }
}
