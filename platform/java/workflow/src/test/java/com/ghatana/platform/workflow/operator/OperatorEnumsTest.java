package com.ghatana.platform.workflow.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OperatorState} and {@link OperatorType} enums.
 *
 * @doc.type class
 * @doc.purpose Operator enum tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("Operator Enums")
class OperatorEnumsTest {

    @Nested
    @DisplayName("OperatorState")
    class OperatorStateTests {

        @Test
        @DisplayName("should have all expected states")
        void shouldHaveAllStates() {
            assertThat(OperatorState.values())
                    .extracting(OperatorState::name)
                    .containsExactlyInAnyOrder("CREATED", "INITIALIZED", "RUNNING", "STOPPED", "FAILED");
        }
    }

    @Nested
    @DisplayName("OperatorType")
    class OperatorTypeTests {

        @Test
        @DisplayName("should have all expected types")
        void shouldHaveAllTypes() {
            assertThat(OperatorType.values())
                    .extracting(OperatorType::name)
                    .containsExactlyInAnyOrder("STREAM", "PATTERN", "LEARNING");
        }
    }
}
