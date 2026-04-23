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
 * and the {@code of()} factory. // GH-90000
 */
@DisplayName("CompletionResult")
class CompletionResultTest {

    @Nested
    @DisplayName("of() factory")
    class OfFactory {

        @Test
        @DisplayName("of(text) creates result with 'stop' finishReason")
        void ofCreatesSimpleResult() { // GH-90000
            CompletionResult result = CompletionResult.of("Hello world");
            assertThat(result.getText()).isEqualTo("Hello world");
            assertThat(result.getFinishReason()).isEqualTo("stop");
            assertThat(result.hasToolCalls()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("of(null) defaults text to empty string")
        void ofNullText() { // GH-90000
            CompletionResult result = CompletionResult.of(null); // GH-90000
            assertThat(result.getText()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("convenience methods")
    class ConvenienceMethods {

        @Test
        @DisplayName("text() is alias for getText()")
        void textAlias() { // GH-90000
            CompletionResult result = CompletionResult.of("response");
            assertThat(result.text()).isEqualTo(result.getText()); // GH-90000
        }

        @Test
        @DisplayName("model() is alias for getModelUsed()")
        void modelAlias() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("hi")
                    .modelUsed("gpt-4")
                    .build(); // GH-90000
            assertThat(result.model()).isEqualTo("gpt-4");
            assertThat(result.model()).isEqualTo(result.getModelUsed()); // GH-90000
        }
    }

    @Nested
    @DisplayName("tool call detection")
    class ToolCallDetection {

        @Test
        @DisplayName("hasToolCalls returns false when no tool calls")
        void noToolCalls() { // GH-90000
            CompletionResult result = CompletionResult.of("text");
            assertThat(result.hasToolCalls()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasToolCalls returns true when tool calls present")
        void withToolCalls() { // GH-90000
            ToolCall call = ToolCall.of("search", Map.of("q", "test")); // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("")
                    .toolCalls(List.of(call)) // GH-90000
                    .finishReason("tool_calls")
                    .build(); // GH-90000
            assertThat(result.hasToolCalls()).isTrue(); // GH-90000
            assertThat(result.getToolCalls()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish for 'tool_calls' reason")
        void toolCallsFinish() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("")
                    .finishReason("tool_calls")
                    .build(); // GH-90000
            assertThat(result.isToolUseFinish()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish for 'function_call' reason")
        void functionCallFinish() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("")
                    .finishReason("function_call")
                    .build(); // GH-90000
            assertThat(result.isToolUseFinish()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish false for 'stop' reason")
        void stopFinish() { // GH-90000
            CompletionResult result = CompletionResult.of("text");
            assertThat(result.isToolUseFinish()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish false for null reason")
        void nullFinish() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("text")
                    .build(); // GH-90000
            assertThat(result.isToolUseFinish()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder fields")
    class BuilderFields {

        @Test
        @DisplayName("all token fields set correctly")
        void tokenFields() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("output")
                    .tokensUsed(150) // GH-90000
                    .promptTokens(50) // GH-90000
                    .completionTokens(100) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getTokensUsed()).isEqualTo(150); // GH-90000
            assertThat(result.getPromptTokens()).isEqualTo(50); // GH-90000
            assertThat(result.getCompletionTokens()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("latencyMs set correctly")
        void latencyMs() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("output")
                    .latencyMs(250L) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getLatencyMs()).isEqualTo(250L); // GH-90000
        }

        @Test
        @DisplayName("metadata defaults to empty map")
        void metadataDefaults() { // GH-90000
            CompletionResult result = CompletionResult.of("text");
            assertThat(result.getMetadata()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("metadata is set correctly")
        void metadataSet() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("text")
                    .metadata(Map.of("provider", "openai")) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getMetadata()).containsEntry("provider", "openai"); // GH-90000
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("toolCalls list is immutable")
        void toolCallsImmutable() { // GH-90000
            ToolCall call = ToolCall.of("search", Map.of()); // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("")
                    .toolCalls(List.of(call)) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getToolCalls()).isUnmodifiable(); // GH-90000
        }

        @Test
        @DisplayName("metadata map is immutable")
        void metadataImmutable() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("")
                    .metadata(Map.of("k", "v")) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getMetadata()).isUnmodifiable(); // GH-90000
        }

        @Test
        @DisplayName("null text defaults to empty string")
        void nullTextDefaults() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text(null) // GH-90000
                    .finishReason("stop")
                    .build(); // GH-90000
            assertThat(result.getText()).isEmpty(); // GH-90000
        }
    }
}
