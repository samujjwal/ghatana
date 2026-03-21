/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.api.AgentConfig;
import com.ghatana.agent.api.AgentDescriptor;
import com.ghatana.agent.api.AgentType;
import com.ghatana.agent.api.TypedAgent;

import java.util.Set;

/**
 * Service Provider Interface for external agent implementations.
 *
 * <p>Third-party agent providers implement this interface and register via
 * {@code META-INF/services/com.ghatana.agent.spi.AgentProvider}. The platform
 * discovers providers at startup via {@link java.util.ServiceLoader}.
 *
 * @doc.type interface
 * @doc.purpose SPI for pluggable agent creation and lifecycle management
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AgentProvider {

    /**
     * Unique identifier for this provider.
     *
     * @return provider identifier
     */
    String getProviderId();

    /**
     * Human-readable name of this provider.
     *
     * @return display name
     */
    String getProviderName();

    /**
     * The set of agent types this provider can create.
     *
     * @return supported agent types
     */
    Set<AgentType> getSupportedTypes();

    /**
     * Creates an agent instance from the given configuration.
     *
     * @param config agent configuration
     * @return the created agent
     */
    TypedAgent<?, ?> createAgent(AgentConfig config);

    /**
     * Checks whether this provider supports the given agent type.
     */
    default boolean supports(AgentType type) {
        return getSupportedTypes().contains(type);
    }

    /**
     * Returns a descriptor advertising this provider's agent capabilities.
     *
     * @return agent descriptor
     */
    AgentDescriptor describe();

    /**
     * Priority for provider ordering. Lower values = higher priority.
     */
    default int priority() {
        return 1000;
    }

    /**
     * Whether this provider is enabled.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Version string for this provider.
     */
    default String getVersion() {
        return "1.0.0";
    }
}
