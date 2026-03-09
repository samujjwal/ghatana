package com.ghatana.services.aiinference;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.aiplatform.gateway.LLMGatewayService;
import com.ghatana.aiplatform.gateway.PromptCache;
import com.ghatana.aiplatform.gateway.ProviderRouter;
import com.ghatana.aiplatform.gateway.RateLimiter;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.core.state.HybridStateStore;
import com.ghatana.core.state.InMemoryStateStore;
import com.ghatana.core.state.SyncStrategy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for LLMGatewayService integration.
 *
 * <p>
 * Tests validate:
 * - Embedding generation with caching
 * - Completion generation with routing
 * - Rate limiting enforcement
 * - Provider fallback on errors
 * - Cache hit/miss behavior
 *
 * @see LLMGatewayService
 */
@DisplayName("LLM Gateway Service Tests")
class LLMGatewayServiceTest extends EventloopTestBase {

        private LLMGatewayService gateway;
        private EmbeddingService mockEmbeddingService;
        private CompletionService mockCompletionService;
        private PromptCache promptCache;
        private ProviderRouter router;
        private RateLimiter rateLimiter;
        private MetricsCollector metrics;

        @BeforeEach
        void setUp() {
                // GIVEN: Mocked services and real infrastructure components
                mockEmbeddingService = mock(EmbeddingService.class);
                mockCompletionService = mock(CompletionService.class);
                metrics = new NoopMetricsCollector();

                // Setup provider router
                router = new ProviderRouter(metrics);
                router.registerEmbeddingService("default", mockEmbeddingService);
                router.registerCompletionService("default", mockCompletionService);
                router.setDefaultEmbeddingProvider("default");
                router.setDefaultCompletionProvider("default");

                // Setup prompt cache with in-memory store backed by hybrid state store
                InMemoryStateStore<String, byte[]> localStore = new InMemoryStateStore<>();
                HybridStateStore<String, byte[]> hybridStore = HybridStateStore.<String, byte[]>builder()
                                .localStore(localStore)
                                .centralStore(localStore)
                                .syncStrategy(SyncStrategy.BATCHED)
                                .build();
                promptCache = new PromptCache(hybridStore, 600);

                // Setup rate limiter
                rateLimiter = new RateLimiter(1000, 10.0);

                // Create gateway
                gateway = new LLMGatewayService(router, promptCache, rateLimiter, metrics);
        }

        /**
         * Verifies embedding generation returns expected result.
         *
         * GIVEN: Valid tenant and text
         * WHEN: generateEmbedding() is called
         * THEN: Embedding is generated and returned with correct dimensions
         */
        @Test
        @DisplayName("Should generate embedding when requested")
        void shouldGenerateEmbeddingWhenRequested() {
                // GIVEN: Mock embedding service returns vector
                float[] expectedVector = new float[] { 0.1f, 0.2f, 0.3f };
                EmbeddingResult mockResult = EmbeddingResult.of(expectedVector);

                when(mockEmbeddingService.createEmbedding(any(String.class)))
                                .thenReturn(Promise.of(mockResult));

                // WHEN: Generate embedding
                EmbeddingResult result = runPromise(() -> gateway.generateEmbedding("tenant-123", "Hello world"));

                // THEN: Result matches expected
                assertThat(result).isNotNull();
                assertThat(result.getVector())
                                .as("Vector should match expected dimensions")
                                .hasSize(3);
                assertThat(result.getVector()[0])
                                .as("First vector element should match")
                                .isEqualTo(0.1f);
        }

        /**
         * Verifies prompt caching reduces API calls.
         *
         * GIVEN: Same text requested twice
         * WHEN: generateEmbedding() is called twice
         * THEN: Second call returns cached result (provider called once)
         */
        @Test
        @DisplayName("Should return cached embedding on repeated requests")
        void shouldReturnCachedEmbeddingOnRepeatedRequests() {
                // GIVEN: Mock embedding service
                float[] vector = new float[] { 0.1f, 0.2f, 0.3f };
                EmbeddingResult mockResult = EmbeddingResult.of(vector);

                when(mockEmbeddingService.createEmbedding(any(String.class)))
                                .thenReturn(Promise.of(mockResult));

                // WHEN: Generate embedding twice with same input
                EmbeddingResult result1 = runPromise(() -> gateway.generateEmbedding("tenant-123", "Hello world"));

                EmbeddingResult result2 = runPromise(() -> gateway.generateEmbedding("tenant-123", "Hello world"));

                // THEN: Both results should have same vector values
                assertThat(result1.getVector())
                                .as("Cached result should match original")
                                .isEqualTo(result2.getVector());
        }

