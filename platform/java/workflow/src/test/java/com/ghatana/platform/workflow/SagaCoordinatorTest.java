/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Saga coordinator.
 *
 * @doc.type class
 * @doc.purpose Unit tests for Saga coordinator
 * @doc.layer test
 */
@DisplayName("Saga Coordinator Tests")
class SagaCoordinatorTest {

    @Test
    @DisplayName("completes saga successfully when all steps succeed")
    void completesSuccessfully() {
        SagaCoordinator coordinator = new SagaCoordinator();

        SagaStep step1 = SagaStep.builder()
            .name("step1")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of("result", "step1"), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated")))
            .build();

        SagaStep step2 = SagaStep.builder()
            .name("step2")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of("result", "step2"), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated")))
            .build();

        SagaCoordinator.SagaResult result = coordinator.execute(
            "saga-1",
            List.of(step1, step2),
            SagaPolicy.BACKWARD_COMPENSATION
        ).getResult();

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.COMPLETED);
        assertThat(result.stepResults()).hasSize(2);
        assertThat(result.stepResults().get(0).success()).isTrue();
        assertThat(result.stepResults().get(1).success()).isTrue();
    }

    @Test
    @DisplayName("compensates steps on failure with backward compensation policy")
    void compensatesOnFailure() {
        SagaCoordinator coordinator = new SagaCoordinator();

        SagaStep step1 = SagaStep.builder()
            .name("step1")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated")))
            .build();

        SagaStep step2 = SagaStep.builder()
            .name("step2")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated")))
            .build();

        SagaStep step3 = SagaStep.builder()
            .name("step3")
            .execute(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Compensated")))
            .build();

        SagaCoordinator.SagaResult result = coordinator.execute(
            "saga-2",
            List.of(step1, step2, step3),
            SagaPolicy.BACKWARD_COMPENSATION
        ).getResult();

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.COMPENSATED);
    }

    @Test
    @DisplayName("continues with forward recovery policy on failure")
    void continuesWithForwardRecovery() {
        SagaCoordinator coordinator = new SagaCoordinator();

        SagaStep step1 = SagaStep.builder()
            .name("step1")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated")))
            .build();

        SagaStep step2 = SagaStep.builder()
            .name("step2")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated")))
            .build();

        SagaStep step3 = SagaStep.builder()
            .name("step3")
            .execute(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Compensated")))
            .build();

        SagaCoordinator.SagaResult result = coordinator.execute(
            "saga-3",
            List.of(step1, step2, step3),
            SagaPolicy.FORWARD_RECOVERY
        ).getResult();

        // With forward recovery, it should complete all steps despite failure
        assertThat(result.stepResults()).hasSize(3);
    }

    @Test
    @DisplayName("fails immediately with none policy on failure")
    void failsWithNonePolicy() {
        SagaCoordinator coordinator = new SagaCoordinator();

        SagaStep step1 = SagaStep.builder()
            .name("step1")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated")))
            .build();

        SagaStep step2 = SagaStep.builder()
            .name("step2")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated")))
            .build();

        SagaCoordinator.SagaResult result = coordinator.execute(
            "saga-4",
            List.of(step1, step2),
            SagaPolicy.NONE
        ).getResult();

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.FAILED);
        assertThat(result.stepResults()).hasSize(2); // Both steps attempted
    }

    @Test
    @DisplayName("tracks active and completed executions")
    void tracksExecutions() {
        SagaCoordinator coordinator = new SagaCoordinator();

        SagaStep step = SagaStep.builder()
            .name("step1")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated")))
            .build();

        coordinator.execute("saga-5", List.of(step), SagaPolicy.BACKWARD_COMPENSATION).getResult();

        assertThat(coordinator.getActiveExecutions()).isEmpty();
        assertThat(coordinator.getCompletedExecutions()).hasSize(1);
        assertThat(coordinator.getCompletedExecution("saga-5")).isPresent();
    }

    @Test
    @DisplayName("handles partial compensation failure")
    void handlesPartialCompensationFailure() {
        SagaCoordinator coordinator = new SagaCoordinator();

        SagaStep step1 = SagaStep.builder()
            .name("step1")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated")))
            .build();

        SagaStep step2 = SagaStep.builder()
            .name("step2")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", false, "Compensation failed", Map.of(), "Compensation failed")))
            .build();

        SagaCoordinator.SagaResult result = coordinator.execute(
            "saga-6",
            List.of(step1, step2),
            SagaPolicy.BACKWARD_COMPENSATION
        ).getResult();

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.PARTIALLY_COMPENSATED);
    }

    @Test
    @DisplayName("clears completed executions")
    void clearsCompletedExecutions() {
        SagaCoordinator coordinator = new SagaCoordinator();

        SagaStep step = SagaStep.builder()
            .name("step1")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success")))
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated")))
            .build();

        coordinator.execute("saga-7", List.of(step), SagaPolicy.BACKWARD_COMPENSATION).getResult();

        assertThat(coordinator.getCompletedExecutions()).hasSize(1);

        coordinator.clearAllCompleted();

        assertThat(coordinator.getCompletedExecutions()).isEmpty();
    }
}
