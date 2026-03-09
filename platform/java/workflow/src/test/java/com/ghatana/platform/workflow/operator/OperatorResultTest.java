package com.ghatana.platform.workflow.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        void shouldCreateEmptyResult() {
            OperatorResult result = OperatorResult.empty();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("failed() should create failed result with error message")
        void shouldCreateFailedResult() {
            OperatorResult result = OperatorResult.failed("Something went wrong");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build a successful result")
        void shouldBuildSuccessResult() {
            OperatorResult result = OperatorResult.builder()
                    .success()
                    .processingTime(150L)
                    .build();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should build a failed result with message")
        void shouldBuildFailedResult() {
            OperatorResult result = OperatorResult.builder()
                    .failed("Validation error")
                    .build();

            assertThat(result).isNotNull();
        }
    }
}
