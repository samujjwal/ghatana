/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.2 — OperatorProviderRegistry: discovers and registers operator providers via ServiceLoader.
 */
package com.ghatana.core.operator.spi;

import com.ghatana.core.operator.*;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry that discovers {@link OperatorProvider} implementations via {@link ServiceLoader}
 * and optionally registers their operators into an {@link OperatorCatalog}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * OperatorProviderRegistry registry = OperatorProviderRegistry.create();
 * int count = registry.discoverProviders();
 *
 * // Optionally materialize into an OperatorCatalog
 * DefaultOperatorCatalog catalog = new DefaultOperatorCatalog();
 * registry.materializeIntoCatalog(catalog);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Discovers OperatorProvider implementations via ServiceLoader and registers them into an OperatorCatalog
 * @doc.layer core
 * @doc.pattern Service
 */
public class OperatorProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(OperatorProviderRegistry.class);

    private final Map<String, OperatorProvider> providers = new ConcurrentHashMap<>();
    private final ClassLoader classLoader;

    private OperatorProviderRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Creates a registry using the thread context class loader.
     */
    public static OperatorProviderRegistry create() {
        return new OperatorProviderRegistry(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a registry using a specific class loader.
     */
    public static OperatorProviderRegistry create(ClassLoader classLoader) {
        return new OperatorProviderRegistry(classLoader);
    }

    /**
     * Discovers operator providers via {@link ServiceLoader}.
     * Providers are sorted by priority. Disabled providers and duplicate IDs are skipped.
     *
     * @return the number of newly discovered providers
     */
    public int discoverProviders() {
        ServiceLoader<OperatorProvider> loader = ServiceLoader.load(OperatorProvider.class, classLoader);
        List<OperatorProvider> discovered = new ArrayList<>();
        for (OperatorProvider provider : loader) {
            discovered.add(provider);
        }

        discovered.sort(Comparator.comparingInt(OperatorProvider::priority));

        int count = 0;
        for (OperatorProvider provider : discovered) {
            if (!provider.isEnabled()) {
                log.debug("Skipping disabled operator provider: {}", provider.getProviderId());
                continue;
            }
            if (providers.containsKey(provider.getProviderId())) {
                log.warn("Duplicate operator provider ID '{}' — skipping", provider.getProviderId());
                continue;
            }
            providers.put(provider.getProviderId(), provider);
            count++;
            log.info("Registered operator provider: {} v{} (operators: {}, priority: {})",
                    provider.getProviderId(), provider.getVersion(),
                    provider.getOperatorIds().size(), provider.priority());
        }

        log.info("Operator provider discovery complete: {} providers registered", count);
        return count;
    }

    /**
     * Manually registers a provider.
     *
     * @param provider the provider to register
     * @throws IllegalArgumentException if duplicate ID
     */
    public void registerProvider(OperatorProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        OperatorProvider existing = providers.putIfAbsent(provider.getProviderId(), provider);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Operator provider already registered: " + provider.getProviderId());
        }
    }

    /**
     * Unregisters a provider by ID.
     *
     * @return true if removed
     */
    public boolean unregisterProvider(String providerId) {
        return providers.remove(providerId) != null;
    }

    /**
     * Gets a provider by ID.
     */
    public Optional<OperatorProvider> getProvider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /**
     * Finds all providers that support the given operator type.
     */
    public List<OperatorProvider> findByType(OperatorType type) {
        return providers.values().stream()
                .filter(p -> p.getOperatorTypes().contains(type))
                .sorted(Comparator.comparingInt(OperatorProvider::priority))
                .collect(Collectors.toList());
    }

    /**
     * Finds the first provider that supports the given operator ID.
     */
    public Optional<OperatorProvider> findByOperatorId(OperatorId operatorId) {
        return providers.values().stream()
                .filter(p -> p.supports(operatorId))
                .min(Comparator.comparingInt(OperatorProvider::priority));
    }

    /**
     * Creates an operator using the first provider that supports the given ID.
     *
     * @param operatorId the operator ID
     * @param config the operator configuration
     * @return the created operator
     * @throws NoSuchElementException if no provider supports the ID
     */
    public UnifiedOperator createOperator(OperatorId operatorId, OperatorConfig config) {
        return findByOperatorId(operatorId)
                .map(p -> p.createOperator(operatorId, config))
                .orElseThrow(() -> new NoSuchElementException(
                        "No operator provider supports: " + operatorId));
    }

    /**
     * Materializes all operators from all providers into the given catalog.
     * Each provider is asked to create an operator for each of its declared operator IDs
     * using an empty config. The operators are then registered in the catalog.
     *
     * @param catalog the catalog to populate
     * @return the number of operators registered
     */
    public int materializeIntoCatalog(DefaultOperatorCatalog catalog) {
        int count = 0;
        for (OperatorProvider provider : providers.values()) {
            for (OperatorId opId : provider.getOperatorIds()) {
                try {
                    UnifiedOperator operator = provider.createOperator(opId, OperatorConfig.empty());
                    catalog.register(operator);
                    count++;
                    log.debug("Materialized operator {} from provider {}",
                            opId, provider.getProviderId());
                } catch (Exception e) {
                    log.warn("Failed to materialize operator {} from provider {}: {}",
                            opId, provider.getProviderId(), e.getMessage());
                }
            }
        }
        log.info("Materialized {} operators into catalog", count);
        return count;
    }

    /**
     * Lists all registered providers sorted by priority.
     */
    public List<OperatorProvider> listAll() {
        return providers.values().stream()
                .sorted(Comparator.comparingInt(OperatorProvider::priority))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the total number of unique operator IDs across all providers.
     */
    public int totalOperatorCount() {
        return providers.values().stream()
                .mapToInt(p -> p.getOperatorIds().size())
                .sum();
    }

    public int size() {
        return providers.size();
    }

    public boolean contains(String providerId) {
        return providers.containsKey(providerId);
    }

    public void clear() {
        providers.clear();
    }
}
