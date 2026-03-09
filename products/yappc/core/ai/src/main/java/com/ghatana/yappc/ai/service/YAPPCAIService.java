package com.ghatana.yappc.ai.service;

import com.ghatana.yappc.ai.integration.AIRouterOutputGenerator;
import com.ghatana.yappc.ai.integration.PromptTemplateEngine;
import com.ghatana.yappc.ai.integration.DefaultResultMapper;
import com.ghatana.yappc.ai.router.*;
import com.ghatana.yappc.ai.router.AIRequest.TaskType;
import com.ghatana.yappc.ai.router.ModelSelector.SelectionStrategy;
import com.ghatana.yappc.ai.router.SemanticCache.CacheConfig;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-level AI service for YAPPC platform.
 * 
 * <p>Provides simplified API for AI-powered operations with:
 * <ul>
 *   <li>Multi-model routing</li>
 *   <li>Semantic caching</li>
 *   <li>Automatic fallback</li>
 *   <li>Performance monitoring</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Initialize service
 * YAPPCAIService aiService = YAPPCAIService.builder()
 *     .selectionStrategy(SelectionStrategy.TASK_BASED)
 *     .cacheEnabled(true)
 *     .build();
 * 
 * aiService.initialize().whenComplete((v, error) -> {
 *     if (error == null) {
 *         // Generate code
 *         aiService.generateCode("Write a Java REST controller for user management")
 *             .whenComplete((code, err) -> {
 *                 System.out.println("Generated code: " + code);
 *             });
 *         
 *         // Analyze code
 *         aiService.analyzeCode(sourceCode)
 *             .whenComplete((analysis, err) -> {
 *                 System.out.println("Analysis: " + analysis);
 *             });
 *     }
 * });
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose High-level AI service for YAPPC
 * @doc.layer service
 * @doc.pattern Facade + Factory
 */
public final class YAPPCAIService {
    
    private static final Logger logger = LoggerFactory.getLogger(YAPPCAIService.class);
    
    private final AIModelRouter router;
    private final AIServiceConfig config;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private volatile boolean initialized = false;
    
    private YAPPCAIService(Builder builder) {
        this.config = builder.config;
        
        // Build router configuration
        AIRouterConfig routerConfig = AIRouterConfig.builder()
            .selectionStrategy(builder.selectionStrategy)
            .cacheConfig(builder.cacheConfig)
            .defaultModel(builder.defaultModel)
            .build();
        
        this.router = new AIModelRouter(routerConfig);
    }
    
    /**
     * Initializes the AI service.
     */
    public Promise<Void> initialize() {
        return router.initialize()
            .whenComplete((v, error) -> {
                if (error == null) {
                    initialized = true;
                    logger.info("YAPPC AI Service initialized");
                } else {
                    logger.error("Failed to initialize YAPPC AI Service", error);
                }
            });
    }
    
    // ========== High-Level AI Operations ==========
    
    /**
     * Generates code based on natural language description.
     * 
     * @param description the code description
     * @return Promise containing generated code
     */
    public Promise<String> generateCode(String description) {
        return generateCode(description, Map.of());
    }
    
    /**
     * Generates code with additional context.
     */
    public Promise<String> generateCode(String description, Map<String, Object> context) {
        AIRequest request = AIRequest.builder()
            .taskType(TaskType.CODE_GENERATION)
            .prompt(buildCodeGenerationPrompt(description, context))
            .context(context)
            .parameters(AIRequest.RequestParameters.builder()
                .temperature(0.2) // Lower temperature for code
                .maxTokens(4096)
                .build())
            .build();
        
        return executeRequest(request)
            .map(response -> extractCode(response.getContent()));
    }
    
    /**
     * Analyzes code for issues, improvements, and best practices.
     */
    public Promise<CodeAnalysis> analyzeCode(String code) {
        return analyzeCode(code, Map.of());
    }
    
    /**
     * Analyzes code with additional context.
     */
    public Promise<CodeAnalysis> analyzeCode(String code, Map<String, Object> context) {
        AIRequest request = AIRequest.builder()
            .taskType(TaskType.CODE_ANALYSIS)
            .prompt(buildCodeAnalysisPrompt(code, context))
            .context(context)
            .parameters(AIRequest.RequestParameters.builder()
                .temperature(0.3)
                .maxTokens(2048)
                .build())
            .build();
        
        return executeRequest(request)
            .map(response -> CodeAnalysis.parse(response.getContent()));
    }
    
    /**
     * Generates unit tests for given code.
     */
    public Promise<String> generateTests(String code) {
        return generateTests(code, Map.of());
    }
    
