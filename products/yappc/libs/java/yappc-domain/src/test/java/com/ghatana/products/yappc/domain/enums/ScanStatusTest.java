package com.ghatana.products.yappc.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScanStatus} enum.
 *
 * @doc.type class
 * @doc.purpose Validates ScanStatus enum values, terminal states, and display names
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ScanStatus Enum Tests")
class ScanStatusTest {

    @Nested
    @DisplayName("Display Name Tests")
    class DisplayNameTests {

        @Test
        @DisplayName("PENDING has correct display name")
        void pendingHasCorrectDisplayName() {
            assertThat(ScanStatus.PENDING.getDisplayName()).isEqualTo("Pending");
        }

        @Test
        @DisplayName("RUNNING has correct display name")
        void runningHasCorrectDisplayName() {
            assertThat(ScanStatus.RUNNING.getDisplayName()).isEqualTo("Running");
        }

        @Test
        @DisplayName("COMPLETED has correct display name")
        void completedHasCorrectDisplayName() {
            assertThat(ScanStatus.COMPLETED.getDisplayName()).isEqualTo("Completed");
        }

        @Test
        @DisplayName("FAILED has correct display name")
        void failedHasCorrectDisplayName() {
            assertThat(ScanStatus.FAILED.getDisplayName()).isEqualTo("Failed");
        }

        @Test
        @DisplayName("CANCELLED has correct display name")
        void cancelledHasCorrectDisplayName() {
            assertThat(ScanStatus.CANCELLED.getDisplayName()).isEqualTo("Cancelled");
        }

        @ParameterizedTest
        @EnumSource(ScanStatus.class)
        @DisplayName("all statuses have non-null display names")
        void allStatusesHaveNonNullDisplayNames(ScanStatus status) {
            assertThat(status.getDisplayName())
                    .as("Display name for %s", status.name())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Terminal State Tests")
    class TerminalStateTests {

        @Test
        @DisplayName("PENDING is not a terminal state")
        void pendingIsNotTerminal() {
            assertThat(ScanStatus.PENDING.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("RUNNING is not a terminal state")
        void runningIsNotTerminal() {
            assertThat(ScanStatus.RUNNING.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("COMPLETED is a terminal state")
        void completedIsTerminal() {
            assertThat(ScanStatus.COMPLETED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("FAILED is a terminal state")
        void failedIsTerminal() {
            assertThat(ScanStatus.FAILED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("CANCELLED is a terminal state")
        void cancelledIsTerminal() {
            assertThat(ScanStatus.CANCELLED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("exactly two non-terminal states exist")
        void exactlyTwoNonTerminalStatesExist() {
            long activeCount = java.util.stream.Stream.of(ScanStatus.values())
                    .filter(s -> !s.isTerminal())
                    .count();
            assertThat(activeCount).isEqualTo(2);
        }

        @Test
        @DisplayName("exactly three terminal states exist")
        void exactlyThreeTerminalStatesExist() {
            long terminalCount = java.util.stream.Stream.of(ScanStatus.values())
                    .filter(ScanStatus::isTerminal)
                    .count();
            assertThat(terminalCount).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("isActive() Tests")
    class IsActiveTests {

        @Test
        @DisplayName("PENDING is active")
        void pendingIsActive() {
            assertThat(ScanStatus.PENDING.isActive()).isTrue();
        }

        @Test
        @DisplayName("RUNNING is active")
        void runningIsActive() {
            assertThat(ScanStatus.RUNNING.isActive()).isTrue();
        }

        @Test
        @DisplayName("COMPLETED is not active")
        void completedIsNotActive() {
            assertThat(ScanStatus.COMPLETED.isActive()).isFalse();
        }

        @Test
        @DisplayName("FAILED is not active")
        void failedIsNotActive() {
            assertThat(ScanStatus.FAILED.isActive()).isFalse();
        }

        @Test
        @DisplayName("CANCELLED is not active")
        void cancelledIsNotActive() {
            assertThat(ScanStatus.CANCELLED.isActive()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(ScanStatus.class)
        @DisplayName("isActive() is inverse of isTerminal()")
        void isActiveIsInverseOfIsTerminal(ScanStatus status) {
            assertThat(status.isActive()).isEqualTo(!status.isTerminal());
        }
    }

    @Nested
    @DisplayName("Enum Value Tests")
    class EnumValueTests {

        @Test
        @DisplayName("enum has expected number of values")
        void enumHasExpectedNumberOfValues() {
            assertThat(ScanStatus.values()).hasSize(5);
        }

        @Test
        @DisplayName("enum values can be retrieved by name")
        void enumValuesCanBeRetrievedByName() {
            assertThat(ScanStatus.valueOf("PENDING")).isEqualTo(ScanStatus.PENDING);
            assertThat(ScanStatus.valueOf("RUNNING")).isEqualTo(ScanStatus.RUNNING);
            assertThat(ScanStatus.valueOf("COMPLETED")).isEqualTo(ScanStatus.COMPLETED);
            assertThat(ScanStatus.valueOf("FAILED")).isEqualTo(ScanStatus.FAILED);
            assertThat(ScanStatus.valueOf("CANCELLED")).isEqualTo(ScanStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("State Machine Consistency Tests")
    class StateMachineTests {

        @Test
        @DisplayName("valid state transitions follow expected pattern")
        void validStateTransitions() {
            // PENDING -> RUNNING (valid initial transition)
            assertThat(ScanStatus.PENDING.isActive()).isTrue();
            assertThat(ScanStatus.RUNNING.isActive()).isTrue();

            // Terminal states cannot transition further
            assertThat(ScanStatus.COMPLETED.isTerminal()).isTrue();
            assertThat(ScanStatus.FAILED.isTerminal()).isTrue();
            assertThat(ScanStatus.CANCELLED.isTerminal()).isTrue();
        }
    }
}
