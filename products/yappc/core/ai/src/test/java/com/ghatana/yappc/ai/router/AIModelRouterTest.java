package com.ghatana.yappc.ai.router;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

import static com.ghatana.yappc.ai.router.AIRequest.TaskType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AI Model Router.
 *
 * @doc.type test
 * @doc.purpose Router functionality validation

 * @doc.layer core
 * @doc.pattern Test
*/
class AIModelRouterTest extends EventloopTestBase {

    @Test
    void testDeterministicFallbackUsesNextModel() {
        AIRouterConfig config = AIRouterConfig.defaults();
        TestModelAdapterFactory factory = new TestModelAdapterFactory();
        AIModelRouter router = new AIModelRouter(config, factory);

        runPromise(router::initialize);

        factory.fail("codellama", new RuntimeException("codellama unavailable"));
        factory.succeed("llama3.2", "fallback answer");

        AIRequest request = AIRequest.builder()
                .taskType(CODE_GENERATION)
                .prompt("generate code")
                .build();

        AIResponse response = runPromise(() -> router.route(request));

        assertEquals("llama3.2", response.getModelId());
        assertTrue(response.isFallbackUsed());
        assertEquals("fallback answer", response.getContent());

        runPromise(router::shutdown);
    }

    @Test
    void testDeterministicFallbackStopsOnCycle() {
        AIRouterConfig config = AIRouterConfig.defaults();
        TestModelAdapterFactory factory = new TestModelAdapterFactory();
        AIModelRouter router = new AIModelRouter(config, factory);

        runPromise(router::initialize);

        factory.fail("codellama", new RuntimeException("codellama unavailable"));
        factory.fail("llama3.2", new RuntimeException("llama unavailable"));
        factory.fail("mistral", new RuntimeException("mistral unavailable"));
        factory.fail("phi-3", new RuntimeException("phi unavailable"));

        AIRequest request = AIRequest.builder()
                .taskType(CODE_GENERATION)
                .prompt("generate code")
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> runPromise(() -> router.route(request)));
        assertTrue(exception.getMessage().contains("Deterministic fallback cycle detected"));