    /**
     * Generates unit tests with additional context.
     */
    public Promise<String> generateTests(String code, Map<String, Object> context) {
        AIRequest request = AIRequest.builder()
            .taskType(TaskType.TEST_GENERATION)
            .prompt(buildTestGenerationPrompt(code, context))
            .context(context)
            .parameters(AIRequest.RequestParameters.builder()
                .temperature(0.3)
                .maxTokens(4096)
                .build())
            .build();
        
        return executeRequest(request)
            .map(response -> extractCode(response.getContent()));
    }
    
    /**
     * Generates documentation for code or project.
     */
    public Promise<String> generateDocumentation(String code) {
        return generateDocumentation(code, Map.of());
    }
    
    /**
     * Generates documentation with additional context.
     */
    public Promise<String> generateDocumentation(String code, Map<String, Object> context) {
        AIRequest request = AIRequest.builder()
            .taskType(TaskType.DOCUMENTATION)
            .prompt(buildDocumentationPrompt(code, context))
            .context(context)
            .parameters(AIRequest.RequestParameters.builder()
                .temperature(0.5)
                .maxTokens(2048)
                .build())
            .build();
        
        return executeRequest(request)
            .map(AIResponse::getContent);
    }
    
    /**
     * Performs reasoning or planning task.
     */
    public Promise<String> reason(String question) {
        return reason(question, Map.of());
    }
    
    /**
     * Performs reasoning with additional context.
     */
    public Promise<String> reason(String question, Map<String, Object> context) {
        AIRequest request = AIRequest.builder()
            .taskType(TaskType.REASONING)
            .prompt(question)
            .context(context)
            .parameters(AIRequest.RequestParameters.builder()
                .temperature(0.7)
                .maxTokens(2048)
                .build())
            .build();
        
        return executeRequest(request)
            .map(AIResponse::getContent);
    }
    
    /**
     * Gets fast response for simple queries.
     */
    public Promise<String> quickResponse(String query) {
        AIRequest request = AIRequest.builder()
            .taskType(TaskType.FAST_RESPONSE)
            .prompt(query)
            .parameters(AIRequest.RequestParameters.builder()
                .temperature(0.7)
                .maxTokens(512) // Shorter for fast responses
                .build())
            .build();
        
        return executeRequest(request)
            .map(AIResponse::getContent);
    }
    
    // ========== Core Execution ==========
    
    /**
     * Executes an AI request through the router.
     */
    private Promise<AIResponse> executeRequest(AIRequest request) {
        if (!initialized) {
            return Promise.ofException(new IllegalStateException("AI Service not initialized"));
        }
        
        long requestId = requestCounter.incrementAndGet();
        logger.debug("Executing AI request #{}: taskType={}", requestId, request.getTaskType());
        
        return router.route(request)
            .whenComplete((response, error) -> {
                if (error != null) {
                    logger.error("AI request #{} failed", requestId, error);
                } else {
                    logger.debug("AI request #{} completed: model={}, latency={}ms, cacheHit={}", 
                        requestId,
                        response.getModelId(),
                        response.getMetrics().getLatencyMs(),
                        response.isCacheHit());
                }
            });
    }
    
    // ========== Prompt Builders ==========
    
    private String buildCodeGenerationPrompt(String description, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate production-quality code based on this description:\n\n");
        prompt.append(description);
        prompt.append("\n\n");
        
        if (context.containsKey("language")) {
            prompt.append("Language: ").append(context.get("language")).append("\n");
        }
        if (context.containsKey("framework")) {
            prompt.append("Framework: ").append(context.get("framework")).append("\n");
        }
        
        prompt.append("\nRequirements:\n");
        prompt.append("- Follow best practices and design patterns\n");
        prompt.append("- Include proper error handling\n");
        prompt.append("- Add comprehensive comments\n");
        prompt.append("- Ensure code is testable and maintainable\n");
        
        return prompt.toString();
    }
    
    private String buildCodeAnalysisPrompt(String code, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following code for issues, improvements, and best practices:\n\n");
        prompt.append("```\n");
        prompt.append(code);
        prompt.append("\n```\n\n");
        prompt.append("Provide:\n");
        prompt.append("1. Security vulnerabilities\n");
        prompt.append("2. Performance issues\n");
        prompt.append("3. Code quality improvements\n");
        prompt.append("4. Best practice violations\n");
        prompt.append("5. Suggested refactorings\n");
        
        return prompt.toString();
    }
    
    private String buildTestGenerationPrompt(String code, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate comprehensive unit tests for the following code:\n\n");
        prompt.append("```\n");
        prompt.append(code);
        prompt.append("\n```\n\n");
        prompt.append("Requirements:\n");
        prompt.append("- Cover happy path and edge cases\n");
        prompt.append("- Test error handling\n");
        prompt.append("- Use appropriate assertions\n");
        prompt.append("- Follow testing best practices\n");
        
        return prompt.toString();
    }
    
