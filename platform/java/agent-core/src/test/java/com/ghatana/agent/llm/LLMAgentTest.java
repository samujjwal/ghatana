/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 5.4 — Tests for LLMAgent with LangChain4j integration.
 */
package com.ghatana.agent.llm;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LLMAgent} — LLM-backed agent with caching, token management, and fallback.
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("LLM Agent [GH-90000]")
class LLMAgentTest extends EventloopTestBase {

    @Mock
    ChatLanguageModel chatModel;

    @Mock
    MemoryStore memoryStore;

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId(UUID.randomUUID().toString()) // GH-90000
                .agentId("test-agent [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000
    }

    private LLMAgentConfig.LLMAgentConfigBuilder defaultConfig() { // GH-90000
        return LLMAgentConfig.builder() // GH-90000
                .systemPrompt("You are a test classifier. [GH-90000]")
                .modelName("test-model [GH-90000]")
                .maxTokens(2048); // GH-90000
    }

    private ChatResponse mockResponse(String text) { // GH-90000
        return ChatResponse.builder() // GH-90000
                .aiMessage(new AiMessage(text)) // GH-90000
                .tokenUsage(new TokenUsage(10, 20)) // GH-90000
                .build(); // GH-90000
    }

    // ─────────────────── Descriptor ───────────────────

    @Nested
    @DisplayName("Descriptor [GH-90000]")
    class DescriptorTests {

        @Test
        @DisplayName("descriptor has correct agent type and subtype [GH-90000]")
        void descriptorTypeAndSubtype() { // GH-90000
            LLMAgent agent = new LLMAgent("classify-1", chatModel, defaultConfig().build()); // GH-90000
            AgentDescriptor desc = agent.descriptor(); // GH-90000

            assertThat(desc.getAgentId()).isEqualTo("classify-1 [GH-90000]");
            assertThat(desc.getType()).isEqualTo(AgentType.PROBABILISTIC); // GH-90000
            assertThat(desc.getSubtype()).isEqualTo("LLM [GH-90000]");
            assertThat(desc.getName()).contains("classify-1 [GH-90000]");
        }

        @Test
        @DisplayName("descriptor declares NLP capabilities [GH-90000]")
        void descriptorCapabilities() { // GH-90000
            LLMAgent agent = new LLMAgent("nlp-1", chatModel, defaultConfig().build()); // GH-90000
            Set<String> caps = agent.descriptor().getCapabilities(); // GH-90000

            assertThat(caps).containsExactlyInAnyOrder( // GH-90000
                    "nlp", "classification", "summarization",
                    "anomaly-explanation", "rule-generation");
        }

        @Test
        @DisplayName("descriptor has FALLBACK failure mode [GH-90000]")
        void descriptorFailureMode() { // GH-90000
            LLMAgent agent = new LLMAgent("fb-1", chatModel, defaultConfig().build()); // GH-90000
            assertThat(agent.descriptor().getFailureMode()) // GH-90000
                    .isEqualTo(FailureMode.FALLBACK); // GH-90000
        }
    }

    // ─────────────────── Processing ───────────────────

    @Nested
    @DisplayName("Processing [GH-90000]")
    class ProcessingTests {

        @Test
        @DisplayName("calls LLM and returns result [GH-90000]")
        void callsLlmAndReturns() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("classified: HIGH_SEVERITY [GH-90000]"));

            LLMAgent agent = new LLMAgent("test-1", chatModel, defaultConfig().build()); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("test-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = runPromise(() -> agent.process(ctx, "CPU at 99%")); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isEqualTo("classified: HIGH_SEVERITY [GH-90000]");
            verify(chatModel).chat(any(ChatRequest.class)); // GH-90000
        }

