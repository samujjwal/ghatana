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
@DisplayName("CompletionResult [GH-90000]")
class CompletionResultTest {

    @Nested
    @DisplayName("of() factory [GH-90000]")
    class OfFactory {

        @Test
        @DisplayName("of(text) creates result with 'stop' finishReason [GH-90000]")
        void ofCreatesSimpleResult() { // GH-90000
            CompletionResult result = CompletionResult.of("Hello world [GH-90000]");
            assertThat(result.getText()).isEqualTo("Hello world [GH-90000]");
            assertThat(result.getFinishReason()).isEqualTo("stop [GH-90000]");
            assertThat(result.hasToolCalls()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("of(null) defaults text to empty string [GH-90000]")
        void ofNullText() { // GH-90000
            CompletionResult result = CompletionResult.of(null); // GH-90000
            assertThat(result.getText()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("convenience methods [GH-90000]")
    class ConvenienceMethods {

        @Test
        @DisplayName("text() is alias for getText() [GH-90000]")
        void textAlias() { // GH-90000
            CompletionResult result = CompletionResult.of("response [GH-90000]");
            assertThat(result.text()).isEqualTo(result.getText()); // GH-90000
        }

        @Test
        @DisplayName("model() is alias for getModelUsed() [GH-90000]")
        void modelAlias() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("hi [GH-90000]")
                    .modelUsed("gpt-4 [GH-90000]")
                    .build(); // GH-90000
            assertThat(result.model()).isEqualTo("gpt-4 [GH-90000]");
            assertThat(result.model()).isEqualTo(result.getModelUsed()); // GH-90000
        }
    }

    @Nested
    @DisplayName("tool call detection [GH-90000]")
    class ToolCallDetection {

        @Test
        @DisplayName("hasToolCalls returns false when no tool calls [GH-90000]")
        void noToolCalls() { // GH-90000
            CompletionResult result = CompletionResult.of("text [GH-90000]");
            assertThat(result.hasToolCalls()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasToolCalls returns true when tool calls present [GH-90000]")
        void withToolCalls() { // GH-90000
            ToolCall call = ToolCall.of("search", Map.of("q", "test")); // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text(" [GH-90000]")
                    .toolCalls(List.of(call)) // GH-90000
                    .finishReason("tool_calls [GH-90000]")
                    .build(); // GH-90000
            assertThat(result.hasToolCalls()).isTrue(); // GH-90000
            assertThat(result.getToolCalls()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish for 'tool_calls' reason [GH-90000]")
        void toolCallsFinish() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text(" [GH-90000]")
                    .finishReason("tool_calls [GH-90000]")
                    .build(); // GH-90000
            assertThat(result.isToolUseFinish()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish for 'function_call' reason [GH-90000]")
        void functionCallFinish() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text(" [GH-90000]")
                    .finishReason("function_call [GH-90000]")
                    .build(); // GH-90000
            assertThat(result.isToolUseFinish()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish false for 'stop' reason [GH-90000]")
        void stopFinish() { // GH-90000
            CompletionResult result = CompletionResult.of("text [GH-90000]");
            assertThat(result.isToolUseFinish()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isToolUseFinish false for null reason [GH-90000]")
        void nullFinish() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("text [GH-90000]")
                    .build(); // GH-90000
            assertThat(result.isToolUseFinish()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder fields [GH-90000]")
    class BuilderFields {

        @Test
        @DisplayName("all token fields set correctly [GH-90000]")
        void tokenFields() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("output [GH-90000]")
                    .tokensUsed(150) // GH-90000
                    .promptTokens(50) // GH-90000
                    .completionTokens(100) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getTokensUsed()).isEqualTo(150); // GH-90000
            assertThat(result.getPromptTokens()).isEqualTo(50); // GH-90000
            assertThat(result.getCompletionTokens()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("latencyMs set correctly [GH-90000]")
        void latencyMs() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("output [GH-90000]")
                    .latencyMs(250L) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getLatencyMs()).isEqualTo(250L); // GH-90000
        }

        @Test
        @DisplayName("metadata defaults to empty map [GH-90000]")
        void metadataDefaults() { // GH-90000
            CompletionResult result = CompletionResult.of("text [GH-90000]");
            assertThat(result.getMetadata()).isNotNull().isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("metadata is set correctly [GH-90000]")
        void metadataSet() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text("text [GH-90000]")
                    .metadata(Map.of("provider", "openai")) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getMetadata()).containsEntry("provider", "openai"); // GH-90000
        }
    }

    @Nested
    @DisplayName("immutability [GH-90000]")
    class Immutability {

        @Test
        @DisplayName("toolCalls list is immutable [GH-90000]")
        void toolCallsImmutable() { // GH-90000
            ToolCall call = ToolCall.of("search", Map.of()); // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text(" [GH-90000]")
                    .toolCalls(List.of(call)) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getToolCalls()).isUnmodifiable(); // GH-90000
        }

        @Test
        @DisplayName("metadata map is immutable [GH-90000]")
        void metadataImmutable() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text(" [GH-90000]")
                    .metadata(Map.of("k", "v")) // GH-90000
                    .build(); // GH-90000
            assertThat(result.getMetadata()).isUnmodifiable(); // GH-90000
        }

        @Test
        @DisplayName("null text defaults to empty string [GH-90000]")
        void nullTextDefaults() { // GH-90000
            CompletionResult result = CompletionResult.builder() // GH-90000
                    .text(null) // GH-90000
                    .finishReason("stop [GH-90000]")
                    .build(); // GH-90000
            assertThat(result.getText()).isEmpty(); // GH-90000
        }
    }
}
