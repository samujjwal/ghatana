/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for {@link AgentLogicProvider} instances, supporting both
 * {@link ServiceLoader} discovery and programmatic registration.
 *
 * <p>Resolves {@code implementationRef} strings to providers and creates
 * agent instances. When multiple providers support the same ref,
 * the provider with the lowest priority wins.
 *
 * @doc.type class
 * @doc.purpose Registry for implementationRef providers
 * @doc.layer platform
 * @doc.pattern Service
 */
public class AgentLogicProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentLogicProviderRegistry.class);

    private final Map<String, AgentLogicProvider> providers = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     */
    public static AgentLogicProviderRegistry create() {
        return new AgentLogicProviderRegistry();
    }

    /**
     * Creates a registry and discovers providers from the classpath.
     *
     * @param classLoader class loader for ServiceLoader discovery
     * @return registry with discovered providers
     */
    public static AgentLogicProviderRegistry createAndDiscover(ClassLoader classLoader) {
        AgentLogicProviderRegistry registry = new AgentLogicProviderRegistry();
        registry.discoverProviders(classLoader);
        return registry;
    }

    /**
     * Discovers and registers all {@link AgentLogicProvider} implementations
     * found via {@link ServiceLoader}.
     *
     * @param classLoader class loader for discovery
     * @return number of providers discovered
     */
    public int discoverProviders(ClassLoader classLoader) {
        int count = 0;
        ServiceLoader<AgentLogicProvider> loader =
                ServiceLoader.load(AgentLogicProvider.class, classLoader);

        for (AgentLogicProvider provider : loader) {
            if (provider.isEnabled()) {
                register(provider);
                count++;
            } else {
                log.info("Skipping disabled provider: {} ({})",
                        provider.getProviderId(), provider.getProviderName());
            }
        }
        log.info("Discovered {} logic providers via ServiceLoader", count);
        return count;
    }

    /**
     * Registers a provider. If a provider with the same ID exists, the
     * one with lower priority wins.
     *
     * @param provider the provider to register
     */
    public void register(AgentLogicProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        String id = provider.getProviderId();
        Objects.requireNonNull(id, "provider ID must not be null");

        providers.merge(id, provider, (existing, incoming) -> {
            if (incoming.priority() < existing.priority()) {
                log.info("Replacing provider '{}' (priority {}) with higher-priority provider (priority {})",
                        id, existing.priority(), incoming.priority());
                return incoming;
            }
            log.debug("Keeping existing provider '{}' (priority {} <= {})",
                    id, existing.priority(), incoming.priority());
            return existing;
        });

        log.debug("Registered logic provider '{}' ({}) with {} refs",
                id, provider.getProviderName(), provider.getSupportedRefs().size());
    }

    /**
     * Resolves the provider for the given {@code implementationRef}.
     *
     * <p>Matching order:
     * <ol>
     *   <li>Exact provider ID prefix match ({@code ref.startsWith(providerId + ":")})</li>
     *   <li>Provider that lists the ref in {@link AgentLogicProvider#getSupportedRefs()}</li>
     * </ol>
     *
     * @param implementationRef the ref to resolve
     * @return matching provider, or empty if none found
     */
    public Optional<AgentLogicProvider> resolve(String implementationRef) {
        if (implementationRef == null || implementationRef.isBlank()) {
            return Optional.empty();
        }

        // Fast path: extract provider ID from ref prefix
        int colonIdx = implementationRef.indexOf(':');
        if (colonIdx > 0) {
            String providerId = implementationRef.substring(0, colonIdx);
            AgentLogicProvider provider = providers.get(providerId);
            if (provider != null && provider.supports(implementationRef)) {
                return Optional.of(provider);
            }
        }

        // Slow path: ask each provider
        return providers.values().stream()
                .filter(p -> p.supports(implementationRef))
                .min(Comparator.comparingInt(AgentLogicProvider::priority));
    }

    /**
     * Resolves a provider and creates an agent in one step.
     *
     * @param implementationRef the ref to resolve
     * @param config agent configuration
     * @return the created agent
     * @throws IllegalArgumentException if no provider supports the ref
     */
    public TypedAgent<?, ?> createAgent(String implementationRef, AgentConfig config) {
        AgentLogicProvider provider = resolve(implementationRef)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No AgentLogicProvider found for implementationRef: " + implementationRef
                                + ". Registered providers: " + providers.keySet()));

        log.debug("Creating agent for ref '{}' via provider '{}'",
                implementationRef, provider.getProviderId());
        return provider.createAgent(implementationRef, config);
    }

    /**
     * Returns all registered provider IDs.
     */
    public Set<String> getProviderIds() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    /**
     * Returns the number of registered providers.
     */
    public int size() {
        return providers.size();
    }
}
