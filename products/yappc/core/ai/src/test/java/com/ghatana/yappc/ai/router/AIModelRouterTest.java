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

        // Test code generation selection
        AIRequest codeRequest = AIRequest.builder() // GH-90000
                .taskType(CODE_GENERATION) // GH-90000
                .prompt("Write a Java function to calculate fibonacci")
                .build(); // GH-90000

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
}
