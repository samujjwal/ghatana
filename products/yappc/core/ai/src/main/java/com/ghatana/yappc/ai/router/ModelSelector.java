package com.ghatana.yappc.ai.router;

import com.ghatana.yappc.ai.router.AIRequest.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Intelligent model selector based on task characteristics.
 * 
 * <p>Selection strategy:
 * <ul>
 *   <li>CODE_GENERATION, CODE_ANALYSIS → codellama</li>
 *   <li>FAST_RESPONSE → phi-3</li>
 *   <li>REASONING → mistral</li>
 *   <li>CHAT, GENERAL → llama3.2</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Intelligent AI model selection
 
 * @doc.layer core
 * @doc.pattern Enum
*/
public final class ModelSelector {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelSelector.class);
    
    private final SelectionStrategy strategy;
    
    public ModelSelector(SelectionStrategy strategy) {
        this.strategy = strategy;
    }
    
    /**
     * Selects the best model for the given request.
     * 
     * @param request the AI request
     * @param availableModels available model configurations
     * @return selected model ID
     */
    public String selectModel(AIRequest request, Map<String, ModelConfig> availableModels) {
        return switch (strategy) {
            case TASK_BASED -> selectByTask(request.getTaskType(), availableModels);
            case CAPABILITY_BASED -> selectByCapability(request, availableModels);
            case COST_OPTIMIZED -> selectByCost(request, availableModels);
            case PERFORMANCE_OPTIMIZED -> selectByPerformance(request, availableModels);
        };
    }
    
    /**
     * Selects model based on task type.
     */
    private String selectByTask(TaskType taskType, Map<String, ModelConfig> models) {
        String selected = switch (taskType) {
            case CODE_GENERATION, CODE_ANALYSIS, TEST_GENERATION -> {
                if (models.containsKey("codellama")) yield "codellama";
                yield "llama3.2";
            }
            case FAST_RESPONSE -> {
                if (models.containsKey("phi-3")) yield "phi-3";
                yield "mistral";
            }
            case REASONING -> {
                if (models.containsKey("mistral")) yield "mistral";
                yield "llama3.2";
            }
            case CHAT, GENERAL, DOCUMENTATION -> "llama3.2";
        };
        
        logger.debug("Selected {} for task type {}", selected, taskType);
        return selected;
    }
    
    /**
     * Selects model based on required capabilities.
     */
    private String selectByCapability(AIRequest request, Map<String, ModelConfig> models) {
        TaskType taskType = request.getTaskType();
        String requiredCapability = mapTaskToCapability(taskType);
        
        // Find models with required capability
        List<ModelConfig> candidates = models.values().stream()
            .filter(config -> config.hasCapability(requiredCapability))
            .toList();
        
        if (candidates.isEmpty()) {
            logger.warn("No models with capability {}, using default", requiredCapability);
            return "llama3.2";
        }
        
        // Select first candidate (could add scoring logic)
        String selected = candidates.get(0).getModelId();
        logger.debug("Selected {} for capability {}", selected, requiredCapability);
        return selected;
    }
    
    /**
     * Selects model optimized for cost.
     */
    private String selectByCost(AIRequest request, Map<String, ModelConfig> models) {
        // For local Ollama, all models are free, so use fastest
        return selectByPerformance(request, models);
    }
    
    /**
     * Selects model optimized for performance.
     */
    private String selectByPerformance(AIRequest request, Map<String, ModelConfig> models) {
        // Prefer smaller, faster models for simple tasks
        TaskType taskType = request.getTaskType();
        
        return switch (taskType) {
            case FAST_RESPONSE -> models.containsKey("phi-3") ? "phi-3" : "mistral";
            case CODE_GENERATION, CODE_ANALYSIS -> models.containsKey("codellama") ? "codellama" : "llama3.2";
            default -> "llama3.2";
        };
    }
    
    /**
     * Maps task type to capability string.
     */
    private String mapTaskToCapability(TaskType taskType) {
        return switch (taskType) {
            case CODE_GENERATION, CODE_ANALYSIS, TEST_GENERATION -> "code";
            case FAST_RESPONSE -> "fast";
            case REASONING -> "reasoning";
            default -> "general";
        };
    }
    
    /**
     * Model selection strategies.
     */
    public enum SelectionStrategy {
        /** Select based on task type (default) */
        TASK_BASED,
        
        /** Select based on model capabilities */
        CAPABILITY_BASED,
        
        /** Select to minimize cost */
        COST_OPTIMIZED,
        
        /** Select for best performance */
        PERFORMANCE_OPTIMIZED
    }
}
