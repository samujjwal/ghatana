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
        void shouldHaveAllStates() { // GH-90000
            assertThat(OperatorState.values()) // GH-90000
                    .extracting(OperatorState::name) // GH-90000
                    .containsExactlyInAnyOrder("CREATED", "INITIALIZED", "RUNNING", "STOPPED", "FAILED"); // GH-90000
        }
    }

    @Nested
    @DisplayName("OperatorType")
    class OperatorTypeTests {

        @Test
        @DisplayName("should have all expected types")
        void shouldHaveAllTypes() { // GH-90000
            assertThat(OperatorType.values()) // GH-90000
                    .extracting(OperatorType::name) // GH-90000
                    .containsExactlyInAnyOrder("STREAM", "PATTERN", "LEARNING"); // GH-90000
        }
    }
}
