/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.1 — Custom Agent SPI with ServiceLoader discovery.
 * Allows external agent implementations to be discovered and registered automatically.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;

import java.util.Set;

/**
 * Service Provider Interface for external agent implementations.
 *
 * <p>Third-party agent providers implement this interface and register via
 * {@code META-INF/services/com.ghatana.agent.spi.AgentProvider}. The platform
 * discovers providers at startup via {@link java.util.ServiceLoader}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // 1. Implement the SPI
 * public class MyFraudAgentProvider implements AgentProvider {
 *     public String getProviderId() { return "acme-fraud-detector"; }
 *     public Set<AgentType> getSupportedTypes() { return Set.of(AgentType.PROBABILISTIC); }
 *     public TypedAgent<?, ?> createAgent(AgentConfig config) { return new MyFraudAgent(config); }
 *     public AgentDescriptor describe() { return AgentDescriptor.builder()... }
 * }
 *
 * // 2. Register in META-INF/services/com.ghatana.agent.spi.AgentProvider:
 * //    com.acme.MyFraudAgentProvider
 *
 * // 3. The platform will auto-discover and register the agent
 * }</pre>
 *
 * <h2>Discovery Lifecycle</h2>
 * <ol>
 *   <li>{@link AgentProviderRegistry#discoverProviders()} scans classpath via ServiceLoader</li>
 *   <li>Providers are sorted by {@link #priority()} (lower = higher priority)</li>
 *   <li>Each provider's {@link #createAgent(AgentConfig)} is called to instantiate agents</li>
 *   <li>Created agents are registered in the {@link com.ghatana.agent.registry.AgentFrameworkRegistry}</li>
 * </ol>
 *
 * @see AgentProviderRegistry Discovery and registration coordinator
 * @see com.ghatana.agent.TypedAgent The core agent interface
 *
 * @doc.type interface
 * @doc.purpose SPI for pluggable agent creation and lifecycle management
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AgentProvider {

    /**
     * Unique identifier for this provider.
     * Used for deduplication and logging.
     *
     * @return provider identifier (e.g., "acme-fraud-detector", "ghatana-ml-suite")
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
     * @throws IllegalArgumentException if the config is invalid for this provider
     */
    TypedAgent<?, ?> createAgent(AgentConfig config);

    /**
     * Checks whether this provider supports the given agent type.
     *
     * @param type the agent type to check
     * @return true if this provider can create agents of the given type
     */
    default boolean supports(AgentType type) {
        return getSupportedTypes().contains(type);
    }

    /**
     * Returns a descriptor advertising this provider's agent capabilities.
     * Used for registry metadata and discovery queries.
     *
     * @return agent descriptor
     */
    AgentDescriptor describe();

    /**
     * Priority for provider ordering when multiple providers are discovered.
     * Lower values = higher priority. Default is 1000.
     *
     * @return priority value
     */
    default int priority() {
        return 1000;
    }

    /**
     * Whether this provider is enabled. Disabled providers are skipped during discovery.
     * Can be used for feature flags or environment-specific enablement.
     *
     * @return true if enabled
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Optional version string for this provider.
     *
     * @return version string (e.g., "1.0.0", "2.3.1-SNAPSHOT")
     */
    default String getVersion() {
        return "1.0.0";
    }
}
