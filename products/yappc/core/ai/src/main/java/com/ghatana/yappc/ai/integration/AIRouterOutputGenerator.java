package com.ghatana.yappc.ai.integration;

import com.ghatana.yappc.ai.router.*;
import com.ghatana.yappc.ai.router.AIRequest.TaskType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * OutputGenerator implementation that routes requests through the AI Model Router.
 * 
 * <p>Bridges YAPPC SDLC agents with the multi-model AI routing system.
 * Intelligently selects the best model based on the agent's task type.
 * 
 * <p>Example usage:
 * <pre>{@code
 * AIModelRouter router = new AIModelRouter(AIRouterConfig.defaults());
 * router.initialize().whenComplete((v, error) -> {
 *     OutputGenerator<StepRequest, StepResult> generator = 
 *         new AIRouterOutputGenerator(router);
 *     
 *     YAPPCAgentBase agent = new CodeGenerationAgent(
 *         "code-gen-1",
 *         "GENERATE_CODE",
 *         stepContract,
 *         generator
 *     );
 * });
 * }</pre>
 * 
 * @param <Req> the request type
 * @param <Res> the result type
 * 
 * @doc.type class
 * @doc.purpose AI-powered output generation for SDLC agents
 * @doc.layer integration
 * @doc.pattern Adapter + Strategy
 */
public final class AIRouterOutputGenerator<Req, Res> {
    
    private static final Logger logger = LoggerFactory.getLogger(AIRouterOutputGenerator.class);
    
    private final AIModelRouter router;
    private final PromptTemplateEngine templateEngine;
    private final ResultMapper<Res> resultMapper;
    
    public AIRouterOutputGenerator(AIModelRouter router) {
        this(router, PromptTemplateEngine.defaultEngine(), new DefaultResultMapper<>());
    }
    
    public AIRouterOutputGenerator(
            AIModelRouter router,
            PromptTemplateEngine templateEngine,
            ResultMapper<Res> resultMapper) {
        this.router = router;
        this.templateEngine = templateEngine;
        this.resultMapper = resultMapper;
    }
    
    /**
     * Generates output by routing the request through the AI Model Router.
     * 
     * @param request the agent request
     * @param context the execution context
     * @return Promise containing the generated result
     */
    public Promise<Res> generate(Req request, Map<String, Object> context) {
        return Promise.ofCallback(cb -> {
            try {
                // Determine task type from request
                TaskType taskType = determineTaskType(request, context);
                
                // Build prompt from template
                String prompt = templateEngine.buildPrompt(request, context);
                
                // Create AI request
                AIRequest aiRequest = AIRequest.builder()
                    .taskType(taskType)
                    .prompt(prompt)
                    .context(context)
                    .parameters(buildParameters(request, context))
                    .build();
                
                logger.debug("Routing AI request: taskType={}, prompt length={}", 
                    taskType, prompt.length());
                
                // Route through AI Model Router
                router.route(aiRequest)
                    .whenComplete((aiResponse, error) -> {
                        if (error != null) {
                            logger.error("AI routing failed", error);
                            cb.setException(error);
                        } else {
                            try {
                                // Map AI response to agent result
                                Res result = resultMapper.mapResponse(aiResponse, request);
                                
                                logger.debug("AI response received: model={}, latency={}ms, cacheHit={}", 
                                    aiResponse.getModelId(), 
                                    aiResponse.getMetrics().getLatencyMs(),
                                    aiResponse.isCacheHit());
                                
                                cb.set(result);
                            } catch (Exception e) {
                                logger.error("Failed to map AI response to result", e);
                                cb.setException(e);
                            }
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to generate output", e);
                cb.setException(e);
            }
        });
    }
    
    /**
     * Determines the appropriate task type based on request and context.
     */
    private TaskType determineTaskType(Req request, Map<String, Object> context) {
        // Extract step name or task type from context
        String stepName = (String) context.getOrDefault("stepName", "");
        String taskCategory = (String) context.getOrDefault("taskCategory", "");
        
        // Map to TaskType
        return switch (stepName.toLowerCase()) {
            case String s when s.contains("implement") || s.contains("code") -> TaskType.CODE_GENERATION;
            case String s when s.contains("analyze") || s.contains("review") -> TaskType.CODE_ANALYSIS;
            case String s when s.contains("test") -> TaskType.TEST_GENERATION;
            case String s when s.contains("reason") || s.contains("plan") -> TaskType.REASONING;
            case String s when s.contains("fast") || s.contains("quick") -> TaskType.FAST_RESPONSE;
            case String s when s.contains("document") -> TaskType.DOCUMENTATION;
            default -> TaskType.GENERAL;
        };
    }
    
    /**
     * Builds AI request parameters from agent request and context.
     */
    private AIRequest.RequestParameters buildParameters(Req request, Map<String, Object> context) {
        // Extract parameters from context or use defaults
        double temperature = (double) context.getOrDefault("temperature", 0.7);
        int maxTokens = (int) context.getOrDefault("maxTokens", 2048);
        double topP = (double) context.getOrDefault("topP", 0.9);
        
        return AIRequest.RequestParameters.builder()
            .temperature(temperature)
            .maxTokens(maxTokens)
            .topP(topP)
            .build();
    }
    
    /**
     * Gets the underlying AI Model Router for metrics and configuration.
     */
    public AIModelRouter getRouter() {
        return router;
    }
    
    /**
     * Gets current cache statistics.
     */
    public CacheStatistics getCacheStatistics() {
        return router.getCacheStatistics();
    }
}
