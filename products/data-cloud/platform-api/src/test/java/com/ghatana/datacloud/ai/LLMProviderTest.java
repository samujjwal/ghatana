/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LLM provider abstraction (AI002). // GH-90000
 *
 * @doc.type class
 * @doc.purpose LLM provider abstraction tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("LLMProvider – Provider Abstraction (AI002) [GH-90000]")
class LLMProviderTest extends EventloopTestBase {

    @Mock
    private LLMProvider llmProvider;

    @Nested
    @DisplayName("Completion [GH-90000]")
    class CompletionTests {

        @Test
        @DisplayName("[AI002]: complete_returns_response [GH-90000]")
        void completeReturnsResponse() { // GH-90000
            LLMProvider.CompletionRequest request = LLMProvider.CompletionRequest.builder() // GH-90000
                .model("gpt-4 [GH-90000]")
                .prompt("What is SQL? [GH-90000]")
                .temperature(0.7) // GH-90000
                .maxTokens(500) // GH-90000
                .build(); // GH-90000

            LLMProvider.CompletionResponse response = new LLMProvider.CompletionResponse( // GH-90000
                "resp-001",
                "SQL is a query language for databases.",
                50, 10, 40, "stop", 800, "gpt-4"
            );

            when(llmProvider.complete(request)) // GH-90000
                .thenReturn(Promise.of(response)); // GH-90000

            LLMProvider.CompletionResponse result = runPromise(() -> // GH-90000
                llmProvider.complete(request) // GH-90000
            );

            assertThat(result.id()).isEqualTo("resp-001 [GH-90000]");
            assertThat(result.text()).contains("SQL [GH-90000]");
            assertThat(result.tokensUsed()).isEqualTo(50); // GH-90000
            assertThat(result.latencyMs()).isEqualTo(800); // GH-90000
            assertThat(result.model()).isEqualTo("gpt-4 [GH-90000]");
        }

        @Test
        @DisplayName("[AI002]: complete_with_different_temperatures [GH-90000]")
        void completeWithDifferentTemperatures() { // GH-90000
            LLMProvider.CompletionRequest lowTemp = LLMProvider.CompletionRequest.builder() // GH-90000
                .model("gpt-4 [GH-90000]")
                .prompt("Generate creative content [GH-90000]")
                .temperature(0.2) // GH-90000
                .build(); // GH-90000

            LLMProvider.CompletionRequest highTemp = LLMProvider.CompletionRequest.builder() // GH-90000
                .model("gpt-4 [GH-90000]")
                .prompt("Generate creative content [GH-90000]")
                .temperature(0.9) // GH-90000
                .build(); // GH-90000

            assertThat(lowTemp.temperature()).isEqualTo(0.2); // GH-90000
            assertThat(highTemp.temperature()).isEqualTo(0.9); // GH-90000
        }
    }

    @Nested
    @DisplayName("Chat Completion [GH-90000]")
    class ChatCompletionTests {

        @Test
        @DisplayName("[AI002]: chat_returns_conversation_response [GH-90000]")
        void chatReturnsConversationResponse() { // GH-90000
            LLMProvider.ChatRequest request = new LLMProvider.ChatRequest( // GH-90000
                "gpt-4",
                List.of( // GH-90000
                    new LLMProvider.ChatMessage("system", "You are a SQL expert."), // GH-90000
                    new LLMProvider.ChatMessage("user", "Write a query to get top 10 users.") // GH-90000
                ),
                0.7, 1000, Map.of() // GH-90000
            );

            LLMProvider.ChatResponse response = new LLMProvider.ChatResponse( // GH-90000
                "chat-001",
                List.of(new LLMProvider.ChatMessage("assistant", "SELECT * FROM users LIMIT 10")), // GH-90000
                25, "stop", 1200, "gpt-4"
            );

            when(llmProvider.chat(request)) // GH-90000
                .thenReturn(Promise.of(response)); // GH-90000

            LLMProvider.ChatResponse result = runPromise(() -> llmProvider.chat(request)); // GH-90000

            assertThat(result.messages()).hasSize(1); // GH-90000
            assertThat(result.messages().get(0).content()).contains("SELECT [GH-90000]");
        }

