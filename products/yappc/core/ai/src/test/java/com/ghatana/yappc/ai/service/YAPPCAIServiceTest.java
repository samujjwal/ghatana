package com.ghatana.yappc.ai.service;

import com.ghatana.yappc.ai.router.CacheStatistics;
import com.ghatana.yappc.ai.router.ModelConfig;
import com.ghatana.yappc.ai.router.ModelSelector.SelectionStrategy;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for YAPPCAIService.
 * 
 * @doc.type test
 * @doc.purpose AI service functionality validation
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
/**
 * @doc.type class
 * @doc.purpose Handles yappcai service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
public class YAPPCAIServiceTest extends EventloopTestBase {
    
    private YAPPCAIService aiService;
    
    @BeforeAll
    void setUp() throws Exception {
        // Initialize AI service
        aiService = YAPPCAIService.builder()
            .selectionStrategy(SelectionStrategy.TASK_BASED)
            .cacheEnabled(true)
            .build();
        
        // Wait for initialization
        Promise<Void> init = aiService.initialize();
        Thread.sleep(2000); // Give Ollama time to initialize
    }
    
    @AfterAll
    void tearDown() throws Exception {
        if (aiService != null) {
            aiService.shutdown();
        }
    }
    
    @Test
    @DisplayName("Should initialize successfully")
    void shouldInitialize() {
        assertNotNull(aiService);
        Map<String, ModelConfig> models = aiService.getAvailableModels();
        assertFalse(models.isEmpty(), "Should have registered models");
        assertTrue(models.containsKey("llama3.2"), "Should have llama3.2");
        assertTrue(models.containsKey("codellama"), "Should have codellama");
    }
    
    @Test
    @DisplayName("Should generate code")
    void shouldGenerateCode() throws Exception {
        String description = "Write a Java method to calculate factorial recursively";
        
        Promise<String> codePromise = aiService.generateCode(description);
        
        // Wait for result
        final String[] result = {null};
        codePromise.whenComplete((code, error) -> {
            assertNull(error, "Should not have errors");
            assertNotNull(code, "Should generate code");
            assertTrue(code.contains("factorial"), "Code should contain factorial logic");
            result[0] = code;
        });
        
        Thread.sleep(3000); // Wait for async completion
        assertNotNull(result[0], "Should have received code");
    }
    
    @Test
    @DisplayName("Should generate code with context")
    void shouldGenerateCodeWithContext() throws Exception {
        Map<String, Object> context = Map.of(
            "language", "Java",
            "framework", "Spring Boot"
        );
        
        Promise<String> codePromise = aiService.generateCode(
            "Create a REST controller for user CRUD operations", 
            context
        );
        
        final String[] result = {null};
        codePromise.whenComplete((code, error) -> {
            assertNull(error);
            assertNotNull(code);
            result[0] = code;
        });
        
        Thread.sleep(3000);
        assertNotNull(result[0]);
    }
    
    @Test
    @DisplayName("Should analyze code")
    void shouldAnalyzeCode() throws Exception {
        String code = """
            public void processData(String input) {
                String[] parts = input.split(",");
                int result = Integer.parseInt(parts[0]) / Integer.parseInt(parts[1]);
                System.out.println(result);
            }
            """;
        
        Promise<YAPPCAIService.CodeAnalysis> analysisPromise = aiService.analyzeCode(code);
        
        final YAPPCAIService.CodeAnalysis[] result = {null};
        analysisPromise.whenComplete((analysis, error) -> {
            assertNull(error);
            assertNotNull(analysis);
            assertNotNull(analysis.getSummary());
            result[0] = analysis;
        });
        
        Thread.sleep(3000);
        assertNotNull(result[0]);
    }
    
    @Test
    @DisplayName("Should generate tests")
    void shouldGenerateTests() throws Exception {
        String code = """
            public int add(int a, int b) {
                return a + b;
            }
            """;
        
        Promise<String> testsPromise = aiService.generateTests(code);
        
        final String[] result = {null};
        testsPromise.whenComplete((tests, error) -> {
            assertNull(error);
            assertNotNull(tests);
            assertTrue(tests.contains("@Test") || tests.contains("test"), 
                "Should generate test code");
            result[0] = tests;
        });
        
        Thread.sleep(3000);
        assertNotNull(result[0]);
    }
    
    @Test
    @DisplayName("Should generate documentation")
    void shouldGenerateDocumentation() throws Exception {
        String code = """
            public List<User> findActiveUsers(int limit) {
                return userRepository.findByStatusAndLimit("ACTIVE", limit);
            }
            """;
        
        Promise<String> docsPromise = aiService.generateDocumentation(code);
        
        final String[] result = {null};
        docsPromise.whenComplete((docs, error) -> {
            assertNull(error);
            assertNotNull(docs);
            result[0] = docs;
        });
        
        Thread.sleep(3000);
        assertNotNull(result[0]);
    }
    
    @Test
    @DisplayName("Should perform reasoning")
    void shouldPerformReasoning() throws Exception {
        String question = "What are the trade-offs between microservices and monolithic architecture?";
        
        Promise<String> reasoningPromise = aiService.reason(question);
        
        final String[] result = {null};
        reasoningPromise.whenComplete((answer, error) -> {
            assertNull(error);
            assertNotNull(answer);
            assertTrue(answer.length() > 100, "Should provide detailed answer");
            result[0] = answer;
        });
        
        Thread.sleep(5000); // Reasoning may take longer
        assertNotNull(result[0]);
    }
    
    @Test
    @DisplayName("Should provide quick response")
    void shouldProvideQuickResponse() throws Exception {
        Promise<String> responsePromise = aiService.quickResponse("What is REST API?");
        
        final String[] result = {null};
        long startTime = System.currentTimeMillis();
        
        responsePromise.whenComplete((response, error) -> {
            long endTime = System.currentTimeMillis();
            assertNull(error);
            assertNotNull(response);
            assertTrue(endTime - startTime < 5000, "Quick response should be fast");
            result[0] = response;
        });
        
        Thread.sleep(3000);
        assertNotNull(result[0]);
    }
    
    @Test
    @DisplayName("Should track cache statistics")
    void shouldTrackCacheStatistics() {
        CacheStatistics stats = aiService.getCacheStatistics();
        assertNotNull(stats);
        assertTrue(stats.size() >= 0);
        assertTrue(stats.hits() >= 0);
        assertTrue(stats.misses() >= 0);
        assertTrue(stats.hitRate() >= 0.0 && stats.hitRate() <= 1.0);
    }
    
    @Test
    @DisplayName("Should track total requests")
    void shouldTrackTotalRequests() {
        long initialCount = aiService.getTotalRequests();
        assertTrue(initialCount >= 0);
    }
    
    @Test
    @DisplayName("Should create output generator")
    void shouldCreateOutputGenerator() {
        var generator = aiService.<Map<String, Object>, Map<String, Object>>createOutputGenerator();
        assertNotNull(generator);
        assertNotNull(generator.getRouter());
    }
}
