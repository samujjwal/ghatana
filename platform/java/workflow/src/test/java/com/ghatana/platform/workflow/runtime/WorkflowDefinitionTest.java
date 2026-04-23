/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void shouldBuildMinimalDefinition() { // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "My Workflow") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1").withNextStep("s2"))
            .addStep(WorkflowStepDefinition.action("s2", "Step 2", "op-2")) // GH-90000
            .build(); // GH-90000

        assertThat(def.workflowId()).isEqualTo("wf-1");
        assertThat(def.name()).isEqualTo("My Workflow");
        assertThat(def.version()).isEqualTo(1); // GH-90000
        assertThat(def.steps()).hasSize(2); // GH-90000
        assertThat(def.entryStepId()).isEqualTo("s1");
        assertThat(def.sagaPolicy()).isEqualTo(SagaPolicy.NONE); // GH-90000
        assertThat(def.enabled()).isTrue(); // GH-90000
    }

    @Test
    void shouldBuildWithAllOptions() { // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder("wf-2", "Complex WF") // GH-90000
            .version(3) // GH-90000
            .triggerType(WorkflowTriggerType.EVENT) // GH-90000
            .triggerFilter("event.type == 'order.created'")
            .addStep(WorkflowStepDefinition.action("s1", "Validate", "validate-op")) // GH-90000
            .entryStepId("s1")
            .timeout(Duration.ofHours(2)) // GH-90000
            .sagaPolicy(SagaPolicy.BACKWARD_COMPENSATION) // GH-90000
            .metadata("team", "platform") // GH-90000
            .enabled(false) // GH-90000
            .build(); // GH-90000

        assertThat(def.version()).isEqualTo(3); // GH-90000
        assertThat(def.triggerType()).isEqualTo(WorkflowTriggerType.EVENT); // GH-90000
        assertThat(def.triggerFilter()).isEqualTo("event.type == 'order.created'");
        assertThat(def.timeout()).isEqualTo(Duration.ofHours(2)); // GH-90000
        assertThat(def.sagaPolicy()).isEqualTo(SagaPolicy.BACKWARD_COMPENSATION); // GH-90000
        assertThat(def.metadata()).containsEntry("team", "platform"); // GH-90000
        assertThat(def.enabled()).isFalse(); // GH-90000
    }

    @Test
    void shouldFindStepById() { // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1")) // GH-90000
            .addStep(WorkflowStepDefinition.action("s2", "Step 2", "op-2")) // GH-90000
            .build(); // GH-90000

        Optional<WorkflowStepDefinition> found = def.findStep("s2");
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().name()).isEqualTo("Step 2");
    }

    @Test
    void shouldReturnEmptyForMissingStep() { // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1")) // GH-90000
            .build(); // GH-90000

        assertThat(def.findStep("nope")).isEmpty();
    }

    @Test
    void shouldCreateDefensiveCopyOfSteps() { // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "op-1")) // GH-90000
            .build(); // GH-90000

        assertThatThrownBy(() -> def.steps().add( // GH-90000
            WorkflowStepDefinition.action("s2", "Step 2", "op-2"))) // GH-90000
            .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    void shouldDefaultEntryStepToFirstStep() { // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder("wf-1", "WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("first", "First", "op")) // GH-90000
            .addStep(WorkflowStepDefinition.action("second", "Second", "op")) // GH-90000
            .build(); // GH-90000

        assertThat(def.entryStepId()).isEqualTo("first");
    }
}
