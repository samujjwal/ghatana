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
@DisplayName("CompletionRequest [GH-90000]")
class CompletionRequestTest {

    @Nested
    @DisplayName("builder validation [GH-90000]")
    class BuilderValidation {

        @Test
        @DisplayName("requires either prompt or messages [GH-90000]")
        void requirePromptOrMessages() { // GH-90000
            assertThatThrownBy(() -> CompletionRequest.builder().build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("prompt or messages [GH-90000]");
        }

        @Test
        @DisplayName("prompt alone is valid [GH-90000]")
        void promptAloneValid() { // GH-90000
            CompletionRequest req = CompletionRequest.builder() // GH-90000
                    .prompt("Hello [GH-90000]")
                    .build(); // GH-90000
            assertThat(req.getPrompt()).isEqualTo("Hello [GH-90000]");
            assertThat(req.getMessages()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("messages alone are valid [GH-90000]")
        void messagesAloneValid() { // GH-90000
            CompletionRequest req = CompletionRequest.builder() // GH-90000
                    .messages(List.of(ChatMessage.user("Hi [GH-90000]")))
                    .build(); // GH-90000
            assertThat(req.getPrompt()).isNull(); // GH-90000
            assertThat(req.getMessages()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("empty messages list is not valid [GH-90000]")
        void emptyMessagesNotValid() { // GH-90000
            assertThatThrownBy(() -> CompletionRequest.builder() // GH-90000
                    .messages(List.of()) // GH-90000
                    .build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder defaults [GH-90000]")
    class BuilderDefaults {

        @Test
        @DisplayName("maxTokens defaults to 256 [GH-90000]")
        void defaultMaxTokens() { // GH-90000
            CompletionRequest req = CompletionRequest.builder().prompt("test [GH-90000]").build();
            assertThat(req.getMaxTokens()).isEqualTo(256); // GH-90000
        }

        @Test
        @DisplayName("temperature defaults to 1.0 [GH-90000]")
        void defaultTemperature() { // GH-90000
            CompletionRequest req = CompletionRequest.builder().prompt("test [GH-90000]").build();
            assertThat(req.getTemperature()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("topP defaults to 1.0 [GH-90000]")
        void defaultTopP() { // GH-90000
            CompletionRequest req = CompletionRequest.builder().prompt("test [GH-90000]").build();
            assertThat(req.getTopP()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("frequencyPenalty defaults to 0.0 [GH-90000]")
        void defaultFrequencyPenalty() { // GH-90000
            CompletionRequest req = CompletionRequest.builder().prompt("test [GH-90000]").build();
            assertThat(req.getFrequencyPenalty()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("presencePenalty defaults to 0.0 [GH-90000]")
        void defaultPresencePenalty() { // GH-90000
            CompletionRequest req = CompletionRequest.builder().prompt("test [GH-90000]").build();
            assertThat(req.getPresencePenalty()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("metadata defaults to empty map [GH-90000]")
        void defaultMetadata() { // GH-90000
            CompletionRequest req = CompletionRequest.builder().prompt("test [GH-90000]").build();
            assertThat(req.getMetadata()).isNotNull().isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("builder customization [GH-90000]")
    class BuilderCustomization {

        @Test
        @DisplayName("all fields set correctly [GH-90000]")
        void allFieldsSet() { // GH-90000
            CompletionRequest req = CompletionRequest.builder() // GH-90000
                    .prompt("Generate code [GH-90000]")
                    .model("gpt-4 [GH-90000]")
                    .maxTokens(1024) // GH-90000
                    .temperature(0.5) // GH-90000
                    .topP(0.9) // GH-90000
                    .stop(List.of("### [GH-90000]"))
                    .stopSequences(List.of("END [GH-90000]"))
                    .responseFormat("json_object [GH-90000]")
                    .frequencyPenalty(0.2) // GH-90000
                    .presencePenalty(0.3) // GH-90000
                    .metadata(Map.of("user", "test")) // GH-90000
                    .build(); // GH-90000

            assertThat(req.getPrompt()).isEqualTo("Generate code [GH-90000]");
            assertThat(req.getModel()).isEqualTo("gpt-4 [GH-90000]");
            assertThat(req.getMaxTokens()).isEqualTo(1024); // GH-90000
            assertThat(req.getTemperature()).isEqualTo(0.5); // GH-90000
            assertThat(req.getTopP()).isEqualTo(0.9); // GH-90000
            assertThat(req.getStop()).containsExactly("### [GH-90000]");
            assertThat(req.getStopSequences()).containsExactly("END [GH-90000]");
            assertThat(req.getResponseFormat()).isEqualTo("json_object [GH-90000]");
            assertThat(req.getFrequencyPenalty()).isEqualTo(0.2); // GH-90000
            assertThat(req.getPresencePenalty()).isEqualTo(0.3); // GH-90000
            assertThat(req.getMetadata()).containsEntry("user", "test"); // GH-90000
        }
    }

    @Nested
    @DisplayName("immutability [GH-90000]")
    class Immutability {

        @Test
        @DisplayName("messages list is immutable [GH-90000]")
        void messagesImmutable() { // GH-90000
            CompletionRequest req = CompletionRequest.builder() // GH-90000
                    .messages(List.of(ChatMessage.user("Hi [GH-90000]")))
                    .build(); // GH-90000
            assertThatThrownBy(() -> req.getMessages().add(ChatMessage.user("new [GH-90000]")))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("metadata map is immutable [GH-90000]")
        void metadataImmutable() { // GH-90000
            CompletionRequest req = CompletionRequest.builder() // GH-90000
                    .prompt("test [GH-90000]")
                    .metadata(Map.of("k", "v")) // GH-90000
                    .build(); // GH-90000
            assertThatThrownBy(() -> req.getMetadata().put("new", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }
}