    private String buildDocumentationPrompt(String code, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate comprehensive documentation for the following code:\n\n");
        prompt.append("```\n");
        prompt.append(code);
        prompt.append("\n```\n\n");
        prompt.append("Include:\n");
        prompt.append("- Overview and purpose\n");
        prompt.append("- Parameters and return values\n");
        prompt.append("- Usage examples\n");
        prompt.append("- Important notes and caveats\n");
        
        return prompt.toString();
    }
    
    /**
     * Extracts code from markdown code blocks if present.
     */
    private String extractCode(String content) {
        // Remove markdown code blocks
        String code = content.replaceAll("```[a-zA-Z]*\\n", "").replaceAll("```", "");
        return code.trim();
    }
    
    // ========== Monitoring and Management ==========
    
    /**
     * Gets cache statistics.
     */
    public CacheStatistics getCacheStatistics() {
        return router.getCacheStatistics();
    }
    
    /**
     * Gets available models.
     */
    public Map<String, ModelConfig> getAvailableModels() {
        return router.getAvailableModels();
    }
    
    /**
     * Gets total requests processed.
     */
    public long getTotalRequests() {
        return requestCounter.get();
    }
    
    /**
     * Creates an output generator for agents.
     */
    public <Req, Res> AIRouterOutputGenerator<Req, Res> createOutputGenerator() {
        return new AIRouterOutputGenerator<>(router, 
            PromptTemplateEngine.defaultEngine(), 
            new DefaultResultMapper<>());
    }
    
    /**
     * Shuts down the AI service.
     */
    public Promise<Void> shutdown() {
        return router.shutdown()
            .whenComplete((v, error) -> {
                initialized = false;
                logger.info("YAPPC AI Service shut down");
            });
    }
    
    // ========== Builder ==========
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private AIServiceConfig config = AIServiceConfig.defaults();
        private SelectionStrategy selectionStrategy = SelectionStrategy.TASK_BASED;
        private CacheConfig cacheConfig = CacheConfig.defaults();
        private String defaultModel = "llama3.2";
        
        public Builder config(AIServiceConfig config) {
            this.config = config;
            return this;
        }
        
        public Builder selectionStrategy(SelectionStrategy strategy) {
            this.selectionStrategy = strategy;
            return this;
        }
        
        public Builder cacheConfig(CacheConfig config) {
            this.cacheConfig = config;
            return this;
        }
        
        public Builder cacheEnabled(boolean enabled) {
            this.cacheConfig = CacheConfig.builder()
                .enabled(enabled)
                .maxSize(10000)
                .ttlSeconds(3600)
                .build();
            return this;
        }
        
        public Builder defaultModel(String model) {
            this.defaultModel = model;
            return this;
        }
        
        public YAPPCAIService build() {
            return new YAPPCAIService(this);
        }
    }
    
    // ========== Supporting Classes ==========
    
    /**
     * Configuration for AI service.
     */
    public static class AIServiceConfig {
        private final String ollamaEndpoint;
        private final int maxConcurrentRequests;
        private final long requestTimeoutMs;
        
        private AIServiceConfig(Builder builder) {
            this.ollamaEndpoint = builder.ollamaEndpoint;
            this.maxConcurrentRequests = builder.maxConcurrentRequests;
            this.requestTimeoutMs = builder.requestTimeoutMs;
        }
        
        public String getOllamaEndpoint() { return ollamaEndpoint; }
        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public long getRequestTimeoutMs() { return requestTimeoutMs; }
        
        public static AIServiceConfig defaults() {
            return builder().build();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String ollamaEndpoint = "http://localhost:11434";
            private int maxConcurrentRequests = 10;
            private long requestTimeoutMs = 300000; // 5 minutes
            
            public Builder ollamaEndpoint(String endpoint) {
                this.ollamaEndpoint = endpoint;
                return this;
            }
            
            public Builder maxConcurrentRequests(int max) {
                this.maxConcurrentRequests = max;
                return this;
            }
            
            public Builder requestTimeoutMs(long timeout) {
                this.requestTimeoutMs = timeout;
                return this;
            }
            
            public AIServiceConfig build() {
                return new AIServiceConfig(this);
            }
        }
    }
    
    /**
     * Code analysis result.
     */
    public static class CodeAnalysis {
        private final String summary;
        private final Map<String, Object> findings;
        
        private CodeAnalysis(String summary, Map<String, Object> findings) {
            this.summary = summary;
            this.findings = findings;
        }
        
        public String getSummary() { return summary; }
        public Map<String, Object> getFindings() { return findings; }
        
        public static CodeAnalysis parse(String content) {
            // Simple parsing - in production, use structured output
            return new CodeAnalysis(content, Map.of("raw", content));
        }
    }
}
