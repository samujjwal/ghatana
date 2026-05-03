package com.ghatana.digitalmarketing.domain.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmWorkflowStep} and {@link DmWorkflowExecution}.
 *
 * @doc.type class
 * @doc.purpose Verifies workflow domain entity lifecycle (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Workflow domain entity tests")
class DmWorkflowExecutionTest {

    // ── DmWorkflowStep ────────────────────────────────────────────────────────

    private DmWorkflowStep pendingStep(String name) {
        return DmWorkflowStep.builder()
            .name(name)
            .stepType("ACTION")
            .status(DmWorkflowStepStatus.PENDING)
            .build();
    }

    @Test
    @DisplayName("markExecuting transitions step to EXECUTING with startedAt")
    void stepMarkExecuting() {
        DmWorkflowStep step = pendingStep("step-1").markExecuting();
        assertThat(step.getStatus()).isEqualTo(DmWorkflowStepStatus.EXECUTING);
        assertThat(step.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("markCompleted transitions step to COMPLETED with completedAt")
    void stepMarkCompleted() {
        DmWorkflowStep step = pendingStep("step-1").markExecuting().markCompleted();
        assertThat(step.getStatus()).isEqualTo(DmWorkflowStepStatus.COMPLETED);
        assertThat(step.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed transitions step to FAILED with reason")
    void stepMarkFailed() {
        DmWorkflowStep step = pendingStep("step-1").markExecuting().markFailed("api-error");
        assertThat(step.getStatus()).isEqualTo(DmWorkflowStepStatus.FAILED);
        assertThat(step.getFailureReason()).isEqualTo("api-error");
    }

    @Test
    @DisplayName("markFailed rejects null reason")
    void stepMarkFailedRejectsNull() {
        DmWorkflowStep step = pendingStep("s").markExecuting();
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> step.markFailed(null));
    }

    @Test
    @DisplayName("markSkipped transitions step to SKIPPED")
    void stepMarkSkipped() {
        DmWorkflowStep step = pendingStep("step-1").markSkipped();
        assertThat(step.getStatus()).isEqualTo(DmWorkflowStepStatus.SKIPPED);
    }

    @Test
    @DisplayName("isTerminal is true for COMPLETED, FAILED, SKIPPED")
    void stepIsTerminal() {
        assertThat(pendingStep("s").markCompleted().isTerminal()).isTrue();
        assertThat(pendingStep("s").markExecuting().markFailed("e").isTerminal()).isTrue();
        assertThat(pendingStep("s").markSkipped().isTerminal()).isTrue();
        assertThat(pendingStep("s").isTerminal()).isFalse();
    }

    @Test
    @DisplayName("DmWorkflowStep builder rejects blank name")
    void stepBuilderRejectsBlankName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmWorkflowStep.builder().name("  ").stepType("T")
                .status(DmWorkflowStepStatus.PENDING).build());
    }

    @Test
    @DisplayName("DmWorkflowStep builder rejects null status")
    void stepBuilderRejectsNullStatus() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> DmWorkflowStep.builder().name("n").stepType("T").build());
    }

    @Test
    @DisplayName("DmWorkflowStep equals and hashCode use name and stepType")
    void stepEqualsAndHashCode() {
        DmWorkflowStep a = pendingStep("step-1");
        DmWorkflowStep b = pendingStep("step-1").markExecuting();
        DmWorkflowStep c = pendingStep("step-2");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("DmWorkflowStep toString contains name and status")
    void stepToString() {
        String str = pendingStep("my-step").toString();
        assertThat(str).contains("my-step").contains("PENDING");
    }

    // ── DmWorkflowExecution ───────────────────────────────────────────────────

    private DmWorkflowExecution.Builder validExecutionBuilder() {
        return DmWorkflowExecution.builder()
            .id(UUID.randomUUID().toString())
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .name("Campaign Launcher")
            .correlationId("corr-1")
            .steps(List.of(pendingStep("step-1"), pendingStep("step-2")))
            .currentStepIndex(0)
            .status(DmWorkflowStatus.PENDING)
            .createdAt(Instant.now());
    }

    @Test
    @DisplayName("start transitions PENDING to RUNNING")
    void executionStart() {
        DmWorkflowExecution running = validExecutionBuilder().build().start();
        assertThat(running.getStatus()).isEqualTo(DmWorkflowStatus.RUNNING);
    }

    @Test
    @DisplayName("start rejects non-PENDING execution")
    void executionStartRejectsNonPending() {
        DmWorkflowExecution running = validExecutionBuilder().build().start();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(running::start);
    }

    @Test
    @DisplayName("complete transitions RUNNING to COMPLETED")
    void executionComplete() {
        DmWorkflowExecution completed = validExecutionBuilder().build().start().complete();
        assertThat(completed.getStatus()).isEqualTo(DmWorkflowStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("complete rejects non-RUNNING execution")
    void executionCompleteRejectsNonRunning() {
        DmWorkflowExecution pending = validExecutionBuilder().build();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(pending::complete);
    }

    @Test
    @DisplayName("fail transitions RUNNING to FAILED")
    void executionFail() {
        DmWorkflowExecution failed = validExecutionBuilder().build().start().fail("timeout");
        assertThat(failed.getStatus()).isEqualTo(DmWorkflowStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("fail rejects invalid states")
    void executionFailRejectsInvalid() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> validExecutionBuilder().build().fail("err"));
    }

    @Test
    @DisplayName("pause transitions RUNNING to PAUSED")
    void executionPause() {
        DmWorkflowExecution paused = validExecutionBuilder().build().start().pause();
        assertThat(paused.getStatus()).isEqualTo(DmWorkflowStatus.PAUSED);
    }

    @Test
    @DisplayName("pause rejects non-RUNNING execution")
    void executionPauseRejectsNonRunning() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> validExecutionBuilder().build().pause());
    }

    @Test
    @DisplayName("resume transitions PAUSED to RUNNING")
    void executionResume() {
        DmWorkflowExecution running = validExecutionBuilder().build().start().pause().resume();
        assertThat(running.getStatus()).isEqualTo(DmWorkflowStatus.RUNNING);
    }

    @Test
    @DisplayName("resume rejects non-PAUSED execution")
    void executionResumeRejectsNonPaused() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> validExecutionBuilder().build().start().resume());
    }

    @Test
    @DisplayName("rollback transitions FAILED to ROLLED_BACK")
    void executionRollback() {
        DmWorkflowExecution rb = validExecutionBuilder().build().start().fail("err").rollback();
        assertThat(rb.getStatus()).isEqualTo(DmWorkflowStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("rollback rejects non-FAILED execution")
    void executionRollbackRejectsNonFailed() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> validExecutionBuilder().build().rollback());
    }

    @Test
    @DisplayName("advanceStep updates step and increments currentStepIndex")
    void executionAdvanceStep() {
        DmWorkflowExecution exec = validExecutionBuilder().build().start();
        DmWorkflowStep done = exec.currentStep().markCompleted();
        DmWorkflowExecution advanced = exec.advanceStep(done);

        assertThat(advanced.getCurrentStepIndex()).isEqualTo(1);
        assertThat(advanced.getSteps().get(0).getStatus()).isEqualTo(DmWorkflowStepStatus.COMPLETED);
    }

    @Test
    @DisplayName("currentStep returns null when beyond steps list")
    void executionCurrentStepNullWhenBeyond() {
        DmWorkflowExecution exec = validExecutionBuilder()
            .currentStepIndex(99).build();
        assertThat(exec.currentStep()).isNull();
    }

    @Test
    @DisplayName("isTerminal is true for COMPLETED, FAILED, ROLLED_BACK")
    void executionIsTerminal() {
        assertThat(validExecutionBuilder().status(DmWorkflowStatus.COMPLETED).build().isTerminal()).isTrue();
        assertThat(validExecutionBuilder().status(DmWorkflowStatus.FAILED).build().isTerminal()).isTrue();
        assertThat(validExecutionBuilder().status(DmWorkflowStatus.ROLLED_BACK).build().isTerminal()).isTrue();
        assertThat(validExecutionBuilder().status(DmWorkflowStatus.PENDING).build().isTerminal()).isFalse();
        assertThat(validExecutionBuilder().status(DmWorkflowStatus.RUNNING).build().isTerminal()).isFalse();
        assertThat(validExecutionBuilder().status(DmWorkflowStatus.PAUSED).build().isTerminal()).isFalse();
    }

    @Test
    @DisplayName("equals and hashCode are id-based")
    void executionEqualsAndHashCode() {
        String id = UUID.randomUUID().toString();
        DmWorkflowExecution a = validExecutionBuilder().id(id).build();
        DmWorkflowExecution b = validExecutionBuilder().id(id).name("Different Name").build();
        DmWorkflowExecution c = validExecutionBuilder().id(UUID.randomUUID().toString()).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString contains id, name, and status")
    void executionToString() {
        DmWorkflowExecution exec = validExecutionBuilder().id("wf-1").name("My Workflow").build();
        String str = exec.toString();
        assertThat(str).contains("wf-1").contains("My Workflow").contains("PENDING");
    }

    @Test
    @DisplayName("builder rejects blank id")
    void executionBuilderRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validExecutionBuilder().id("").build());
    }

    @Test
    @DisplayName("builder rejects null steps")
    void executionBuilderRejectsNullSteps() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validExecutionBuilder().steps(null).build());
    }

    @Test
    @DisplayName("fail from PAUSED state is allowed")
    void executionFailFromPaused() {
        DmWorkflowExecution paused = validExecutionBuilder().build().start().pause();
        DmWorkflowExecution failed = paused.fail("paused-timeout");
        assertThat(failed.getStatus()).isEqualTo(DmWorkflowStatus.FAILED);
    }
}
