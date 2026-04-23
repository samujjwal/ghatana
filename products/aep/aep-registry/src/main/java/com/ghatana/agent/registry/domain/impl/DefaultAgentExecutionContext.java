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
@SuppressWarnings("removal") // Uses deprecated AEP SecurityContext; migration to platform SecurityContext tracked separately
/**
 * @doc.type record
 * @doc.purpose Provides default agent execution context functionality.
 * @doc.layer product
 * @doc.pattern Agent
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

    @Override
    public AgentExecutionContext copy() {
        return new DefaultAgentExecutionContext(
                tenantId, userId, securityContext, correlationId,
                Map.copyOf(metadata), Set.copyOf(enabledCapabilities),
                timeoutMs, createdAt);
    }
}
