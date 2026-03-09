package com.ghatana.yappc.ai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.ai.router.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of ResultMapper.
 * 
 * <p>Maps AI responses using JSON deserialization or dynamic object creation.
 * 
 * @param <T> the result type
 * 
 * @doc.type class
 * @doc.purpose Default AI response mapping
 
 * @doc.layer core
 * @doc.pattern Mapper
*/
public final class DefaultResultMapper<T> implements ResultMapper<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultResultMapper.class);
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    
    @Override
    @SuppressWarnings("unchecked")
    public <Req> T mapResponse(AIResponse response, Req request) {
        String content = response.getContent();
        
        try {
            // Try to parse as JSON if content looks like JSON
            if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
                // Attempt to deserialize to Map or target type
                return (T) objectMapper.readValue(content, Map.class);
            }
            
            // Otherwise, wrap in a result object
            Map<String, Object> result = new HashMap<>();
            result.put("content", content);
            result.put("modelId", response.getModelId());
            result.put("latencyMs", response.getMetrics().getLatencyMs());
            result.put("cacheHit", response.isCacheHit());
            
            return (T) result;
            
        } catch (Exception e) {
            logger.warn("Failed to parse AI response as JSON, returning as string", e);
            
            // Fallback: return content as-is wrapped in map
            Map<String, Object> result = new HashMap<>();
            result.put("content", content);
            result.put("error", "Failed to parse response: " + e.getMessage());
            
            return (T) result;
        }
    }
}