        @Test
        @DisplayName("[AI002]: chat_handles_multi_turn [GH-90000]")
        void chatHandlesMultiTurn() { // GH-90000
            LLMProvider.ChatRequest request = new LLMProvider.ChatRequest( // GH-90000
                "gpt-4",
                List.of( // GH-90000
                    new LLMProvider.ChatMessage("user", "Hello"), // GH-90000
                    new LLMProvider.ChatMessage("assistant", "Hi!"), // GH-90000
                    new LLMProvider.ChatMessage("user", "How are you?") // GH-90000
                ),
                0.7, 500, Map.of() // GH-90000
            );

            assertThat(request.messages()).hasSize(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("Model Management [GH-90000]")
    class ModelManagementTests {

        @Test
        @DisplayName("[AI002]: get_models_returns_available_models [GH-90000]")
        void getModelsReturnsAvailableModels() { // GH-90000
            List<LLMProvider.ModelInfo> models = List.of( // GH-90000
                new LLMProvider.ModelInfo( // GH-90000
                    "gpt-4", "GPT-4", "openai", 8192,
                    List.of("completion", "chat", "code"), true // GH-90000
                ),
                new LLMProvider.ModelInfo( // GH-90000
                    "gpt-3.5-turbo", "GPT-3.5 Turbo", "openai", 4096,
                    List.of("completion", "chat"), true // GH-90000
                ),
                new LLMProvider.ModelInfo( // GH-90000
                    "claude-3", "Claude 3", "anthropic", 100000,
                    List.of("completion", "chat"), true // GH-90000
                )
            );

            when(llmProvider.getModels()) // GH-90000
                .thenReturn(Promise.of(models)); // GH-90000

            List<LLMProvider.ModelInfo> result = runPromise(() -> llmProvider.getModels()); // GH-90000

            assertThat(result).hasSize(3); // GH-90000
            assertThat(result.get(0).id()).isEqualTo("gpt-4 [GH-90000]");
            assertThat(result.get(0).capabilities()).contains("chat [GH-90000]");
        }

        @Test
        @DisplayName("[AI002]: get_name_returns_provider_name [GH-90000]")
        void getNameReturnsProviderName() { // GH-90000
            when(llmProvider.getName()) // GH-90000
                .thenReturn("openai [GH-90000]");

            String name = llmProvider.getName(); // GH-90000

            assertThat(name).isEqualTo("openai [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Provider Status [GH-90000]")
    class ProviderStatusTests {

        @Test
        @DisplayName("[AI002]: get_status_returns_health_info [GH-90000]")
        void getStatusReturnsHealthInfo() { // GH-90000
            LLMProvider.ProviderStatus status = new LLMProvider.ProviderStatus( // GH-90000
                "openai", true, "Healthy", 5, 450.0, Instant.now() // GH-90000
            );

            when(llmProvider.getStatus()) // GH-90000
                .thenReturn(Promise.of(status)); // GH-90000

            LLMProvider.ProviderStatus result = runPromise(() -> llmProvider.getStatus()); // GH-90000

            assertThat(result.healthy()).isTrue(); // GH-90000
            assertThat(result.requestsInFlight()).isEqualTo(5); // GH-90000
            assertThat(result.averageLatencyMs()).isEqualTo(450.0); // GH-90000
        }

        @Test
        @DisplayName("[AI002]: get_status_shows_unhealthy_when_down [GH-90000]")
        void getStatusShowsUnhealthyWhenDown() { // GH-90000
            LLMProvider.ProviderStatus status = new LLMProvider.ProviderStatus( // GH-90000
                "openai", false, "Service unavailable", 0, 0.0, Instant.now() // GH-90000
            );

            when(llmProvider.getStatus()) // GH-90000
                .thenReturn(Promise.of(status)); // GH-90000

            LLMProvider.ProviderStatus result = runPromise(() -> llmProvider.getStatus()); // GH-90000

            assertThat(result.healthy()).isFalse(); // GH-90000
            assertThat(result.message()).contains("unavailable [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Request Builder [GH-90000]")
    class RequestBuilderTests {

        @Test
        @DisplayName("[AI002]: completion_request_builder_creates_request [GH-90000]")
        void completionRequestBuilderCreatesRequest() { // GH-90000
            LLMProvider.CompletionRequest request = LLMProvider.CompletionRequest.builder() // GH-90000
                .model("gpt-4 [GH-90000]")
                .prompt("Generate SQL [GH-90000]")
                .temperature(0.5) // GH-90000
                .maxTokens(200) // GH-90000
                .stopSequences(List.of(";", "END")) // GH-90000
                .parameters(Map.of("top_p", 0.9)) // GH-90000
                .build(); // GH-90000

            assertThat(request.model()).isEqualTo("gpt-4 [GH-90000]");
            assertThat(request.temperature()).isEqualTo(0.5); // GH-90000
            assertThat(request.maxTokens()).isEqualTo(200); // GH-90000
            assertThat(request.stopSequences()).contains("; [GH-90000]");
        }

        @Test
        @DisplayName("[AI002]: builder_uses_defaults [GH-90000]")
        void builderUsesDefaults() { // GH-90000
            LLMProvider.CompletionRequest request = LLMProvider.CompletionRequest.builder() // GH-90000
                .model("gpt-3.5 [GH-90000]")
                .prompt("Test [GH-90000]")
                .build(); // GH-90000

            assertThat(request.temperature()).isEqualTo(0.7); // Default // GH-90000
            assertThat(request.maxTokens()).isEqualTo(1000); // Default // GH-90000
        }
    }

    @Nested
    @DisplayName("Token Tracking [GH-90000]")
    class TokenTrackingTests {

        @Test
        @DisplayName("[AI002]: response_includes_token_counts [GH-90000]")
        void responseIncludesTokenCounts() { // GH-90000
            LLMProvider.CompletionResponse response = new LLMProvider.CompletionResponse( // GH-90000
                "resp-001", "Response text", 100, 50, 50, "stop", 500, "gpt-4"
            );

            assertThat(response.promptTokens()).isEqualTo(50); // GH-90000
            assertThat(response.completionTokens()).isEqualTo(50); // GH-90000
            assertThat(response.tokensUsed()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("[AI002]: finish_reason_tracked [GH-90000]")
        void finishReasonTracked() { // GH-90000
            LLMProvider.CompletionResponse normal = new LLMProvider.CompletionResponse( // GH-90000
                "r1", "Text", 10, 5, 5, "stop", 100, "gpt-4"
            );

            LLMProvider.CompletionResponse maxTokens = new LLMProvider.CompletionResponse( // GH-90000
                "r2", "Long text...", 1000, 50, 950, "length", 2000, "gpt-4"
            );

            assertThat(normal.finishReason()).isEqualTo("stop [GH-90000]");
            assertThat(maxTokens.finishReason()).isEqualTo("length [GH-90000]");
        }
    }
}
