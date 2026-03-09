package com.ghatana.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CompletionResult}.
 *
 * Covers builder, convenience methods, tool call detection, immutability,
 * and the {@code of()} factory.
 */
@DisplayName("CompletionResult")
class CompletionResultTest {

    @Nested
    @DisplayName("of() factory")
    class OfFactory {

        @Test
        @DisplayName("of(text) creates result with 'stop' finishReason")
        void ofCreatesSimpleResult() {
            CompletionResult result = CompletionResult.of("Hello world");
            assertThat(result.getText()).isEqualTo("Hello world");
            assertThat(result.getFinishReason()).isEqualTo("stop");
            assertThat(result.hasToolCalls()).isFalse();
        }

        @Test
        @DisplayName("of(null) defaults text to empty string")
        void ofNullText() {
            CompletionResult result = CompletionResult.of(null);
            assertThat(result.getText()).isEmpty();
        }
    }

    @Nested
    @DisplayName("convenience methods")
    class ConvenienceMethods {

        @Test
        @DisplayName("text() is alias for getText()")
        void textAlias() {
            CompletionResult result = CompletionResult.of("response");
            assertThat(result.text()).isEqualTo(result.getText());
        }

        @Test
        @DisplayName("model() is alias for getModelUsed()")
        void modelAlias() {
            CompletionResult result = CompletionResult.builder()
                    .text("hi")
                    .modelUsed("gpt-4")
                    .build();
            assertThat(result.model()).isEqualTo("gpt-4");
            assertThat(result.model()).isEqualTo(result.getModelUsed());
        }
    }

    @Nested
    @DisplayName("tool call detection")
    class ToolCallDetection {

        @Test
        @DisplayName("hasToolCalls returns false when no tool calls")
        void noToolCalls() {
            CompletionResult result = CompletionResult.of("text");
            assertThat(result.hasToolCalls()).isFalse();
        }

        @Test
        @DisplayName("hasToolCalls returns true when tool calls present")
        void withToolCalls() {
            ToolCall call = ToolCall.of("search", Map.of("q", "test"));
            CompletionResult result = CompletionResult.builder()
                    .text("")
                    .toolCalls(List.of(call))
                    .finishReason("tool_calls")
                    .build();
            assertThat(result.hasToolCalls()).isTrue();
            assertThat(result.getToolCalls()).hasSize(1);
        }

        @Test
        @DisplayName("isToolUseFinish for 'tool_calls' reason")
        void toolCallsFinish() {
            CompletionResult result = CompletionResult.builder()
                    .text("")
                    .finishReason("tool_calls")
                    .build();
            assertThat(result.isToolUseFinish()).isTrue();
        }

        @Test
        @DisplayName("isToolUseFinish for 'function_call' reason")
        void functionCallFinish() {
            CompletionResult result = CompletionResult.builder()
                    .text("")
                    .finishReason("function_call")
                    .build();
            assertThat(result.isToolUseFinish()).isTrue();
        }

        @Test
        @DisplayName("isToolUseFinish false for 'stop' reason")
        void stopFinish() {
            CompletionResult result = CompletionResult.of("text");
            assertThat(result.isToolUseFinish()).isFalse();
        }

        @Test
        @DisplayName("isToolUseFinish false for null reason")
        void nullFinish() {
            CompletionResult result = CompletionResult.builder()
                    .text("text")
                    .build();
            assertThat(result.isToolUseFinish()).isFalse();
        }
    }

    @Nested
    @DisplayName("builder fields")
    class BuilderFields {

        @Test
        @DisplayName("all token fields set correctly")
        void tokenFields() {
            CompletionResult result = CompletionResult.builder()
                    .text("output")
                    .tokensUsed(150)
                    .promptTokens(50)
                    .completionTokens(100)
                    .build();
            assertThat(result.getTokensUsed()).isEqualTo(150);
            assertThat(result.getPromptTokens()).isEqualTo(50);
            assertThat(result.getCompletionTokens()).isEqualTo(100);
        }

        @Test
        @DisplayName("latencyMs set correctly")
        void latencyMs() {
            CompletionResult result = CompletionResult.builder()
                    .text("output")
                    .latencyMs(250L)
                    .build();
            assertThat(result.getLatencyMs()).isEqualTo(250L);
        }

        @Test
        @DisplayName("metadata defaults to empty map")
        void metadataDefaults() {
            CompletionResult result = CompletionResult.of("text");
            assertThat(result.getMetadata()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("metadata is set correctly")
        void metadataSet() {
            CompletionResult result = CompletionResult.builder()
                    .text("text")
                    .metadata(Map.of("provider", "openai"))
                    .build();
            assertThat(result.getMetadata()).containsEntry("provider", "openai");
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("toolCalls list is immutable")
        void toolCallsImmutable() {
            ToolCall call = ToolCall.of("search", Map.of());
            CompletionResult result = CompletionResult.builder()
                    .text("")
                    .toolCalls(List.of(call))
                    .build();
            assertThat(result.getToolCalls()).isUnmodifiable();
        }

        @Test
        @DisplayName("metadata map is immutable")
        void metadataImmutable() {
            CompletionResult result = CompletionResult.builder()
                    .text("")
                    .metadata(Map.of("k", "v"))
                    .build();
            assertThat(result.getMetadata()).isUnmodifiable();
        }

        @Test
        @DisplayName("null text defaults to empty string")
        void nullTextDefaults() {
            CompletionResult result = CompletionResult.builder()
                    .text(null)
                    .finishReason("stop")
                    .build();
            assertThat(result.getText()).isEmpty();
        }
    }
}
