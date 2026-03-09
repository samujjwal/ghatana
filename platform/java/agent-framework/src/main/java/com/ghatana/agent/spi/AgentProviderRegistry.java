/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.1 — AgentProviderRegistry: discovers and registers agent providers via ServiceLoader.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry that discovers {@link AgentProvider} implementations via {@link ServiceLoader}
 * and manages their lifecycle.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentProviderRegistry registry = AgentProviderRegistry.create();
 * int discovered = registry.discoverProviders();
 * // discovered providers available via getProvider(), findByType(), etc.
 *
 * TypedAgent<?, ?> agent = registry.createAgent("acme-fraud-detector", config);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Registry that manages and discovers AgentProvider implementations
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class AgentProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentProviderRegistry.class);

    private final Map<String, AgentProvider> providers = new ConcurrentHashMap<>();
    private final ClassLoader classLoader;

    private AgentProviderRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Creates a registry using the thread context class loader.
     */
    public static AgentProviderRegistry create() {
        return new AgentProviderRegistry(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a registry using a specific class loader.
     * Useful for plugin isolation scenarios.
     */
    public static AgentProviderRegistry create(ClassLoader classLoader) {
        return new AgentProviderRegistry(classLoader);
    }

    /**
     * Discovers agent providers via {@link ServiceLoader} and registers them.
     * Providers are sorted by priority — lower values registered first.
     * Disabled providers and duplicate IDs are skipped.
     *
     * @return the number of newly discovered providers
     */
    public int discoverProviders() {
        ServiceLoader<AgentProvider> loader = ServiceLoader.load(AgentProvider.class, classLoader);
        List<AgentProvider> discovered = new ArrayList<>();

        for (AgentProvider provider : loader) {
            discovered.add(provider);
        }

        // Sort by priority (lower = higher priority)
        discovered.sort(Comparator.comparingInt(AgentProvider::priority));

        int count = 0;
        for (AgentProvider provider : discovered) {
            if (!provider.isEnabled()) {
                log.debug("Skipping disabled provider: {} ({})", provider.getProviderId(), provider.getProviderName());
                continue;
            }

            if (providers.containsKey(provider.getProviderId())) {
                log.warn("Duplicate provider ID '{}' — skipping {} in favor of already-registered provider",
                        provider.getProviderId(), provider.getProviderName());
                continue;
            }

            providers.put(provider.getProviderId(), provider);
            count++;
            log.info("Registered agent provider: {} v{} (types: {}, priority: {})",
                    provider.getProviderId(), provider.getVersion(),
                    provider.getSupportedTypes(), provider.priority());
        }

        log.info("Agent provider discovery complete: {} providers registered ({} total scanned)",
                count, discovered.size());
        return count;
    }

    /**
     * Manually registers a provider. Useful for testing or programmatic registration.
     *
     * @param provider the provider to register
     * @throws IllegalArgumentException if a provider with the same ID already exists
     */
    public void registerProvider(AgentProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        AgentProvider existing = providers.putIfAbsent(provider.getProviderId(), provider);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Provider already registered: " + provider.getProviderId());
        }
    }

    /**
     * Unregisters a provider by ID.
     *
     * @param providerId the provider ID
     * @return true if a provider was removed
     */
    public boolean unregisterProvider(String providerId) {
        return providers.remove(providerId) != null;
    }

    /**
     * Gets a provider by ID.
     *
     * @param providerId the provider ID
     * @return the provider, or empty if not found
     */
    public Optional<AgentProvider> getProvider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /**
     * Finds all providers that support the given agent type.
     *
     * @param type the agent type
     * @return list of matching providers, sorted by priority
     */
    public List<AgentProvider> findByType(AgentType type) {
        return providers.values().stream()
                .filter(p -> p.supports(type))
                .sorted(Comparator.comparingInt(AgentProvider::priority))
                .collect(Collectors.toList());
    }

    /**
     * Creates an agent using the specified provider.
     *
     * @param providerId the provider ID
     * @param config the agent configuration
     * @return the created agent
     * @throws NoSuchElementException if the provider ID is not found
     */
    public TypedAgent<?, ?> createAgent(String providerId, AgentConfig config) {
        AgentProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new NoSuchElementException("No agent provider found: " + providerId);
        }
        return provider.createAgent(config);
    }

    /**
     * Creates an agent using the first provider that supports the given type.
     *
     * @param type the agent type
     * @param config the agent configuration
     * @return the created agent
     * @throws NoSuchElementException if no provider supports the type
     */
    public TypedAgent<?, ?> createAgentByType(AgentType type, AgentConfig config) {
        return findByType(type).stream()
                .findFirst()
                .map(p -> p.createAgent(config))
                .orElseThrow(() -> new NoSuchElementException(
                        "No agent provider supports type: " + type));
    }

    /**
     * Lists all registered providers, sorted by priority.
     *
     * @return unmodifiable list of providers
     */
    public List<AgentProvider> listAll() {
        return providers.values().stream()
                .sorted(Comparator.comparingInt(AgentProvider::priority))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the number of registered providers.
     */
    public int size() {
        return providers.size();
    }

    /**
     * Checks if a provider with the given ID is registered.
     */
    public boolean contains(String providerId) {
        return providers.containsKey(providerId);
    }

    /**
     * Removes all registered providers.
     */
    public void clear() {
        providers.clear();
    }
}
