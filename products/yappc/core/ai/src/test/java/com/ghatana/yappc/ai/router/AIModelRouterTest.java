package com.ghatana.yappc.ai.router;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
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
    void testRouterInitialization() throws Exception {
        AIRouterConfig config = AIRouterConfig.defaults();
        AIModelRouter router = new AIModelRouter(config);

        Promise<Void> init = router.initialize();
        init.whenComplete((v, error) -> {
            assertNull(error, "Initialization should succeed");
        });

        // Wait for completion
        Thread.sleep(2000);

        // Check available models
        assertEquals(4, router.getAvailableModels().size(), "Should have 4 models");
        assertTrue(router.getAvailableModels().containsKey("llama3.2"), "Should have llama3.2");
        assertTrue(router.getAvailableModels().containsKey("codellama"), "Should have codellama");
        assertTrue(router.getAvailableModels().containsKey("mistral"), "Should have mistral");
        assertTrue(router.getAvailableModels().containsKey("phi-3"), "Should have phi-3");

        router.shutdown();
    }

    @Test
    void testModelSelection() {
        AIRouterConfig config = AIRouterConfig.defaults();
        AIModelRouter router = new AIModelRouter(config);

        // Test code generation selection
        AIRequest codeRequest = AIRequest.builder()
                .taskType(CODE_GENERATION)
                .prompt("Write a Java function to calculate fibonacci")
                .build();

        // Should select codellama for code tasks
        // (This test assumes models are registered but not necessarily available)

        router.shutdown();
    }

    @Test
    void testSemanticCache() throws Exception {
        SemanticCache.CacheConfig cacheConfig = SemanticCache.CacheConfig.builder()
                .enabled(true)
                .maxSize(100)
                .ttlSeconds(60)
                .build();

        SemanticCache cache = new SemanticCache(cacheConfig);

        // Create request and response
        AIRequest request = AIRequest.builder()
                .taskType(CHAT)
                .prompt("Hello, how are you?")
                .build();

        AIResponse response = AIResponse.builder()
                .requestId(request.getRequestId())
                .modelId("llama3.2")
                .content("I'm doing well, thank you!")
                .metrics(AIResponse.ResponseMetrics.builder()
                        .latencyMs(100)
                        .tokenCount(10)
                        .build())
                .build();

        // Put in cache
        cache.put(request, response);

        // Get from cache
        Promise<AIResponse> cached = cache.get(request);
        cached.whenComplete((resp, error) -> {
            assertNull(error, "Cache get should succeed");
            assertNotNull(resp, "Should find cached response");
            assertEquals(response.getContent(), resp.getContent(), "Content should match");
        });

        Thread.sleep(100);

        // Check statistics
        CacheStatistics stats = cache.getStatistics();
        assertEquals(1, stats.hits(), "Should have 1 hit");
        assertEquals(1, stats.size(), "Cache size should be 1");
        assertTrue(stats.hitRate() > 0, "Hit rate should be > 0");

        cache.clear();
    }

    @Test
    void testCacheExpiration() throws Exception {
        SemanticCache.CacheConfig cacheConfig = SemanticCache.CacheConfig.builder()
                .enabled(true)
                .ttlSeconds(1) // 1 second TTL
                .build();

        SemanticCache cache = new SemanticCache(cacheConfig);

        AIRequest request = AIRequest.builder()
                .taskType(CHAT)
                .prompt("Test expiration")
                .build();

        AIResponse response = AIResponse.builder()
                .requestId(request.getRequestId())
                .modelId("llama3.2")
                .content("Test response")
                .metrics(AIResponse.ResponseMetrics.builder().latencyMs(100).build())
                .build();

        cache.put(request, response);

        // Wait for expiration
        Thread.sleep(1500);

        // Should not find expired entry
        Promise<AIResponse> cached = cache.get(request);
        cached.whenComplete((resp, error) -> {
            assertNull(resp, "Should not find expired entry");
        });

        Thread.sleep(100);
        cache.clear();
    }
}
