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
@DisplayName("ScanStatus Enum Tests [GH-90000]")
class ScanStatusTest {

    @Nested
    @DisplayName("Display Name Tests [GH-90000]")
    class DisplayNameTests {

        @Test
        @DisplayName("PENDING has correct display name [GH-90000]")
        void pendingHasCorrectDisplayName() { // GH-90000
            assertThat(ScanStatus.PENDING.getDisplayName()).isEqualTo("Pending [GH-90000]");
        }

        @Test
        @DisplayName("RUNNING has correct display name [GH-90000]")
        void runningHasCorrectDisplayName() { // GH-90000
            assertThat(ScanStatus.RUNNING.getDisplayName()).isEqualTo("Running [GH-90000]");
        }

        @Test
        @DisplayName("COMPLETED has correct display name [GH-90000]")
        void completedHasCorrectDisplayName() { // GH-90000
            assertThat(ScanStatus.COMPLETED.getDisplayName()).isEqualTo("Completed [GH-90000]");
        }

        @Test
        @DisplayName("FAILED has correct display name [GH-90000]")
        void failedHasCorrectDisplayName() { // GH-90000
            assertThat(ScanStatus.FAILED.getDisplayName()).isEqualTo("Failed [GH-90000]");
        }

        @Test
        @DisplayName("CANCELLED has correct display name [GH-90000]")
        void cancelledHasCorrectDisplayName() { // GH-90000
            assertThat(ScanStatus.CANCELLED.getDisplayName()).isEqualTo("Cancelled [GH-90000]");
        }

        @ParameterizedTest
        @EnumSource(ScanStatus.class) // GH-90000
        @DisplayName("all statuses have non-null display names [GH-90000]")
        void allStatusesHaveNonNullDisplayNames(ScanStatus status) { // GH-90000
            assertThat(status.getDisplayName()) // GH-90000
                    .as("Display name for %s", status.name()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Terminal State Tests [GH-90000]")
    class TerminalStateTests {

        @Test
        @DisplayName("PENDING is not a terminal state [GH-90000]")
        void pendingIsNotTerminal() { // GH-90000
            assertThat(ScanStatus.PENDING.isTerminal()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("RUNNING is not a terminal state [GH-90000]")
        void runningIsNotTerminal() { // GH-90000
            assertThat(ScanStatus.RUNNING.isTerminal()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("COMPLETED is a terminal state [GH-90000]")
        void completedIsTerminal() { // GH-90000
            assertThat(ScanStatus.COMPLETED.isTerminal()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("FAILED is a terminal state [GH-90000]")
        void failedIsTerminal() { // GH-90000
            assertThat(ScanStatus.FAILED.isTerminal()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("CANCELLED is a terminal state [GH-90000]")
        void cancelledIsTerminal() { // GH-90000
            assertThat(ScanStatus.CANCELLED.isTerminal()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("exactly two non-terminal states exist [GH-90000]")
        void exactlyTwoNonTerminalStatesExist() { // GH-90000
            long activeCount = java.util.stream.Stream.of(ScanStatus.values()) // GH-90000
                    .filter(s -> !s.isTerminal()) // GH-90000
                    .count(); // GH-90000
            assertThat(activeCount).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("exactly three terminal states exist [GH-90000]")
        void exactlyThreeTerminalStatesExist() { // GH-90000
            long terminalCount = java.util.stream.Stream.of(ScanStatus.values()) // GH-90000
                    .filter(ScanStatus::isTerminal) // GH-90000
                    .count(); // GH-90000
            assertThat(terminalCount).isEqualTo(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("isActive() Tests [GH-90000]")
    class IsActiveTests {

        @Test
        @DisplayName("PENDING is active [GH-90000]")
        void pendingIsActive() { // GH-90000
            assertThat(ScanStatus.PENDING.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("RUNNING is active [GH-90000]")
        void runningIsActive() { // GH-90000
            assertThat(ScanStatus.RUNNING.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("COMPLETED is not active [GH-90000]")
        void completedIsNotActive() { // GH-90000
            assertThat(ScanStatus.COMPLETED.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("FAILED is not active [GH-90000]")
        void failedIsNotActive() { // GH-90000
            assertThat(ScanStatus.FAILED.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("CANCELLED is not active [GH-90000]")
        void cancelledIsNotActive() { // GH-90000
            assertThat(ScanStatus.CANCELLED.isActive()).isFalse(); // GH-90000
        }

        @ParameterizedTest
        @EnumSource(ScanStatus.class) // GH-90000
        @DisplayName("isActive() is inverse of isTerminal() [GH-90000]")
        void isActiveIsInverseOfIsTerminal(ScanStatus status) { // GH-90000
            assertThat(status.isActive()).isEqualTo(!status.isTerminal()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Enum Value Tests [GH-90000]")
    class EnumValueTests {

        @Test
        @DisplayName("enum has expected number of values [GH-90000]")
        void enumHasExpectedNumberOfValues() { // GH-90000
            assertThat(ScanStatus.values()).hasSize(5); // GH-90000
        }

        @Test
        @DisplayName("enum values can be retrieved by name [GH-90000]")
        void enumValuesCanBeRetrievedByName() { // GH-90000
            assertThat(ScanStatus.valueOf("PENDING [GH-90000]")).isEqualTo(ScanStatus.PENDING);
            assertThat(ScanStatus.valueOf("RUNNING [GH-90000]")).isEqualTo(ScanStatus.RUNNING);
            assertThat(ScanStatus.valueOf("COMPLETED [GH-90000]")).isEqualTo(ScanStatus.COMPLETED);
            assertThat(ScanStatus.valueOf("FAILED [GH-90000]")).isEqualTo(ScanStatus.FAILED);
            assertThat(ScanStatus.valueOf("CANCELLED [GH-90000]")).isEqualTo(ScanStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("State Machine Consistency Tests [GH-90000]")
    class StateMachineTests {

        @Test
        @DisplayName("valid state transitions follow expected pattern [GH-90000]")
        void validStateTransitions() { // GH-90000
            // PENDING -> RUNNING (valid initial transition) // GH-90000
            assertThat(ScanStatus.PENDING.isActive()).isTrue(); // GH-90000
            assertThat(ScanStatus.RUNNING.isActive()).isTrue(); // GH-90000

            // Terminal states cannot transition further
            assertThat(ScanStatus.COMPLETED.isTerminal()).isTrue(); // GH-90000
            assertThat(ScanStatus.FAILED.isTerminal()).isTrue(); // GH-90000
            assertThat(ScanStatus.CANCELLED.isTerminal()).isTrue(); // GH-90000
        }
    }
}
