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
        void successResult() {
            ToolCallResult result = ToolCallResult.success("call-1", "search", "{\"count\":5}");
            assertThat(result.getToolCallId()).isEqualTo("call-1");
            assertThat(result.getToolName()).isEqualTo("search");
            assertThat(result.getResult()).isEqualTo("{\"count\":5}");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("failure() creates a failed result")
        void failureResult() {
            ToolCallResult result = ToolCallResult.failure("call-2", "api_call", "Connection timeout");
            assertThat(result.getToolCallId()).isEqualTo("call-2");
            assertThat(result.getToolName()).isEqualTo("api_call");
            assertThat(result.getResult()).isEqualTo("Connection timeout");
            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null toolCallId throws NullPointerException")
        void nullToolCallId() {
            assertThatThrownBy(() -> ToolCallResult.success(null, "tool", "result"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("toolCallId");
        }

        @Test
        @DisplayName("null toolName throws NullPointerException")
        void nullToolName() {
            assertThatThrownBy(() -> ToolCallResult.success("id", null, "result"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("toolName");
        }

        @Test
        @DisplayName("null result throws NullPointerException")
        void nullResult() {
            assertThatThrownBy(() -> ToolCallResult.success("id", "tool", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("result");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("short result is not truncated")
        void shortResult() {
            ToolCallResult result = ToolCallResult.success("c1", "tool", "short");
            assertThat(result.toString()).contains("short").doesNotContain("...");
        }

        @Test
        @DisplayName("long result is truncated to 100 chars")
        void longResultTruncated() {
            String longText = "x".repeat(200);
            ToolCallResult result = ToolCallResult.success("c1", "tool", longText);
            String str = result.toString();
            assertThat(str).contains("...");
            // Should not contain the full 200-char string
            assertThat(str).doesNotContain(longText);
        }

        @Test
        @DisplayName("toString includes success flag")
        void includesSuccessFlag() {
            ToolCallResult success = ToolCallResult.success("c1", "tool", "ok");
            assertThat(success.toString()).contains("success=true");

            ToolCallResult failure = ToolCallResult.failure("c2", "tool", "err");
            assertThat(failure.toString()).contains("success=false");
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal results are equal")
        void equalResults() {
            ToolCallResult r1 = ToolCallResult.success("c1", "tool", "data");
            ToolCallResult r2 = ToolCallResult.success("c1", "tool", "data");
            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("success vs failure not equal")
        void successVsFailure() {
            ToolCallResult r1 = ToolCallResult.success("c1", "tool", "data");
            ToolCallResult r2 = ToolCallResult.failure("c1", "tool", "data");
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("different toolCallId not equal")
        void differentIds() {
            ToolCallResult r1 = ToolCallResult.success("c1", "tool", "data");
            ToolCallResult r2 = ToolCallResult.success("c2", "tool", "data");
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("equal to itself")
        void equalToItself() {
            ToolCallResult r = ToolCallResult.success("c1", "tool", "data");
            assertThat(r).isEqualTo(r);
        }

        @Test
        @DisplayName("not equal to null or different type")
        void notEqualToNullOrDifferentType() {
            ToolCallResult r = ToolCallResult.success("c1", "tool", "data");
            assertThat(r).isNotEqualTo(null);
            assertThat(r).isNotEqualTo("string");
        }
    }
}
