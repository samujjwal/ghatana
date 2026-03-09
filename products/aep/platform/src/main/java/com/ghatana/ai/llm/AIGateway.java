package com.ghatana.ai.llm;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Local AIGateway implementation to avoid circular dependencies
 
 *
 * @doc.type class
 * @doc.purpose Aigateway
 * @doc.layer core
 * @doc.pattern Component
*/
public class AIGateway {
    
    public Promise<String> generatePattern(String input) {
        return Promise.of("generated-pattern");
    }
    
    public Promise<Map<String, Object>> analyzeEvent(Object event) {
        return Promise.of(Map.of("analysis", "completed"));
    }
}
