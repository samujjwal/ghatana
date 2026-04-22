/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkflowStepDefinition Tests [GH-90000]")
class WorkflowStepDefinitionTest {

    @Test
    void shouldCreateActionStep() { // GH-90000
        WorkflowStepDefinition step = WorkflowStepDefinition.action("s1", "Validate", "validate-op"); // GH-90000

        assertThat(step.stepId()).isEqualTo("s1 [GH-90000]");
        assertThat(step.name()).isEqualTo("Validate [GH-90000]");
        assertThat(step.kind()).isEqualTo(WorkflowStepKind.ACTION); // GH-90000
        assertThat(step.operatorId()).isEqualTo("validate-op [GH-90000]");
        assertThat(step.maxRetries()).isZero(); // GH-90000
        assertThat(step.config()).isEmpty(); // GH-90000
    }

    @Test
    void shouldCreateDecisionStep() { // GH-90000
        WorkflowStepDefinition step = WorkflowStepDefinition.decision( // GH-90000
            "s2", "Check Amount", "ctx.amount > 1000", "step-high", "step-low");

        assertThat(step.kind()).isEqualTo(WorkflowStepKind.DECISION); // GH-90000
        assertThat(step.celCondition()).isEqualTo("ctx.amount > 1000 [GH-90000]");
        assertThat(step.nextStepOnTrue()).isEqualTo("step-high [GH-90000]");
        assertThat(step.nextStepOnFalse()).isEqualTo("step-low [GH-90000]");
    }

    @Test
    void shouldCreateWaitStep() { // GH-90000
        WorkflowStepDefinition step = WorkflowStepDefinition.wait( // GH-90000
            "s3", "Await Approval", Duration.ofHours(24)); // GH-90000

        assertThat(step.kind()).isEqualTo(WorkflowStepKind.WAIT); // GH-90000
        assertThat(step.timeout()).isEqualTo(Duration.ofHours(24)); // GH-90000
    }

    @Test
    void shouldCreateSubWorkflowStep() { // GH-90000
        WorkflowStepDefinition step = WorkflowStepDefinition.subWorkflow( // GH-90000
            "s4", "Run Sub", "sub-wf-1");

        assertThat(step.kind()).isEqualTo(WorkflowStepKind.SUB_WORKFLOW); // GH-90000
        assertThat(step.subWorkflowId()).isEqualTo("sub-wf-1 [GH-90000]");
    }

    @Test
    void shouldApplyRetries() { // GH-90000
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op"); // GH-90000
        WorkflowStepDefinition withRetries = base.withRetries(5, Duration.ofSeconds(2)); // GH-90000

        assertThat(withRetries.maxRetries()).isEqualTo(5); // GH-90000
        assertThat(withRetries.retryBackoff()).isEqualTo(Duration.ofSeconds(2)); // GH-90000
        assertThat(withRetries.stepId()).isEqualTo("s1 [GH-90000]");
    }

    @Test
    void shouldApplyTimeout() { // GH-90000
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op"); // GH-90000
        WorkflowStepDefinition withTimeout = base.withTimeout(Duration.ofMinutes(3)); // GH-90000

        assertThat(withTimeout.timeout()).isEqualTo(Duration.ofMinutes(3)); // GH-90000
    }

    @Test
    void shouldApplyCompensation() { // GH-90000
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op"); // GH-90000
        WorkflowStepDefinition withComp = base.withCompensation("comp-step [GH-90000]");

        assertThat(withComp.compensationStepId()).isEqualTo("comp-step [GH-90000]");
    }

    @Test
    void shouldApplyNextStep() { // GH-90000
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op"); // GH-90000
        WorkflowStepDefinition withNext = base.withNextStep("s2 [GH-90000]");

        assertThat(withNext.nextStep()).isEqualTo("s2 [GH-90000]");
    }

    @Test
    void shouldRejectNullStepId() { // GH-90000
        assertThatThrownBy(() -> WorkflowStepDefinition.action(null, "n", "op")) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    void shouldCreateDefensiveCopyOfConfig() { // GH-90000
        var mutableConfig = new java.util.HashMap<String, Object>(); // GH-90000
        mutableConfig.put("k", "v"); // GH-90000

        WorkflowStepDefinition step = new WorkflowStepDefinition( // GH-90000
            "s1", "n", WorkflowStepKind.ACTION,
            "op", null, null, null, null, null,
            0, null, null, null, mutableConfig);

        // Modifying original shouldn't affect step
        mutableConfig.put("k2", "v2"); // GH-90000
        assertThat(step.config()).doesNotContainKey("k2 [GH-90000]");
    }
}
