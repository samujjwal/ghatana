package com.ghatana.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CompletionRequest}.
 *
 * Covers builder validation, defaults, immutability, and edge cases.
 */
@DisplayName("CompletionRequest")
class CompletionRequestTest {

    @Nested
    @DisplayName("builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("requires either prompt or messages")
        void requirePromptOrMessages() {
            assertThatThrownBy(() -> CompletionRequest.builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prompt or messages");
        }

        @Test
        @DisplayName("prompt alone is valid")
        void promptAloneValid() {
            CompletionRequest req = CompletionRequest.builder()
                    .prompt("Hello")
                    .build();
            assertThat(req.getPrompt()).isEqualTo("Hello");
            assertThat(req.getMessages()).isEmpty();
        }

        @Test
        @DisplayName("messages alone are valid")
        void messagesAloneValid() {
            CompletionRequest req = CompletionRequest.builder()
                    .messages(List.of(ChatMessage.user("Hi")))
                    .build();
            assertThat(req.getPrompt()).isNull();
            assertThat(req.getMessages()).hasSize(1);
        }

        @Test
        @DisplayName("empty messages list is not valid")
        void emptyMessagesNotValid() {
            assertThatThrownBy(() -> CompletionRequest.builder()
                    .messages(List.of())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("maxTokens defaults to 256")
        void defaultMaxTokens() {
            CompletionRequest req = CompletionRequest.builder().prompt("test").build();
            assertThat(req.getMaxTokens()).isEqualTo(256);
        }

        @Test
        @DisplayName("temperature defaults to 1.0")
        void defaultTemperature() {
            CompletionRequest req = CompletionRequest.builder().prompt("test").build();
            assertThat(req.getTemperature()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("topP defaults to 1.0")
        void defaultTopP() {
            CompletionRequest req = CompletionRequest.builder().prompt("test").build();
            assertThat(req.getTopP()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("frequencyPenalty defaults to 0.0")
        void defaultFrequencyPenalty() {
            CompletionRequest req = CompletionRequest.builder().prompt("test").build();
            assertThat(req.getFrequencyPenalty()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("presencePenalty defaults to 0.0")
        void defaultPresencePenalty() {
            CompletionRequest req = CompletionRequest.builder().prompt("test").build();
            assertThat(req.getPresencePenalty()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("metadata defaults to empty map")
        void defaultMetadata() {
            CompletionRequest req = CompletionRequest.builder().prompt("test").build();
            assertThat(req.getMetadata()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("builder customization")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly")
        void allFieldsSet() {
            CompletionRequest req = CompletionRequest.builder()
                    .prompt("Generate code")
                    .model("gpt-4")
                    .maxTokens(1024)
                    .temperature(0.5)
                    .topP(0.9)
                    .stop(List.of("###"))
                    .stopSequences(List.of("END"))
                    .responseFormat("json_object")
                    .frequencyPenalty(0.2)
                    .presencePenalty(0.3)
                    .metadata(Map.of("user", "test"))
                    .build();

            assertThat(req.getPrompt()).isEqualTo("Generate code");
            assertThat(req.getModel()).isEqualTo("gpt-4");
            assertThat(req.getMaxTokens()).isEqualTo(1024);
            assertThat(req.getTemperature()).isEqualTo(0.5);
            assertThat(req.getTopP()).isEqualTo(0.9);
            assertThat(req.getStop()).containsExactly("###");
            assertThat(req.getStopSequences()).containsExactly("END");
            assertThat(req.getResponseFormat()).isEqualTo("json_object");
            assertThat(req.getFrequencyPenalty()).isEqualTo(0.2);
            assertThat(req.getPresencePenalty()).isEqualTo(0.3);
            assertThat(req.getMetadata()).containsEntry("user", "test");
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("messages list is immutable")
        void messagesImmutable() {
            CompletionRequest req = CompletionRequest.builder()
                    .messages(List.of(ChatMessage.user("Hi")))
                    .build();
            assertThatThrownBy(() -> req.getMessages().add(ChatMessage.user("new")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("metadata map is immutable")
        void metadataImmutable() {
            CompletionRequest req = CompletionRequest.builder()
                    .prompt("test")
                    .metadata(Map.of("k", "v"))
                    .build();
            assertThatThrownBy(() -> req.getMetadata().put("new", "val"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
