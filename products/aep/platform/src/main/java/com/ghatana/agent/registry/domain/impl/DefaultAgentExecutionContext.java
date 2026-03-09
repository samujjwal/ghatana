package com.ghatana.agent.registry.domain.impl;

import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.aep.domain.agent.registry.SecurityContext;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of AgentExecutionContext providing all necessary
 * context information for agent execution.
 */
public record DefaultAgentExecutionContext(
        String tenantId,
        String userId,
        SecurityContext securityContext,
        String correlationId,
        Map<String, Object> metadata,
        Set<String> enabledCapabilities,
        long timeoutMs,
        long createdAt
) implements AgentExecutionContext {
    
    /**
     * Create a basic execution context with minimal required information
     */
    public static AgentExecutionContext basic(String tenantId, String userId) {
        return new DefaultAgentExecutionContext(
                tenantId,
                userId,
                null,
                UUID.randomUUID().toString(),
                Map.of(),
                Set.of(),
                30000, // 30 second default timeout
                System.currentTimeMillis()
        );
    }
    
    /**
     * Create an execution context with security information
     */
    public static AgentExecutionContext withSecurity(String tenantId, String userId, SecurityContext securityContext) {
        return new DefaultAgentExecutionContext(
                tenantId,
                userId,
                securityContext,
                UUID.randomUUID().toString(),
                Map.of(),
                Set.of(),
                30000,
                System.currentTimeMillis()
        );
    }
}