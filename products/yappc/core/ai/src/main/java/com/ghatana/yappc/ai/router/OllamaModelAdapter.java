package com.ghatana.yappc.ai.router;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Model adapter for Ollama local LLM service.
 * 
 * <p>Connects to Ollama API running locally (default: http://localhost:11434).
 * Supports all Ollama models: llama3.2, codellama, mistral, phi-3.
 * 
 * @doc.type class
 * @doc.purpose Ollama API integration
 
 * @doc.layer core
 * @doc.pattern Adapter
*/
public final class OllamaModelAdapter implements ModelAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(OllamaModelAdapter.class);
    
    private final ModelConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile boolean initialized = false;
    
    public OllamaModelAdapter(ModelConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = JsonUtils.getDefaultMapper();
    }
    
    @Override
    public Promise<Void> initialize() {
        return Promise.ofCallback(cb -> {
            if (initialized) {
                cb.set(null);
                return;
            }
            
            logger.info("Initializing Ollama adapter for model: {}", config.getModelId());
            
            // Check if model is available
            isAvailable()
                .whenComplete((available, error) -> {
                    if (error != null) {
                        logger.error("Failed to initialize Ollama adapter", error);
                        cb.setException(error);
                    } else if (!available) {
                        logger.warn("Model {} not available in Ollama", config.getModelId());
                        // Still initialize, model might be pulled later
                        initialized = true;
                        cb.set(null);
                    } else {
                        initialized = true;
                        logger.info("Ollama adapter initialized for {}", config.getModelId());
                        cb.set(null);
                    }
                });
        });
    }
    
    @Override
    public Promise<AIResponse> execute(AIRequest request) {
        return Promise.ofCallback(cb -> {
            if (!initialized) {
                cb.setException(new IllegalStateException("Adapter not initialized"));
                return;
            }
            
            long startTime = System.currentTimeMillis();
            
            try {
                // Build Ollama API request
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", config.getModelId());
                requestBody.put("prompt", request.getPrompt());
                requestBody.put("stream", false);
                
                // Add parameters
                ObjectNode options = requestBody.putObject("options");
                options.put("temperature", request.getParameters().getTemperature());
                options.put("num_predict", request.getParameters().getMaxTokens());
                options.put("top_p", request.getParameters().getTopP());
                options.put("top_k", request.getParameters().getTopK());
                
                // Add stop sequences
                if (!request.getParameters().getStopSequences().isEmpty()) {
                    var stopArray = requestBody.putArray("stop");
                    request.getParameters().getStopSequences().forEach(stopArray::add);
                }
                
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                
                // Send HTTP request
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.getEndpoint() + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(300))
                    .build();
                
                httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        long latency = System.currentTimeMillis() - startTime;
                        
                        if (response.statusCode() != 200) {
                            cb.setException(new RuntimeException(
                                "Ollama API error: " + response.statusCode() + " - " + response.body()));
                            return;
                        }
                        
                        try {
                            // Parse response
                            ObjectNode responseBody = (ObjectNode) objectMapper.readTree(response.body());
                            String content = responseBody.get("response").asText();
                            
                            // Build AI response
                            AIResponse aiResponse = AIResponse.builder()
                                .requestId(request.getRequestId())
                                .modelId(config.getModelId())
                                .content(content)
                                .metrics(AIResponse.ResponseMetrics.builder()
                                    .latencyMs(latency)
                                    .tokenCount(responseBody.path("eval_count").asInt(0))
                                    .promptTokens(responseBody.path("prompt_eval_count").asInt(0))
                                    .completionTokens(responseBody.path("eval_count").asInt(0))
                                    .cost(0.0) // Ollama is free
                                    .build())
                                .addMetadata("model", responseBody.path("model").asText())
                                .addMetadata("total_duration", responseBody.path("total_duration").asLong())
                                .build();
                            
                            logger.debug("Ollama response received in {}ms", latency);
                            cb.set(aiResponse);
                            
                        } catch (Exception e) {
                            logger.error("Failed to parse Ollama response", e);
                            cb.setException(e);
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("Ollama API request failed", e);
                        cb.setException((Exception) e);
                        return null;
                    });
                    
            } catch (Exception e) {
                logger.error("Failed to execute Ollama request", e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public ModelConfig getConfig() {
        return config;
    }
    
    @Override
    public Promise<Boolean> isAvailable() {
        return Promise.ofCallback(cb -> {
            try {
                // Check if Ollama is running
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getEndpoint() + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
                
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                ObjectNode body = (ObjectNode) objectMapper.readTree(response.body());
                                var models = body.get("models");
                                
                                if (models != null && models.isArray()) {
                                    // Check if our model is in the list
                                    boolean found = false;
                                    for (var model : models) {
                                        String name = model.get("name").asText();
                                        if (name.startsWith(config.getModelId())) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    cb.set(found);
                                } else {
                                    cb.set(false);
                                }
                            } catch (Exception e) {
                                logger.error("Failed to parse Ollama tags response", e);
                                cb.set(false);
                            }
                        } else {
                            cb.set(false);
                        }
                    })
                    .exceptionally(e -> {
                        logger.debug("Ollama not available: {}", e.getMessage());
                        cb.set(false);
                        return null;
                    });
                    
            } catch (Exception e) {
                logger.debug("Failed to check Ollama availability", e);
                cb.set(false);
            }
        });
    }
    
    @Override
    public Promise<Void> shutdown() {
        return Promise.ofCallback(cb -> {
            logger.info("Shutting down Ollama adapter for {}", config.getModelId());
            initialized = false;
            cb.set(null);
        });
    }
}