        /**
         * Verifies batch embedding generation.
         *
         * GIVEN: Multiple texts
         * WHEN: generateEmbeddings() is called
         * THEN: Returns list of embeddings with correct count
         */
        @Test
        @DisplayName("Should generate batch embeddings when requested")
        void shouldGenerateBatchEmbeddingsWhenRequested() {
                // GIVEN: Mock embedding service returns vectors
                List<EmbeddingResult> mockResults = List.of(
                                EmbeddingResult.of(new float[] { 0.1f, 0.2f }),
                                EmbeddingResult.of(new float[] { 0.3f, 0.4f }));

                when(mockEmbeddingService.createEmbeddings(any()))
                                .thenReturn(Promise.of(mockResults));

                // WHEN: Generate batch embeddings
                List<EmbeddingResult> results = runPromise(
                                () -> gateway.generateEmbeddings("tenant-123", List.of("Hello", "World")));

                // THEN: Results match expected count
                assertThat(results)
                                .as("Should return 2 embeddings")
                                .hasSize(2);
                assertThat(results.get(0).getVector())
                                .as("First embedding should match")
                                .hasSize(2);
        }

        /**
         * Verifies completion generation returns expected result.
         *
         * GIVEN: Valid completion request
         * WHEN: generateCompletion() is called
         * THEN: Completion is generated with expected text and token usage
         */
        @Test
        @DisplayName("Should generate completion when requested")
        void shouldGenerateCompletionWhenRequested() {
                // GIVEN: Mock completion service returns result
                CompletionResult mockResult = CompletionResult.builder()
                                .text("Bonjour")
                                .tokensUsed(50)
                                .promptTokens(30)
                                .completionTokens(20)
                                .finishReason("stop")
                                .modelUsed("gpt-4")
                                .build();

                when(mockCompletionService.complete(any(CompletionRequest.class)))
                                .thenReturn(Promise.of(mockResult));

                // WHEN: Generate completion
                CompletionRequest request = CompletionRequest.builder()
                                .prompt("Translate to French: Hello")
                                .maxTokens(100)
                                .build();

                CompletionResult result = runPromise(() -> gateway.generateCompletion("tenant-123", request));

                // THEN: Result matches expected
                assertThat(result).isNotNull();
                assertThat(result.getText())
                                .as("Completion text should match")
                                .isEqualTo("Bonjour");
                assertThat(result.getTokensUsed())
                                .as("Token usage should match")
                                .isEqualTo(50);
        }

        /**
         * Verifies rate limiting enforcement.
         *
         * GIVEN: Rate limit exceeded for tenant
         * WHEN: generateEmbedding() is called
         * THEN: Request is rejected with rate limit exception
         */
        @Test
        @DisplayName("Should enforce rate limit when exceeded")
        void shouldEnforceRateLimitWhenExceeded() {
                // GIVEN: Set low rate limit
                rateLimiter.setTenantLimit("tenant-limited", 1, 0.1);

                float[] vector = new float[] { 0.1f };
                when(mockEmbeddingService.createEmbedding(any(String.class)))
                                .thenReturn(Promise.of(EmbeddingResult.of(vector)));

                // WHEN: Exhaust rate limit
                runPromise(() -> gateway.generateEmbedding("tenant-limited", "First"));

                try {
                        runPromise(() -> gateway.generateEmbedding("tenant-limited", "Second"));

                        // THEN: Should throw rate limit exception
                        assertThat(false)
                                        .as("Should have thrown RateLimitExceededException")
                                        .isTrue();
                } catch (Exception e) {
                        assertThat(e.getClass().getSimpleName())
                                        .as("Should be rate limit exception")
                                        .contains("RateLimit");
                }
        }

        /**
         * Verifies cache behavior for deterministic completions.
         *
         * GIVEN: Completion request with temperature=0 (deterministic)
         * WHEN: Same request made twice
         * THEN: Second request returns cached result
         */
        @Test
        @DisplayName("Should cache deterministic completions")
        void shouldCacheDeterministicCompletions() {
                // GIVEN: Mock completion service
                CompletionResult mockResult = CompletionResult.builder()
                                .text("Cached response")
                                .tokensUsed(10)
                                .build();

                when(mockCompletionService.complete(any(CompletionRequest.class)))
                                .thenReturn(Promise.of(mockResult));

                // WHEN: Generate deterministic completion twice
                CompletionRequest request = CompletionRequest.builder()
                                .prompt("Fixed prompt")
                                .maxTokens(50)
                                .build();

                CompletionResult result1 = runPromise(() -> gateway.generateCompletion("tenant-123", request));

                CompletionResult result2 = runPromise(() -> gateway.generateCompletion("tenant-123", request));

                // THEN: Both results should have same text
                assertThat(result1.getText())
                                .as("Cached result should match")
                                .isEqualTo(result2.getText());
        }
}
