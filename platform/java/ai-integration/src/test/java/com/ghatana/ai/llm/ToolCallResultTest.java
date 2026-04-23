package com.ghatana.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ToolCallResult}.
 *
 * Covers success/failure factories, null validation, toString truncation, and equality.
 */
@DisplayName("ToolCallResult")
class ToolCallResultTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("success() creates a successful result")
        void successResult() { // GH-90000
            ToolCallResult result = ToolCallResult.success("call-1", "search", "{\"count\":5}"); // GH-90000
            assertThat(result.getToolCallId()).isEqualTo("call-1");
            assertThat(result.getToolName()).isEqualTo("search");
            assertThat(result.getResult()).isEqualTo("{\"count\":5}"); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("failure() creates a failed result")
        void failureResult() { // GH-90000
            ToolCallResult result = ToolCallResult.failure("call-2", "api_call", "Connection timeout"); // GH-90000
            assertThat(result.getToolCallId()).isEqualTo("call-2");
            assertThat(result.getToolName()).isEqualTo("api_call");
            assertThat(result.getResult()).isEqualTo("Connection timeout");
            assertThat(result.isSuccess()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null toolCallId throws NullPointerException")
        void nullToolCallId() { // GH-90000
            assertThatThrownBy(() -> ToolCallResult.success(null, "tool", "result")) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("toolCallId");
        }

        @Test
        @DisplayName("null toolName throws NullPointerException")
        void nullToolName() { // GH-90000
            assertThatThrownBy(() -> ToolCallResult.success("id", null, "result")) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("toolName");
        }

        @Test
        @DisplayName("null result throws NullPointerException")
        void nullResult() { // GH-90000
            assertThatThrownBy(() -> ToolCallResult.success("id", "tool", null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("result");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("short result is not truncated")
        void shortResult() { // GH-90000
            ToolCallResult result = ToolCallResult.success("c1", "tool", "short"); // GH-90000
            assertThat(result.toString()).contains("short").doesNotContain("...");
        }

        @Test
        @DisplayName("long result is truncated to 100 chars")
        void longResultTruncated() { // GH-90000
            String longText = "x".repeat(200); // GH-90000
            ToolCallResult result = ToolCallResult.success("c1", "tool", longText); // GH-90000
            String str = result.toString(); // GH-90000
            assertThat(str).contains("...");
            // Should not contain the full 200-char string
            assertThat(str).doesNotContain(longText); // GH-90000
        }

        @Test
        @DisplayName("toString includes success flag")
        void includesSuccessFlag() { // GH-90000
            ToolCallResult success = ToolCallResult.success("c1", "tool", "ok"); // GH-90000
            assertThat(success.toString()).contains("success=true");

            ToolCallResult failure = ToolCallResult.failure("c2", "tool", "err"); // GH-90000
            assertThat(failure.toString()).contains("success=false");
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal results are equal")
        void equalResults() { // GH-90000
            ToolCallResult r1 = ToolCallResult.success("c1", "tool", "data"); // GH-90000
            ToolCallResult r2 = ToolCallResult.success("c1", "tool", "data"); // GH-90000
            assertThat(r1).isEqualTo(r2); // GH-90000
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("success vs failure not equal")
        void successVsFailure() { // GH-90000
            ToolCallResult r1 = ToolCallResult.success("c1", "tool", "data"); // GH-90000
            ToolCallResult r2 = ToolCallResult.failure("c1", "tool", "data"); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("different toolCallId not equal")
        void differentIds() { // GH-90000
            ToolCallResult r1 = ToolCallResult.success("c1", "tool", "data"); // GH-90000
            ToolCallResult r2 = ToolCallResult.success("c2", "tool", "data"); // GH-90000
            assertThat(r1).isNotEqualTo(r2); // GH-90000
        }

        @Test
        @DisplayName("equal to itself")
        void equalToItself() { // GH-90000
            ToolCallResult r = ToolCallResult.success("c1", "tool", "data"); // GH-90000
            assertThat(r).isEqualTo(r); // GH-90000
        }

        @Test
        @DisplayName("not equal to null or different type")
        void notEqualToNullOrDifferentType() { // GH-90000
            ToolCallResult r = ToolCallResult.success("c1", "tool", "data"); // GH-90000
            assertThat(r).isNotEqualTo(null); // GH-90000
            assertThat(r).isNotEqualTo("string");
        }
    }
}