        @Test
        @DisplayName("uses prompt template with {{input}} placeholder [GH-90000]")
        void promptTemplate() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("result [GH-90000]"));

            LLMAgentConfig config = defaultConfig() // GH-90000
                    .userPromptTemplate("Classify this event: {{input}} [GH-90000]")
                    .build(); // GH-90000

            LLMAgent agent = new LLMAgent("tmpl-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("tmpl-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            runPromise(() -> agent.process(ctx, "disk full")); // GH-90000

            ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class); // GH-90000
            verify(chatModel).chat(cap.capture()); // GH-90000
            String userMsg = cap.getValue().messages().stream() // GH-90000
                    .filter(m -> m instanceof dev.langchain4j.data.message.UserMessage) // GH-90000
                    .map(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText()) // GH-90000
                    .findFirst().orElse(" [GH-90000]");
            assertThat(userMsg).isEqualTo("Classify this event: disk full [GH-90000]");
        }

        @Test
        @DisplayName("returns confidence from config base [GH-90000]")
        void confidenceFromConfig() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("ok [GH-90000]"));

            LLMAgentConfig config = defaultConfig().baseConfidence(0.85).build(); // GH-90000
            LLMAgent agent = new LLMAgent("conf-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("conf-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = runPromise(() -> agent.process(ctx, "test")); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(0.85); // GH-90000
        }
    }

    // ─────────────────── Caching ───────────────────

    @Nested
    @DisplayName("Response caching [GH-90000]")
    class CachingTests {

        @Test
        @DisplayName("cache hit avoids second LLM call [GH-90000]")
        void cacheHitSkipsLlm() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("cached result [GH-90000]"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(true).cacheTtlSeconds(60).build(); // GH-90000
            LLMAgent agent = new LLMAgent("cache-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("cache-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            // First call — LLM invoked
            runPromise(() -> agent.process(ctx, "same input")); // GH-90000
            // Second call — cache hit
            AgentResult<String> result = runPromise(() -> agent.process(ctx, "same input")); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isEqualTo("cached result [GH-90000]");
            verify(chatModel, times(1)).chat(any(ChatRequest.class)); // GH-90000
        }

        @Test
        @DisplayName("different inputs are cached separately [GH-90000]")
        void differentInputsCachedSeparately() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("result-1 [GH-90000]"))
                    .thenReturn(mockResponse("result-2 [GH-90000]"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(true).build(); // GH-90000
            LLMAgent agent = new LLMAgent("cache-2", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("cache-2 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            runPromise(() -> agent.process(ctx, "input-A")); // GH-90000
            runPromise(() -> agent.process(ctx, "input-B")); // GH-90000

            assertThat(agent.cacheSize()).isEqualTo(2); // GH-90000
            verify(chatModel, times(2)).chat(any(ChatRequest.class)); // GH-90000
        }

        @Test
        @DisplayName("cache disabled skips caching [GH-90000]")
        void cacheDisabled() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("no cache [GH-90000]"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(false).build(); // GH-90000
            LLMAgent agent = new LLMAgent("nocache-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("nocache-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            runPromise(() -> agent.process(ctx, "input")); // GH-90000
            runPromise(() -> agent.process(ctx, "input")); // GH-90000

            verify(chatModel, times(2)).chat(any(ChatRequest.class)); // GH-90000
            assertThat(agent.cacheSize()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("clearCache empties the cache [GH-90000]")
        void clearCacheWorks() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("result [GH-90000]"));

            LLMAgentConfig config = defaultConfig().cacheEnabled(true).build(); // GH-90000
            LLMAgent agent = new LLMAgent("clear-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("clear-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            runPromise(() -> agent.process(ctx, "input")); // GH-90000
            assertThat(agent.cacheSize()).isEqualTo(1); // GH-90000

            agent.clearCache(); // GH-90000
            assertThat(agent.cacheSize()).isZero(); // GH-90000
        }
    }

    // ─────────────────── Token Budget ───────────────────

    @Nested
    @DisplayName("Token budget management [GH-90000]")
    class TokenBudgetTests {

        @Test
        @DisplayName("estimateTokens uses 4-char heuristic [GH-90000]")
        void tokenEstimation() { // GH-90000
            LLMAgent agent = new LLMAgent("tok-1", chatModel, defaultConfig().build()); // GH-90000
            assertThat(agent.estimateTokens(" [GH-90000]")).isZero();
            assertThat(agent.estimateTokens(null)).isZero(); // GH-90000
            assertThat(agent.estimateTokens("1234 [GH-90000]")).isEqualTo(1);
            assertThat(agent.estimateTokens("12345678 [GH-90000]")).isEqualTo(2);
            assertThat(agent.estimateTokens("123 [GH-90000]")).isEqualTo(1); // ceil
        }

        @Test
        @DisplayName("long input is truncated to token budget [GH-90000]")
        void longInputTruncated() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenReturn(mockResponse("result [GH-90000]"));

            LLMAgentConfig config = defaultConfig() // GH-90000
                    .maxTokens(20) // Very small budget // GH-90000
                    .systemPrompt("Be brief. [GH-90000]")
                    .build(); // GH-90000

            LLMAgent agent = new LLMAgent("trunc-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("trunc-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            // Very long input
            String longInput = "x".repeat(10000); // GH-90000
            runPromise(() -> agent.process(ctx, longInput)); // GH-90000

            ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class); // GH-90000
            verify(chatModel).chat(cap.capture()); // GH-90000

            String userMsg = cap.getValue().messages().stream() // GH-90000
                    .filter(m -> m instanceof dev.langchain4j.data.message.UserMessage) // GH-90000
                    .map(m -> ((dev.langchain4j.data.message.UserMessage) m).singleText()) // GH-90000
                    .findFirst().orElse(" [GH-90000]");

            // Should be truncated
            assertThat(userMsg.length()).isLessThan(longInput.length()); // GH-90000
            assertThat(userMsg).endsWith("[truncated] [GH-90000]");
        }
    }

    // ─────────────────── Fallback ───────────────────

    @Nested
    @DisplayName("Fallback on LLM failure [GH-90000]")
    class FallbackTests {

        @Test
        @DisplayName("returns fallback response when LLM throws [GH-90000]")
        void fallbackOnException() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenThrow(new RuntimeException("API rate limited [GH-90000]"));

            LLMAgentConfig config = defaultConfig() // GH-90000
                    .fallbackResponse("Unable to classify — please retry later [GH-90000]")
                    .build(); // GH-90000

            LLMAgent agent = new LLMAgent("fb-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("fb-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = runPromise(() -> agent.process(ctx, "input")); // GH-90000

            // Fallback has low confidence → LOW_CONFIDENCE status, not FAILED
            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput()).contains("Unable to classify [GH-90000]");
            assertThat(result.getConfidence()).isLessThan(0.2); // GH-90000
        }

        @Test
        @DisplayName("propagates failure when no fallback configured [GH-90000]")
        void failureWithoutFallback() { // GH-90000
            when(chatModel.chat(any(ChatRequest.class))) // GH-90000
                    .thenThrow(new RuntimeException("timeout [GH-90000]"));

            LLMAgentConfig config = defaultConfig() // GH-90000
                    .fallbackResponse(null) // GH-90000
                    .build(); // GH-90000

            LLMAgent agent = new LLMAgent("nofb-1", chatModel, config); // GH-90000
            agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("nofb-1 [GH-90000]").type(AgentType.PROBABILISTIC).build());

            AgentResult<String> result = runPromise(() -> agent.process(ctx, "input")); // GH-90000

            assertThat(result.isSuccess()).isFalse(); // GH-90000
        }
    }

    // ─────────────────── LLMAgentConfig ───────────────────

    @Nested
    @DisplayName("LLMAgentConfig [GH-90000]")
    class ConfigTests {

        @Test
        @DisplayName("defaults are sensible [GH-90000]")
        void configDefaults() { // GH-90000
            LLMAgentConfig config = LLMAgentConfig.builder().build(); // GH-90000

            assertThat(config.getMaxTokens()).isEqualTo(2048); // GH-90000
            assertThat(config.getTimeoutSeconds()).isEqualTo(30); // GH-90000
            assertThat(config.getBaseConfidence()).isEqualTo(0.75); // GH-90000
            assertThat(config.isCacheEnabled()).isTrue(); // GH-90000
            assertThat(config.getCacheTtlSeconds()).isEqualTo(300); // GH-90000
            assertThat(config.getTemperature()).isEqualTo(0.3); // GH-90000
        }

        @Test
        @DisplayName("builder overrides work [GH-90000]")
        void configOverrides() { // GH-90000
            LLMAgentConfig config = LLMAgentConfig.builder() // GH-90000
                    .maxTokens(4096) // GH-90000
                    .timeoutSeconds(60) // GH-90000
                    .baseConfidence(0.9) // GH-90000
                    .temperature(0.7) // GH-90000
                    .modelName("gpt-4o [GH-90000]")
                    .build(); // GH-90000

            assertThat(config.getMaxTokens()).isEqualTo(4096); // GH-90000
            assertThat(config.getTimeoutSeconds()).isEqualTo(60); // GH-90000
            assertThat(config.getBaseConfidence()).isEqualTo(0.9); // GH-90000
            assertThat(config.getTemperature()).isEqualTo(0.7); // GH-90000
            assertThat(config.getModelName()).isEqualTo("gpt-4o [GH-90000]");
        }

        @Test
        @DisplayName("toBuilder creates modified copy [GH-90000]")
        void toBuilderWorks() { // GH-90000
            LLMAgentConfig original = LLMAgentConfig.builder() // GH-90000
                    .maxTokens(1024).build(); // GH-90000
            LLMAgentConfig copy = original.toBuilder() // GH-90000
                    .maxTokens(2048).build(); // GH-90000

            assertThat(original.getMaxTokens()).isEqualTo(1024); // GH-90000
            assertThat(copy.getMaxTokens()).isEqualTo(2048); // GH-90000
        }
    }

    // ─────────────────── Constructor Validation ───────────────────

    @Nested
    @DisplayName("Constructor validation [GH-90000]")
    class ValidationTests {

        @Test
        @DisplayName("null agentId throws [GH-90000]")
        void nullAgentId() { // GH-90000
            assertThatThrownBy(() -> new LLMAgent(null, chatModel, defaultConfig().build())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null chatModel throws [GH-90000]")
        void nullChatModel() { // GH-90000
            assertThatThrownBy(() -> new LLMAgent("id", null, defaultConfig().build())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null config throws [GH-90000]")
        void nullConfig() { // GH-90000
            assertThatThrownBy(() -> new LLMAgent("id", chatModel, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
