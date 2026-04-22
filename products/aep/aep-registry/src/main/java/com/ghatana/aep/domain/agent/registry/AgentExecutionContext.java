/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.domain.agent.registry;

import java.util.Map;
import java.util.Set;

/**
 * Context for agent execution containing tenant, user, and security information.
 *
 * @doc.type interface
 * @doc.purpose Execution context contract for agent invocations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@SuppressWarnings("removal") // References deprecated AEP SecurityContext; migration to platform SecurityContext tracked separately
public interface AgentExecutionContext {

    /**
     * Get the tenant ID for this execution context.
     */
    String tenantId();

    /**
     * Get the user ID for this execution context.
     */
    String userId();

    /**
     * Get the security context.
     */
    SecurityContext securityContext();

    /**
     * Get the correlation ID for tracing.
     */
    String correlationId();

    /**
     * Get execution metadata.
     */
    Map<String, Object> metadata();

    /**
     * Get the set of enabled capabilities.
     */
    Set<String> enabledCapabilities();

    /**
     * Get execution timeout in milliseconds.
     */
    long timeoutMs();

    /**
     * Get execution creation time.
     */
    long createdAt();

    /**
     * Create a copy of this context.
     */
    AgentExecutionContext copy();
}
