/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Aggregates all discovered {@link AgentCatalog} instances into a single
 * queryable registry.
 *
 * <p>Catalogs are discovered via {@link java.util.ServiceLoader} at startup,
 * or can be registered programmatically. When multiple catalogs contain an
 * agent with the same ID, the catalog with the lowest {@link AgentCatalog#priority()}
 * wins.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CatalogRegistry registry = CatalogRegistry.discover();
 * Optional<AgentDefinition> def = registry.findById("sentinel");
 * List<AgentDefinition> codeGen = registry.findByCapability("code_generation");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Aggregated catalog registry with ServiceLoader discovery
 * @doc.layer framework
 * @doc.pattern Registry
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class CatalogRegistry {

    private static final Logger log = LoggerFactory.getLogger(CatalogRegistry.class);

    private final Map<String, AgentCatalog> catalogs = new ConcurrentHashMap<>();
    private final Map<String, CatalogAgentEntry> mergedIndex = new ConcurrentHashMap<>();

    /**
     * Discovers all {@link AgentCatalog} implementations via ServiceLoader
     * and builds the merged index.
     *
     * @return a new CatalogRegistry containing all discovered catalogs
     */
    public static CatalogRegistry discover() {
        CatalogRegistry registry = new CatalogRegistry();
        ServiceLoader<AgentCatalog> loader = ServiceLoader.load(AgentCatalog.class);
        for (AgentCatalog catalog : loader) {
            registry.register(catalog);
        }
        log.info("CatalogRegistry: discovered {} catalogs, {} total agent definitions",
                registry.catalogs.size(), registry.mergedIndex.size());
        return registry;
    }

    /**
     * Creates an empty registry (for programmatic registration or testing).
     */
    public static CatalogRegistry empty() {
        return new CatalogRegistry();
    }

    /**
     * Registers a catalog and merges its definitions into the global index.
     *
     * @param catalog the catalog to register
     */
    public void register(AgentCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        String id = catalog.getCatalogId();

        AgentCatalog existing = catalogs.get(id);
        if (existing != null) {
            log.warn("CatalogRegistry: replacing catalog '{}' (old priority={}, new priority={})",
                    id, existing.priority(), catalog.priority());
        }
        catalogs.put(id, catalog);

        for (CatalogAgentEntry def : catalog.getDefinitions()) {
            CatalogAgentEntry prev = mergedIndex.get(def.getId());
            if (prev == null || catalogPriority(prev.getCatalogId()) > catalog.priority()) {
                mergedIndex.put(def.getId(), def);
            } else {
                log.debug("CatalogRegistry: skipping agent '{}' from catalog '{}' (shadowed by '{}')",
                        def.getId(), id, prev.getCatalogId());
            }
        }

        log.info("CatalogRegistry: registered catalog '{}' ({}) with {} definitions",
                id, catalog.getDisplayName(), catalog.getDefinitions().size());
    }

    /**
     * Unregisters a catalog and rebuilds the merged index.
     *
     * @param catalogId the catalog ID to remove
     */
    public void unregister(String catalogId) {
        AgentCatalog removed = catalogs.remove(catalogId);
        if (removed != null) {
            rebuildIndex();
            log.info("CatalogRegistry: unregistered catalog '{}'", catalogId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Queries
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Finds an agent definition by ID across all catalogs.
     */
    public Optional<CatalogAgentEntry> findById(String agentId) {
        return Optional.ofNullable(mergedIndex.get(agentId));
    }

    /**
     * Finds all agent definitions that declare a given capability.
     */
    public List<CatalogAgentEntry> findByCapability(String capability) {
        return mergedIndex.values().stream()
                .filter(d -> d.getCapabilities().contains(capability))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds all agent definitions at a given level.
     */
    public List<CatalogAgentEntry> findByLevel(String level) {
        return mergedIndex.values().stream()
                .filter(d -> level.equalsIgnoreCase(d.getLevel()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds all agent definitions in a given domain.
     */
    public List<CatalogAgentEntry> findByDomain(String domain) {
        return mergedIndex.values().stream()
                .filter(d -> domain.equalsIgnoreCase(d.getDomain()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all agent definitions from all catalogs (merged, deduplicated).
     */
    public Collection<CatalogAgentEntry> allDefinitions() {
        return Collections.unmodifiableCollection(mergedIndex.values());
    }

    /**
     * Returns all registered catalog IDs.
     */
    public Set<String> getCatalogIds() {
        return Collections.unmodifiableSet(catalogs.keySet());
    }

    /**
     * Returns a specific catalog by ID.
     */
    public Optional<AgentCatalog> getCatalog(String catalogId) {
        return Optional.ofNullable(catalogs.get(catalogId));
    }

    /**
     * Returns the total number of unique agent definitions across all catalogs.
     */
    public int size() {
        return mergedIndex.size();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════════════════════════════════════════

    private void rebuildIndex() {
        mergedIndex.clear();
        catalogs.values().stream()
                .sorted(Comparator.comparingInt(AgentCatalog::priority))
                .forEach(catalog -> {
                    for (CatalogAgentEntry def : catalog.getDefinitions()) {
                        mergedIndex.putIfAbsent(def.getId(), def);
                    }
                });
    }

    private int catalogPriority(String catalogId) {
        AgentCatalog cat = catalogs.get(catalogId);
        return cat != null ? cat.priority() : Integer.MAX_VALUE;
    }
}
