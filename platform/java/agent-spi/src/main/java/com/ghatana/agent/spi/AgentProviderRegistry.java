/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.api.AgentConfig;
import com.ghatana.agent.api.AgentType;
import com.ghatana.agent.api.TypedAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry that discovers {@link AgentProvider} implementations via
 * {@link ServiceLoader} and manages their lifecycle.
 *
 * @doc.type class
 * @doc.purpose Registry for agent provider discovery and management
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
     */
    public static AgentProviderRegistry create(ClassLoader classLoader) {
        return new AgentProviderRegistry(classLoader);
    }

    /**
     * Discovers agent providers via {@link ServiceLoader} and registers them.
     *
     * @return the number of newly discovered providers
     */
    public int discoverProviders() {
        ServiceLoader<AgentProvider> loader = ServiceLoader.load(AgentProvider.class, classLoader);
        List<AgentProvider> discovered = new ArrayList<>();

        for (AgentProvider provider : loader) {
            discovered.add(provider);
        }

        discovered.sort(Comparator.comparingInt(AgentProvider::priority));

        int count = 0;
        for (AgentProvider provider : discovered) {
            if (!provider.isEnabled()) {
                log.debug("Skipping disabled provider: {} ({})",
                        provider.getProviderId(), provider.getProviderName());
                continue;
            }
            if (providers.containsKey(provider.getProviderId())) {
                log.warn("Duplicate provider ID '{}' — skipping {}",
                        provider.getProviderId(), provider.getProviderName());
                continue;
            }
            providers.put(provider.getProviderId(), provider);
            count++;
            log.info("Registered agent provider: {} v{} (types: {}, priority: {})",
                    provider.getProviderId(), provider.getVersion(),
                    provider.getSupportedTypes(), provider.priority());
        }

        log.info("Agent provider discovery complete: {} registered ({} scanned)",
                count, discovered.size());
        return count;
    }

    /**
     * Manually registers a provider.
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
     * Retrieves a provider by its ID.
     */
    public Optional<AgentProvider> getProvider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /**
     * Finds all providers that support the given agent type.
     */
    public List<AgentProvider> findByType(AgentType type) {
        return providers.values().stream()
                .filter(p -> p.supports(type))
                .sorted(Comparator.comparingInt(AgentProvider::priority))
                .collect(Collectors.toList());
    }

    /**
     * Creates an agent via the specified provider.
     */
    public TypedAgent<?, ?> createAgent(String providerId, AgentConfig config) {
        AgentProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerId);
        }
        return provider.createAgent(config);
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
