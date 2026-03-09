package com.ghatana.products.multi.agent.orchestrator.config;

import lombok.Data;

/**
 * Configuration for the orchestrator application.
 * 
 * <p>This class holds all configuration properties for the orchestrator.
 * In a real application, this would be loaded from Typesafe Config.
 * 
 * @author Platform Team
 * @since 1.0.0
 */
@Data
public class OrchestratorAppConfig {
    
    /**
     * HTTP server port.
     */
    private int httpPort = 8080;
    
    /**
     * Maximum number of concurrent requests.
     */
    private int maxConcurrentRequests = 100;
    
    /**
     * Maximum request size in bytes.
     */
    private int maxRequestSize = 1024 * 1024; // 1MB
    
    /**
     * Health check path.
     */
    private String healthPath = "/health";
}