        runPromise(router::shutdown);
    }

    @Test
    void testRouterInitialization() throws Exception { // GH-90000
        AIRouterConfig config = AIRouterConfig.defaults(); // GH-90000
        AIModelRouter router = new AIModelRouter(config); // GH-90000

        Promise<Void> init = router.initialize(); // GH-90000
        init.whenComplete((v, error) -> { // GH-90000
            assertNull(error, "Initialization should succeed"); // GH-90000
        });

        // Wait for completion
        Thread.sleep(2000); // GH-90000

        // Check available models
        assertEquals(4, router.getAvailableModels().size(), "Should have 4 models"); // GH-90000
        assertTrue(router.getAvailableModels().containsKey("llama3.2"), "Should have llama3.2");
        assertTrue(router.getAvailableModels().containsKey("codellama"), "Should have codellama");
        assertTrue(router.getAvailableModels().containsKey("mistral"), "Should have mistral");
        assertTrue(router.getAvailableModels().containsKey("phi-3"), "Should have phi-3");

        router.shutdown(); // GH-90000
    }

    @Test
    void testModelSelection() { // GH-90000
        AIRouterConfig config = AIRouterConfig.defaults(); // GH-90000
        AIModelRouter router = new AIModelRouter(config); // GH-90000

        // Should select codellama for code tasks
        // (This test assumes models are registered but not necessarily available) // GH-90000

        router.shutdown(); // GH-90000
    }

    @Test
    void testSemanticCache() throws Exception { // GH-90000
        SemanticCache.CacheConfig cacheConfig = SemanticCache.CacheConfig.builder() // GH-90000
                .enabled(true) // GH-90000
                .maxSize(100) // GH-90000
                .ttlSeconds(60) // GH-90000
                .build(); // GH-90000

        SemanticCache cache = new SemanticCache(cacheConfig); // GH-90000

        // Create request and response
        AIRequest request = AIRequest.builder() // GH-90000
                .taskType(CHAT) // GH-90000
                .prompt("Hello, how are you?")
                .build(); // GH-90000

        AIResponse response = AIResponse.builder() // GH-90000
                .requestId(request.getRequestId()) // GH-90000
                .modelId("llama3.2")
                .content("I'm doing well, thank you!")
                .metrics(AIResponse.ResponseMetrics.builder() // GH-90000
                        .latencyMs(100) // GH-90000
                        .tokenCount(10) // GH-90000
                        .build()) // GH-90000
                .build(); // GH-90000

        // Put in cache
        cache.put(request, response); // GH-90000

        // Get from cache
        Promise<AIResponse> cached = cache.get(request); // GH-90000
        cached.whenComplete((resp, error) -> { // GH-90000
            assertNull(error, "Cache get should succeed"); // GH-90000
            assertNotNull(resp, "Should find cached response"); // GH-90000
            assertEquals(response.getContent(), resp.getContent(), "Content should match"); // GH-90000
        });

        Thread.sleep(100); // GH-90000

        // Check statistics
        CacheStatistics stats = cache.getStatistics(); // GH-90000
        assertEquals(1, stats.hits(), "Should have 1 hit"); // GH-90000
        assertEquals(1, stats.size(), "Cache size should be 1"); // GH-90000
        assertTrue(stats.hitRate() > 0, "Hit rate should be > 0"); // GH-90000

        cache.clear(); // GH-90000
    }

    @Test
    void testCacheExpiration() throws Exception { // GH-90000
        SemanticCache.CacheConfig cacheConfig = SemanticCache.CacheConfig.builder() // GH-90000
                .enabled(true) // GH-90000
                .ttlSeconds(1) // 1 second TTL // GH-90000
                .build(); // GH-90000

        SemanticCache cache = new SemanticCache(cacheConfig); // GH-90000

        AIRequest request = AIRequest.builder() // GH-90000
                .taskType(CHAT) // GH-90000
                .prompt("Test expiration")
                .build(); // GH-90000

        AIResponse response = AIResponse.builder() // GH-90000
                .requestId(request.getRequestId()) // GH-90000
                .modelId("llama3.2")
                .content("Test response")
                .metrics(AIResponse.ResponseMetrics.builder().latencyMs(100).build()) // GH-90000
                .build(); // GH-90000

        cache.put(request, response); // GH-90000

        // Wait for expiration
        Thread.sleep(1500); // GH-90000

        // Should not find expired entry
        Promise<AIResponse> cached = cache.get(request); // GH-90000
        cached.whenComplete((resp, error) -> { // GH-90000
            assertNull(resp, "Should not find expired entry"); // GH-90000
        });

        Thread.sleep(100); // GH-90000
        cache.clear(); // GH-90000
    }

    private static final class TestModelAdapterFactory implements AIModelRouter.ModelAdapterFactory {
        private final Map<String, TestModelAdapter> adapters = new ConcurrentHashMap<>();

        @Override
        public ModelAdapter create(ModelConfig config) {
            return adapters.computeIfAbsent(config.getModelId(), modelId -> new TestModelAdapter(config));
        }

        void fail(String modelId, RuntimeException error) {
            adapter(modelId).error = error;
            adapter(modelId).response = null;
        }

        void succeed(String modelId, String content) {
            TestModelAdapter adapter = adapter(modelId);
            adapter.error = null;
            adapter.response = AIResponse.builder()
                    .requestId("test-req")
                    .modelId(modelId)
                    .content(content)
                    .metrics(AIResponse.ResponseMetrics.builder().latencyMs(1).build())
                    .build();
        }

        private TestModelAdapter adapter(String modelId) {
            return adapters.get(modelId);
        }
    }

    private static final class TestModelAdapter implements ModelAdapter {
        private final ModelConfig config;
        private RuntimeException error;
        private AIResponse response;

        private TestModelAdapter(ModelConfig config) {
            this.config = config;
            this.response = AIResponse.builder()
                    .requestId("default-req")
                    .modelId(config.getModelId())
                    .content("ok")
                    .metrics(AIResponse.ResponseMetrics.builder().latencyMs(1).build())
                    .build();
        }

        @Override
        public Promise<Void> initialize() {
            return Promise.complete();
        }

        @Override
        public Promise<AIResponse> execute(AIRequest request) {
            if (error != null) {
                return Promise.ofException(error);
            }
            return Promise.of(response);
        }

        @Override
        public ModelConfig getConfig() {
            return config;
        }

        @Override
        public Promise<Boolean> isAvailable() {
            return Promise.of(Boolean.TRUE);
        }

        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }
    }
}
