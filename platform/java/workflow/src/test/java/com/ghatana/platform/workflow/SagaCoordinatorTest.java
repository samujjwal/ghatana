/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Saga Coordinator Tests [GH-90000]")
class SagaCoordinatorTest {

    @Test
    @DisplayName("completes saga successfully when all steps succeed [GH-90000]")
    void completesSuccessfully() { // GH-90000
        SagaCoordinator coordinator = new SagaCoordinator(); // GH-90000

        SagaStep step1 = SagaStep.builder() // GH-90000
            .name("step1 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of("result", "step1"), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaStep step2 = SagaStep.builder() // GH-90000
            .name("step2 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of("result", "step2"), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaCoordinator.SagaResult result = coordinator.execute( // GH-90000
            "saga-1",
            List.of(step1, step2), // GH-90000
            SagaPolicy.BACKWARD_COMPENSATION
        ).getResult(); // GH-90000

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.COMPLETED); // GH-90000
        assertThat(result.stepResults()).hasSize(2); // GH-90000
        assertThat(result.stepResults().get(0).success()).isTrue(); // GH-90000
        assertThat(result.stepResults().get(1).success()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("compensates steps on failure with backward compensation policy [GH-90000]")
    void compensatesOnFailure() { // GH-90000
        SagaCoordinator coordinator = new SagaCoordinator(); // GH-90000

        SagaStep step1 = SagaStep.builder() // GH-90000
            .name("step1 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaStep step2 = SagaStep.builder() // GH-90000
            .name("step2 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaStep step3 = SagaStep.builder() // GH-90000
            .name("step3 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaCoordinator.SagaResult result = coordinator.execute( // GH-90000
            "saga-2",
            List.of(step1, step2, step3), // GH-90000
            SagaPolicy.BACKWARD_COMPENSATION
        ).getResult(); // GH-90000

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.COMPENSATED); // GH-90000
    }

    @Test
    @DisplayName("continues with forward recovery policy on failure [GH-90000]")
    void continuesWithForwardRecovery() { // GH-90000
        SagaCoordinator coordinator = new SagaCoordinator(); // GH-90000

        SagaStep step1 = SagaStep.builder() // GH-90000
            .name("step1 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaStep step2 = SagaStep.builder() // GH-90000
            .name("step2 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaStep step3 = SagaStep.builder() // GH-90000
            .name("step3 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step3", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaCoordinator.SagaResult result = coordinator.execute( // GH-90000
            "saga-3",
            List.of(step1, step2, step3), // GH-90000
            SagaPolicy.FORWARD_RECOVERY
        ).getResult(); // GH-90000

        // With forward recovery, it should complete all steps despite failure
        assertThat(result.stepResults()).hasSize(3); // GH-90000
    }

    @Test
    @DisplayName("fails immediately with none policy on failure [GH-90000]")
    void failsWithNonePolicy() { // GH-90000
        SagaCoordinator coordinator = new SagaCoordinator(); // GH-90000

        SagaStep step1 = SagaStep.builder() // GH-90000
            .name("step1 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaStep step2 = SagaStep.builder() // GH-90000
            .name("step2 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaCoordinator.SagaResult result = coordinator.execute( // GH-90000
            "saga-4",
            List.of(step1, step2), // GH-90000
            SagaPolicy.NONE
        ).getResult(); // GH-90000

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.FAILED); // GH-90000
        assertThat(result.stepResults()).hasSize(2); // Both steps attempted // GH-90000
    }

    @Test
    @DisplayName("tracks active and completed executions [GH-90000]")
    void tracksExecutions() { // GH-90000
        SagaCoordinator coordinator = new SagaCoordinator(); // GH-90000

        SagaStep step = SagaStep.builder() // GH-90000
            .name("step1 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        coordinator.execute("saga-5", List.of(step), SagaPolicy.BACKWARD_COMPENSATION).getResult(); // GH-90000

        assertThat(coordinator.getActiveExecutions()).isEmpty(); // GH-90000
        assertThat(coordinator.getCompletedExecutions()).hasSize(1); // GH-90000
        assertThat(coordinator.getCompletedExecution("saga-5 [GH-90000]")).isPresent();
    }

    @Test
    @DisplayName("handles partial compensation failure [GH-90000]")
    void handlesPartialCompensationFailure() { // GH-90000
        SagaCoordinator coordinator = new SagaCoordinator(); // GH-90000

        SagaStep step1 = SagaStep.builder() // GH-90000
            .name("step1 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        SagaStep step2 = SagaStep.builder() // GH-90000
            .name("step2 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step2", false, "Step 2 failed", Map.of(), "Failed"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step2", false, "Compensation failed", Map.of(), "Compensation failed"))) // GH-90000
            .build(); // GH-90000

        SagaCoordinator.SagaResult result = coordinator.execute( // GH-90000
            "saga-6",
            List.of(step1, step2), // GH-90000
            SagaPolicy.BACKWARD_COMPENSATION
        ).getResult(); // GH-90000

        assertThat(result.status()).isEqualTo(SagaCoordinator.SagaStatus.PARTIALLY_COMPENSATED); // GH-90000
    }

    @Test
    @DisplayName("clears completed executions [GH-90000]")
    void clearsCompletedExecutions() { // GH-90000
        SagaCoordinator coordinator = new SagaCoordinator(); // GH-90000

        SagaStep step = SagaStep.builder() // GH-90000
            .name("step1 [GH-90000]")
            .execute(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Success"))) // GH-90000
            .compensate(Promise.of(new SagaStep.SagaStepResult("step1", true, null, Map.of(), "Compensated"))) // GH-90000
            .build(); // GH-90000

        coordinator.execute("saga-7", List.of(step), SagaPolicy.BACKWARD_COMPENSATION).getResult(); // GH-90000

        assertThat(coordinator.getCompletedExecutions()).hasSize(1); // GH-90000

        coordinator.clearAllCompleted(); // GH-90000

        assertThat(coordinator.getCompletedExecutions()).isEmpty(); // GH-90000
    }
}
