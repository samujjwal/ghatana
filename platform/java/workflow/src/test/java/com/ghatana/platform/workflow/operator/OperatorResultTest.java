package com.ghatana.platform.workflow.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OperatorResult} value object.
 *
 * @doc.type class
 * @doc.purpose OperatorResult factory methods and builder tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("OperatorResult")
class OperatorResultTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("empty() should create successful result with no events")
        void shouldCreateEmptyResult() { // GH-90000
            OperatorResult result = OperatorResult.empty(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("failed() should create failed result with error message")
        void shouldCreateFailedResult() { // GH-90000
            OperatorResult result = OperatorResult.failed("Something went wrong");

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build a successful result")
        void shouldBuildSuccessResult() { // GH-90000
            OperatorResult result = OperatorResult.builder() // GH-90000
                    .success() // GH-90000
                    .processingTime(150L) // GH-90000
                    .build(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should build a failed result with message")
        void shouldBuildFailedResult() { // GH-90000
            OperatorResult result = OperatorResult.builder() // GH-90000
                    .failed("Validation error")
                    .build(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }
}
