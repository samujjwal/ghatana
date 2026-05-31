/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;

import java.util.Set;

/**
 * Service Provider Interface for named agent implementations resolved via
 * {@code implementationRef}.
 *
 * <p>Products register an {@code AgentLogicProvider} that maps one or more
 * {@code implementationRef} strings to concrete {@link TypedAgent} instances.
 * The runtime resolves refs through the provider registry at
 * materialization time (YAML → provider → agent instance).
 *
 * <h3>implementationRef format</h3>
 * <pre>{@code <provider-id>:<qualified-agent-id>}</pre>
 * Example: {@code yappc-java:agent.yappc.java-expert}
 *
 * <h3>Registration</h3>
 * Providers register via {@code META-INF/services/com.ghatana.agent.spi.AgentLogicProvider}
 * for {@link java.util.ServiceLoader} discovery, or programmatically via
 * {@link AgentLogicProviderRegistry#register(AgentLogicProvider)}.
 *
 * @doc.type interface
 * @doc.purpose SPI for implementationRef-based agent instantiation
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AgentLogicProvider {

    /**
     * Unique identifier for this provider (e.g., {@code "yappc-java"}).
     *
     * <p>This ID forms the prefix of the {@code implementationRef} before
     * the colon separator.
     *
     * @return provider identifier, never null
     */
    String getProviderId();

    /**
     * Human-readable name of this provider.
     *
     * @return display name
     */
    String getProviderName();

    /**
     * The set of {@code implementationRef} values this provider can create.
     *
     * <p>Each ref must be in the format {@code <provider-id>:<agent-id>}.
     *
     * @return supported implementation references
     */
    Set<String> getSupportedRefs();

    /**
     * Checks whether this provider can resolve the given reference.
     *
     * @param implementationRef the ref to check
     * @return true if this provider handles the given ref
     */
    default boolean supports(String implementationRef) {
        if (implementationRef == null) return false;
        return getSupportedRefs().contains(implementationRef)
                || implementationRef.startsWith(getProviderId() + ":");
    }

    /**
     * Creates a typed agent for the given implementation reference and
     * configuration.
     *
     * @param implementationRef the ref identifying the concrete implementation
     * @param config            agent configuration (includes timeout, retries, etc.)
     * @return a fully initialized agent
     * @throws IllegalArgumentException if the ref is not supported
     */
    TypedAgent<?, ?> createAgent(String implementationRef, AgentConfig config);

    /**
     * Priority for conflict resolution where multiple providers support
     * the same ref. Lower values win.
     *
     * @return priority value (default 1000)
     */
    default int priority() {
        return 1000;
    }

    /**
     * Whether this provider is currently enabled.
     *
     * @return true if enabled (default)
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Version string for this provider.
     *
     * @return version (default "1.0.0")
     */
    default String getVersion() {
        return "1.0.0";
    }
}
