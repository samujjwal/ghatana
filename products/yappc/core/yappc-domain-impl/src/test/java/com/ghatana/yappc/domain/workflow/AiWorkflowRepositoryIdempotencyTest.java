package com.ghatana.yappc.domain.workflow;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryAiWorkflowRepository Idempotency Tests")
class AiWorkflowRepositoryIdempotencyTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-idempotency";

    private InMemoryAiWorkflowRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAiWorkflowRepository();
    }

    @Test
    @DisplayName("YD-3: delete is idempotent across repeated calls")
    void delete_isIdempotent() {
        AiWorkflowInstance workflow = AiWorkflowInstance.create(
                "wf-delete",
                TENANT_ID,
                "Delete Flow",
                "test",
                AiWorkflowInstance.WorkflowType.TESTING);
        runPromise(() -> repository.save(workflow));

        boolean firstDelete = runPromise(() -> repository.delete(workflow.id(), TENANT_ID));
        boolean secondDelete = runPromise(() -> repository.delete(workflow.id(), TENANT_ID));

        assertThat(firstDelete).isTrue();
        assertThat(secondDelete).isFalse();
    }

    @Test
    @DisplayName("YD-3: step result upsert is deterministic for same stepId")
    void saveStepResult_upsertsByStepId() {
        AiWorkflowInstance workflow = AiWorkflowInstance.create(
                "wf-step",
                TENANT_ID,
                "Step Flow",
                "test",
                AiWorkflowInstance.WorkflowType.TESTING);
        runPromise(() -> repository.save(workflow));

        AiWorkflowInstance.AiWorkflowStepResult first = new AiWorkflowInstance.AiWorkflowStepResult(
                "step-1",
                "Step One",
                AiWorkflowInstance.AiWorkflowStepResult.StepStatus.IN_PROGRESS,
                "first-output",
                null,
                null,
                Instant.now(),
                null,
                null,
                Map.of("attempt", 1));

        AiWorkflowInstance.AiWorkflowStepResult second = new AiWorkflowInstance.AiWorkflowStepResult(
                "step-1",
                "Step One",
                AiWorkflowInstance.AiWorkflowStepResult.StepStatus.COMPLETED,
                "second-output",
                null,
                null,
                Instant.now(),
                Instant.now(),
                null,
                Map.of("attempt", 2));

        runPromise(() -> repository.saveStepResult(workflow.id(), TENANT_ID, first));
        runPromise(() -> repository.saveStepResult(workflow.id(), TENANT_ID, second));

        Optional<AiWorkflowInstance> stored = runPromise(() -> repository.findById(workflow.id(), TENANT_ID));

        assertThat(stored).isPresent();
        assertThat(stored.orElseThrow().stepResults()).hasSize(1);
        assertThat(stored.orElseThrow().stepResults().get("step-1").status())
                .isEqualTo(AiWorkflowInstance.AiWorkflowStepResult.StepStatus.COMPLETED);
        assertThat(stored.orElseThrow().stepResults().get("step-1").output())
                .isEqualTo("second-output");
    }
}

