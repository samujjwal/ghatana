/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.4 — Tests for LLMAgent with LangChain4j integration.
 */
package com.ghatana.agent.llm;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LLMAgent} — LLM-backed agent with caching, token management, and fallback.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LLM Agent")
class LLMAgentTest {

    @Mock
    ChatLanguageModel chatModel;

    @Mock
    MemoryStore memoryStore;

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId(UUID.randomUUID().toString())
                .agentId("test-agent")
                .tenantId("test-tenant")
                .memoryStore(memoryStore)
                .build();
    }

    private LLMAgentConfig.LLMAgentConfigBuilder defaultConfig() {
        return LLMAgentConfig.builder()
                .systemPrompt("You are a test classifier.")
                .modelName("test-model")
                .maxTokens(2048);
    }

    private ChatResponse mockResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(new AiMessage(text))
                .tokenUsage(new TokenUsage(10, 20))
                .build();
    }

    // ─────────────────── Descriptor ───────────────────

    @Nested
    @DisplayName("Descriptor")
    class DescriptorTests {

        @Test
        @DisplayName("descriptor has correct agent type and subtype")
        void descriptorTypeAndSubtype() {
            LLMAgent agent = new LLMAgent("classify-1", chatModel, defaultConfig().build());
            AgentDescriptor desc = agent.descriptor();

            assertThat(desc.getAgentId()).isEqualTo("classify-1");
            assertThat(desc.getType()).isEqualTo(AgentType.PROBABILISTIC);
            assertThat(desc.getSubtype()).isEqualTo("LLM");
            assertThat(desc.getName()).contains("classify-1");
        }

        @Test
        @DisplayName("descriptor declares NLP capabilities")
        void descriptorCapabilities() {
            LLMAgent agent = new LLMAgent("nlp-1", chatModel, defaultConfig().build());
            Set<String> caps = agent.descriptor().getCapabilities();

            assertThat(caps).containsExactlyInAnyOrder(
                    "nlp", "classification", "summarization",
                    "anomaly-explanation", "rule-generation");
        }

        @Test
        @DisplayName("descriptor has FALLBACK failure mode")
        void descriptorFailureMode() {
            LLMAgent agent = new LLMAgent("fb-1", chatModel, defaultConfig().build());
            assertThat(agent.descriptor().getFailureMode())
                    .isEqualTo(FailureMode.FALLBACK);
        }
    }

    // ─────────────────── Processing ───────────────────

    @Nested
    @DisplayName("Processing")
    class ProcessingTests {

        @Test
        @DisplayName("calls LLM and returns result")
        void callsLlmAndReturns() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("classified: HIGH_SEVERITY"));

            LLMAgent agent = new LLMAgent("test-1", chatModel, defaultConfig().build());
            agent.initialize(AgentConfig.builder()
                    .agentId("test-1").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = agent.process(ctx, "CPU at 99%").getResult();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("classified: HIGH_SEVERITY");
            verify(chatModel).chat(any(ChatRequest.class));
        }

        @Test
        @DisplayName("uses prompt template with {{input}} placeholder")
        void promptTemplate() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("result"));

            LLMAgentConfig config = defaultConfig()
                    .userPromptTemplate("Classify this event: {{input}}")
                    .build();

            LLMAgent agent = new LLMAgent("tmpl-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("tmpl-1").type(AgentType.PROBABILISTIC).build());

            agent.process(ctx, "disk full").getResult();

            ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class);
            verify(chatModel).chat(cap.capture());
            String userMsg = cap.getValue().messages().stream()
                    .filter(m -> m instanceof dev.langchain4j.data.message.UserMessage)
                    .map(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                    .findFirst().orElse("");
            assertThat(userMsg).isEqualTo("Classify this event: disk full");
        }

        @Test
        @DisplayName("returns confidence from config base")
        void confidenceFromConfig() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("ok"));

            LLMAgentConfig config = defaultConfig().baseConfidence(0.85).build();
            LLMAgent agent = new LLMAgent("conf-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("conf-1").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = agent.process(ctx, "test").getResult();
            assertThat(result.getConfidence()).isEqualTo(0.85);
        }
    }

    // ─────────────────── Caching ───────────────────

    @Nested
    @DisplayName("Response caching")
    class CachingTests {

        @Test
        @DisplayName("cache hit avoids second LLM call")
        void cacheHitSkipsLlm() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("cached result"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(true).cacheTtlSeconds(60).build();
            LLMAgent agent = new LLMAgent("cache-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("cache-1").type(AgentType.PROBABILISTIC).build());

            // First call — LLM invoked
            agent.process(ctx, "same input").getResult();
            // Second call — cache hit
            AgentResult<String> result = agent.process(ctx, "same input").getResult();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("cached result");
            verify(chatModel, times(1)).chat(any(ChatRequest.class));
        }

        @Test
        @DisplayName("different inputs are cached separately")
        void differentInputsCachedSeparately() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("result-1"))
                    .thenReturn(mockResponse("result-2"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(true).build();
            LLMAgent agent = new LLMAgent("cache-2", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("cache-2").type(AgentType.PROBABILISTIC).build());

            agent.process(ctx, "input-A").getResult();
            agent.process(ctx, "input-B").getResult();

            assertThat(agent.cacheSize()).isEqualTo(2);
            verify(chatModel, times(2)).chat(any(ChatRequest.class));
        }

        @Test
        @DisplayName("cache disabled skips caching")
        void cacheDisabled() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("no cache"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(false).build();
            LLMAgent agent = new LLMAgent("nocache-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("nocache-1").type(AgentType.PROBABILISTIC).build());

            agent.process(ctx, "input").getResult();
            agent.process(ctx, "input").getResult();

            verify(chatModel, times(2)).chat(any(ChatRequest.class));
            assertThat(agent.cacheSize()).isZero();
        }

        @Test
        @DisplayName("clearCache empties the cache")
        void clearCacheWorks() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("result"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(true).build();
            LLMAgent agent = new LLMAgent("clear-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("clear-1").type(AgentType.PROBABILISTIC).build());

            agent.process(ctx, "input").getResult();
            assertThat(agent.cacheSize()).isEqualTo(1);

            agent.clearCache();
            assertThat(agent.cacheSize()).isZero();
        }
    }

    // ─────────────────── Token Budget ───────────────────

    @Nested
    @DisplayName("Token budget management")
    class TokenBudgetTests {

        @Test
        @DisplayName("estimateTokens uses 4-char heuristic")
        void tokenEstimation() {
            LLMAgent agent = new LLMAgent("tok-1", chatModel, defaultConfig().build());
            assertThat(agent.estimateTokens("")).isZero();
            assertThat(agent.estimateTokens(null)).isZero();
            assertThat(agent.estimateTokens("1234")).isEqualTo(1);
            assertThat(agent.estimateTokens("12345678")).isEqualTo(2);
            assertThat(agent.estimateTokens("123")).isEqualTo(1); // ceil
        }

        @Test
        @DisplayName("long input is truncated to token budget")
        void longInputTruncated() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(mockResponse("result"));

            LLMAgentConfig config = defaultConfig()
                    .maxTokens(20) // Very small budget
                    .systemPrompt("Be brief.")
                    .build();

            LLMAgent agent = new LLMAgent("trunc-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("trunc-1").type(AgentType.PROBABILISTIC).build());

            // Very long input
            String longInput = "x".repeat(10000);
            agent.process(ctx, longInput).getResult();

            ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class);
            verify(chatModel).chat(cap.capture());

            String userMsg = cap.getValue().messages().stream()
                    .filter(m -> m instanceof dev.langchain4j.data.message.UserMessage)
                    .map(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText())
                    .findFirst().orElse("");

            // Should be truncated
            assertThat(userMsg.length()).isLessThan(longInput.length());
            assertThat(userMsg).endsWith("[truncated]");
        }
    }

    // ─────────────────── Fallback ───────────────────

    @Nested
    @DisplayName("Fallback on LLM failure")
    class FallbackTests {

        @Test
        @DisplayName("returns fallback response when LLM throws")
        void fallbackOnException() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenThrow(new RuntimeException("API rate limited"));

            LLMAgentConfig config = defaultConfig()
                    .fallbackResponse("Unable to classify — please retry later")
                    .build();

            LLMAgent agent = new LLMAgent("fb-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("fb-1").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = agent.process(ctx, "input").getResult();

            // Fallback has low confidence → LOW_CONFIDENCE status, not FAILED
            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput()).contains("Unable to classify");
            assertThat(result.getConfidence()).isLessThan(0.2);
        }

        @Test
        @DisplayName("propagates failure when no fallback configured")
        void failureWithoutFallback() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenThrow(new RuntimeException("timeout"));

            LLMAgentConfig config = defaultConfig()
                    .fallbackResponse(null)
                    .build();

            LLMAgent agent = new LLMAgent("nofb-1", chatModel, config);
            agent.initialize(AgentConfig.builder()
                    .agentId("nofb-1").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = agent.process(ctx, "input").getResult();

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ─────────────────── LLMAgentConfig ───────────────────

    @Nested
    @DisplayName("LLMAgentConfig")
    class ConfigTests {

        @Test
        @DisplayName("defaults are sensible")
        void configDefaults() {
            LLMAgentConfig config = LLMAgentConfig.builder().build();

            assertThat(config.getMaxTokens()).isEqualTo(2048);
            assertThat(config.getTimeoutSeconds()).isEqualTo(30);
            assertThat(config.getBaseConfidence()).isEqualTo(0.75);
            assertThat(config.isCacheEnabled()).isTrue();
            assertThat(config.getCacheTtlSeconds()).isEqualTo(300);
            assertThat(config.getTemperature()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("builder overrides work")
        void configOverrides() {
            LLMAgentConfig config = LLMAgentConfig.builder()
                    .maxTokens(4096)
                    .timeoutSeconds(60)
                    .baseConfidence(0.9)
                    .temperature(0.7)
                    .modelName("gpt-4o")
                    .build();

            assertThat(config.getMaxTokens()).isEqualTo(4096);
            assertThat(config.getTimeoutSeconds()).isEqualTo(60);
            assertThat(config.getBaseConfidence()).isEqualTo(0.9);
            assertThat(config.getTemperature()).isEqualTo(0.7);
            assertThat(config.getModelName()).isEqualTo("gpt-4o");
        }

        @Test
        @DisplayName("toBuilder creates modified copy")
        void toBuilderWorks() {
            LLMAgentConfig original = LLMAgentConfig.builder()
                    .maxTokens(1024).build();
            LLMAgentConfig copy = original.toBuilder()
                    .maxTokens(2048).build();

            assertThat(original.getMaxTokens()).isEqualTo(1024);
            assertThat(copy.getMaxTokens()).isEqualTo(2048);
        }
    }

    // ─────────────────── Constructor Validation ───────────────────

    @Nested
    @DisplayName("Constructor validation")
    class ValidationTests {

        @Test
        @DisplayName("null agentId throws")
        void nullAgentId() {
            assertThatThrownBy(() -> new LLMAgent(null, chatModel, defaultConfig().build()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null chatModel throws")
        void nullChatModel() {
            assertThatThrownBy(() -> new LLMAgent("id", null, defaultConfig().build()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null config throws")
        void nullConfig() {
            assertThatThrownBy(() -> new LLMAgent("id", chatModel, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
