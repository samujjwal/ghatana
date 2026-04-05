/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for LLM provider abstraction (AI002).
 *
 * @doc.type class
 * @doc.purpose LLM provider abstraction tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LLMProvider – Provider Abstraction (AI002)")
class LLMProviderTest extends EventloopTestBase {

    @Mock
    private LLMProvider llmProvider;

    @Nested
    @DisplayName("Completion")
    class CompletionTests {

        @Test
        @DisplayName("[AI002]: complete_returns_response")
        void completeReturnsResponse() {
            LLMProvider.CompletionRequest request = LLMProvider.CompletionRequest.builder()
                .model("gpt-4")
                .prompt("What is SQL?")
                .temperature(0.7)
                .maxTokens(500)
                .build();

            LLMProvider.CompletionResponse response = new LLMProvider.CompletionResponse(
                "resp-001",
                "SQL is a query language for databases.",
                50, 10, 40, "stop", 800, "gpt-4"
            );

            when(llmProvider.complete(request))
                .thenReturn(Promise.of(response));

            LLMProvider.CompletionResponse result = runPromise(() ->
                llmProvider.complete(request)
            );

            assertThat(result.id()).isEqualTo("resp-001");
            assertThat(result.text()).contains("SQL");
            assertThat(result.tokensUsed()).isEqualTo(50);
            assertThat(result.latencyMs()).isEqualTo(800);
            assertThat(result.model()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("[AI002]: complete_with_different_temperatures")
        void completeWithDifferentTemperatures() {
            LLMProvider.CompletionRequest lowTemp = LLMProvider.CompletionRequest.builder()
                .model("gpt-4")
                .prompt("Generate creative content")
                .temperature(0.2)
                .build();

            LLMProvider.CompletionRequest highTemp = LLMProvider.CompletionRequest.builder()
                .model("gpt-4")
                .prompt("Generate creative content")
                .temperature(0.9)
                .build();

            assertThat(lowTemp.temperature()).isEqualTo(0.2);
            assertThat(highTemp.temperature()).isEqualTo(0.9);
        }
    }

    @Nested
    @DisplayName("Chat Completion")
    class ChatCompletionTests {

        @Test
        @DisplayName("[AI002]: chat_returns_conversation_response")
        void chatReturnsConversationResponse() {
            LLMProvider.ChatRequest request = new LLMProvider.ChatRequest(
                "gpt-4",
                List.of(
                    new LLMProvider.ChatMessage("system", "You are a SQL expert."),
                    new LLMProvider.ChatMessage("user", "Write a query to get top 10 users.")
                ),
                0.7, 1000, Map.of()
            );

            LLMProvider.ChatResponse response = new LLMProvider.ChatResponse(
                "chat-001",
                List.of(new LLMProvider.ChatMessage("assistant", "SELECT * FROM users LIMIT 10")),
                25, "stop", 1200, "gpt-4"
            );

            when(llmProvider.chat(request))
                .thenReturn(Promise.of(response));

            LLMProvider.ChatResponse result = runPromise(() -> llmProvider.chat(request));

            assertThat(result.messages()).hasSize(1);
            assertThat(result.messages().get(0).content()).contains("SELECT");
        }

        @Test
        @DisplayName("[AI002]: chat_handles_multi_turn")
        void chatHandlesMultiTurn() {
            LLMProvider.ChatRequest request = new LLMProvider.ChatRequest(
                "gpt-4",
                List.of(
                    new LLMProvider.ChatMessage("user", "Hello"),
                    new LLMProvider.ChatMessage("assistant", "Hi!"),
                    new LLMProvider.ChatMessage("user", "How are you?")
                ),
                0.7, 500, Map.of()
            );

            assertThat(request.messages()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Model Management")
    class ModelManagementTests {

        @Test
        @DisplayName("[AI002]: get_models_returns_available_models")
        void getModelsReturnsAvailableModels() {
            List<LLMProvider.ModelInfo> models = List.of(
                new LLMProvider.ModelInfo(
                    "gpt-4", "GPT-4", "openai", 8192,
                    List.of("completion", "chat", "code"), true
                ),
                new LLMProvider.ModelInfo(
                    "gpt-3.5-turbo", "GPT-3.5 Turbo", "openai", 4096,
                    List.of("completion", "chat"), true
                ),
                new LLMProvider.ModelInfo(
                    "claude-3", "Claude 3", "anthropic", 100000,
                    List.of("completion", "chat"), true
                )
            );

            when(llmProvider.getModels())
                .thenReturn(Promise.of(models));

            List<LLMProvider.ModelInfo> result = runPromise(() -> llmProvider.getModels());

            assertThat(result).hasSize(3);
            assertThat(result.get(0).id()).isEqualTo("gpt-4");
            assertThat(result.get(0).capabilities()).contains("chat");
        }

        @Test
        @DisplayName("[AI002]: get_name_returns_provider_name")
        void getNameReturnsProviderName() {
            when(llmProvider.getName())
                .thenReturn("openai");

            String name = llmProvider.getName();

            assertThat(name).isEqualTo("openai");
        }
    }

    @Nested
    @DisplayName("Provider Status")
    class ProviderStatusTests {

        @Test
        @DisplayName("[AI002]: get_status_returns_health_info")
        void getStatusReturnsHealthInfo() {
            LLMProvider.ProviderStatus status = new LLMProvider.ProviderStatus(
                "openai", true, "Healthy", 5, 450.0, Instant.now()
            );

            when(llmProvider.getStatus())
                .thenReturn(Promise.of(status));

            LLMProvider.ProviderStatus result = runPromise(() -> llmProvider.getStatus());

            assertThat(result.healthy()).isTrue();
            assertThat(result.requestsInFlight()).isEqualTo(5);
            assertThat(result.averageLatencyMs()).isEqualTo(450.0);
        }

        @Test
        @DisplayName("[AI002]: get_status_shows_unhealthy_when_down")
        void getStatusShowsUnhealthyWhenDown() {
            LLMProvider.ProviderStatus status = new LLMProvider.ProviderStatus(
                "openai", false, "Service unavailable", 0, 0.0, Instant.now()
            );

            when(llmProvider.getStatus())
                .thenReturn(Promise.of(status));

            LLMProvider.ProviderStatus result = runPromise(() -> llmProvider.getStatus());

            assertThat(result.healthy()).isFalse();
            assertThat(result.message()).contains("unavailable");
        }
    }

    @Nested
    @DisplayName("Request Builder")
    class RequestBuilderTests {

        @Test
        @DisplayName("[AI002]: completion_request_builder_creates_request")
        void completionRequestBuilderCreatesRequest() {
            LLMProvider.CompletionRequest request = LLMProvider.CompletionRequest.builder()
                .model("gpt-4")
                .prompt("Generate SQL")
                .temperature(0.5)
                .maxTokens(200)
                .stopSequences(List.of(";", "END"))
                .parameters(Map.of("top_p", 0.9))
                .build();

            assertThat(request.model()).isEqualTo("gpt-4");
            assertThat(request.temperature()).isEqualTo(0.5);
            assertThat(request.maxTokens()).isEqualTo(200);
            assertThat(request.stopSequences()).contains(";");
        }

        @Test
        @DisplayName("[AI002]: builder_uses_defaults")
        void builderUsesDefaults() {
            LLMProvider.CompletionRequest request = LLMProvider.CompletionRequest.builder()
                .model("gpt-3.5")
                .prompt("Test")
                .build();

            assertThat(request.temperature()).isEqualTo(0.7); // Default
            assertThat(request.maxTokens()).isEqualTo(1000); // Default
        }
    }

    @Nested
    @DisplayName("Token Tracking")
    class TokenTrackingTests {

        @Test
        @DisplayName("[AI002]: response_includes_token_counts")
        void responseIncludesTokenCounts() {
            LLMProvider.CompletionResponse response = new LLMProvider.CompletionResponse(
                "resp-001", "Response text", 100, 50, 50, "stop", 500, "gpt-4"
            );

            assertThat(response.promptTokens()).isEqualTo(50);
            assertThat(response.completionTokens()).isEqualTo(50);
            assertThat(response.tokensUsed()).isEqualTo(100);
        }

        @Test
        @DisplayName("[AI002]: finish_reason_tracked")
        void finishReasonTracked() {
            LLMProvider.CompletionResponse normal = new LLMProvider.CompletionResponse(
                "r1", "Text", 10, 5, 5, "stop", 100, "gpt-4"
            );

            LLMProvider.CompletionResponse maxTokens = new LLMProvider.CompletionResponse(
                "r2", "Long text...", 1000, 50, 950, "length", 2000, "gpt-4"
            );

            assertThat(normal.finishReason()).isEqualTo("stop");
            assertThat(maxTokens.finishReason()).isEqualTo("length");
        }
    }
}
