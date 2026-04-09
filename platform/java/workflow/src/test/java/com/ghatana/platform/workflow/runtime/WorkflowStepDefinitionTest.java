/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WorkflowStepDefinition Tests")
class WorkflowStepDefinitionTest {

    @Test
    void shouldCreateActionStep() {
        WorkflowStepDefinition step = WorkflowStepDefinition.action("s1", "Validate", "validate-op");

        assertThat(step.stepId()).isEqualTo("s1");
        assertThat(step.name()).isEqualTo("Validate");
        assertThat(step.kind()).isEqualTo(WorkflowStepKind.ACTION);
        assertThat(step.operatorId()).isEqualTo("validate-op");
        assertThat(step.maxRetries()).isZero();
        assertThat(step.config()).isEmpty();
    }

    @Test
    void shouldCreateDecisionStep() {
        WorkflowStepDefinition step = WorkflowStepDefinition.decision(
            "s2", "Check Amount", "ctx.amount > 1000", "step-high", "step-low");

        assertThat(step.kind()).isEqualTo(WorkflowStepKind.DECISION);
        assertThat(step.celCondition()).isEqualTo("ctx.amount > 1000");
        assertThat(step.nextStepOnTrue()).isEqualTo("step-high");
        assertThat(step.nextStepOnFalse()).isEqualTo("step-low");
    }

    @Test
    void shouldCreateWaitStep() {
        WorkflowStepDefinition step = WorkflowStepDefinition.wait(
            "s3", "Await Approval", Duration.ofHours(24));

        assertThat(step.kind()).isEqualTo(WorkflowStepKind.WAIT);
        assertThat(step.timeout()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void shouldCreateSubWorkflowStep() {
        WorkflowStepDefinition step = WorkflowStepDefinition.subWorkflow(
            "s4", "Run Sub", "sub-wf-1");

        assertThat(step.kind()).isEqualTo(WorkflowStepKind.SUB_WORKFLOW);
        assertThat(step.subWorkflowId()).isEqualTo("sub-wf-1");
    }

    @Test
    void shouldApplyRetries() {
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op");
        WorkflowStepDefinition withRetries = base.withRetries(5, Duration.ofSeconds(2));

        assertThat(withRetries.maxRetries()).isEqualTo(5);
        assertThat(withRetries.retryBackoff()).isEqualTo(Duration.ofSeconds(2));
        assertThat(withRetries.stepId()).isEqualTo("s1");
    }

    @Test
    void shouldApplyTimeout() {
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op");
        WorkflowStepDefinition withTimeout = base.withTimeout(Duration.ofMinutes(3));

        assertThat(withTimeout.timeout()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void shouldApplyCompensation() {
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op");
        WorkflowStepDefinition withComp = base.withCompensation("comp-step");

        assertThat(withComp.compensationStepId()).isEqualTo("comp-step");
    }

    @Test
    void shouldApplyNextStep() {
        WorkflowStepDefinition base = WorkflowStepDefinition.action("s1", "Do", "op");
        WorkflowStepDefinition withNext = base.withNextStep("s2");

        assertThat(withNext.nextStep()).isEqualTo("s2");
    }

    @Test
    void shouldRejectNullStepId() {
        assertThatThrownBy(() -> WorkflowStepDefinition.action(null, "n", "op"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCreateDefensiveCopyOfConfig() {
        var mutableConfig = new java.util.HashMap<String, Object>();
        mutableConfig.put("k", "v");

        WorkflowStepDefinition step = new WorkflowStepDefinition(
            "s1", "n", WorkflowStepKind.ACTION,
            "op", null, null, null, null, null,
            0, null, null, null, mutableConfig);

        // Modifying original shouldn't affect step
        mutableConfig.put("k2", "v2");
        assertThat(step.config()).doesNotContainKey("k2");
    }
}
