package com.ghatana.yappc.domain.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Verifies lifecycle behaviour helpers on all canonical status enums
 * @doc.layer domain
 * @doc.pattern Unit Test
 */
@DisplayName("Canonical Status Enums")
class StatusEnumsTest {

    @Nested
    @DisplayName("WorkflowStatus")
    class WorkflowStatusTests {

        @Test
        void terminalStatesAreCorrect() {
            assertThat(WorkflowStatus.COMPLETED.isTerminal()).isTrue();
            assertThat(WorkflowStatus.FAILED.isTerminal()).isTrue();
            assertThat(WorkflowStatus.CANCELLED.isTerminal()).isTrue();
        }

        @Test
        void nonTerminalStatesReturnFalse() {
            assertThat(WorkflowStatus.DRAFT.isTerminal()).isFalse();
            assertThat(WorkflowStatus.IN_PROGRESS.isTerminal()).isFalse();
            assertThat(WorkflowStatus.PAUSED.isTerminal()).isFalse();
        }

        @Test
        void activeStatesAreCorrect() {
            assertThat(WorkflowStatus.IN_PROGRESS.isActive()).isTrue();
            assertThat(WorkflowStatus.AWAITING_REVIEW.isActive()).isTrue();
        }

        @Test
        void inactiveStatesReturnFalseForActive() {
            assertThat(WorkflowStatus.DRAFT.isActive()).isFalse();
            assertThat(WorkflowStatus.COMPLETED.isActive()).isFalse();
        }

        @Test
        void implementsLifecycle() {
            assertThat(WorkflowStatus.IN_PROGRESS).isInstanceOf(Lifecycle.class);
        }
    }

    @Nested
    @DisplayName("RequirementStatus")
    class RequirementStatusTests {

        @Test
        void terminalStatesAreCorrect() {
            assertThat(RequirementStatus.VERIFIED.isTerminal()).isTrue();
            assertThat(RequirementStatus.DEPRECATED.isTerminal()).isTrue();
        }

        @Test
        void pendingStatesAreCorrect() {
            assertThat(RequirementStatus.PENDING_REVIEW.isPending()).isTrue();
            assertThat(RequirementStatus.IN_REVIEW.isPending()).isTrue();
        }

        @Test
        void nonPendingStatesReturnFalse() {
            assertThat(RequirementStatus.DRAFT.isPending()).isFalse();
            assertThat(RequirementStatus.APPROVED.isPending()).isFalse();
        }

        @Test
        void implementsLifecycle() {
            assertThat(RequirementStatus.DRAFT).isInstanceOf(Lifecycle.class);
        }
    }

    @Nested
    @DisplayName("PlanStatus")
    class PlanStatusTests {

        @Test
        void terminalStatesAreCorrect() {
            assertThat(PlanStatus.EXECUTED.isTerminal()).isTrue();
            assertThat(PlanStatus.FAILED.isTerminal()).isTrue();
        }

        @Test
        void awaitingHumanStatesAreCorrect() {
            assertThat(PlanStatus.PENDING_REVIEW.awaitingHuman()).isTrue();
            assertThat(PlanStatus.MODIFIED.awaitingHuman()).isTrue();
        }

        @Test
        void nonAwaitingHumanStatesReturnFalse() {
            assertThat(PlanStatus.GENERATING.awaitingHuman()).isFalse();
            assertThat(PlanStatus.APPROVED.awaitingHuman()).isFalse();
        }

        @Test
        void implementsLifecycle() {
            assertThat(PlanStatus.APPROVED).isInstanceOf(Lifecycle.class);
        }
    }

    @Nested
    @DisplayName("RunStatus")
    class RunStatusTests {

        @Test
        void terminalStatesAreCorrect() {
            assertThat(RunStatus.SUCCESS.isTerminal()).isTrue();
            assertThat(RunStatus.FAILED.isTerminal()).isTrue();
            assertThat(RunStatus.CANCELLED.isTerminal()).isTrue();
        }

        @Test
        void activeStatesAreCorrect() {
            assertThat(RunStatus.QUEUED.isActive()).isTrue();
            assertThat(RunStatus.RUNNING.isActive()).isTrue();
        }

        @Test
        void nonActiveStatesReturnFalse() {
            assertThat(RunStatus.NOT_READY.isActive()).isFalse();
            assertThat(RunStatus.SUCCESS.isActive()).isFalse();
        }

        @Test
        void implementsLifecycle() {
            assertThat(RunStatus.RUNNING).isInstanceOf(Lifecycle.class);
        }
    }
}
