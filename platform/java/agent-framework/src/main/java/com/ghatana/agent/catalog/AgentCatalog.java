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

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SPI for pluggable agent catalogs.
 *
 * <p>Each product (YAPPC, AEP, Tutorputor, etc.) implements this interface to
 * expose its agent definitions to the platform. Discovery is performed via
 * {@link java.util.ServiceLoader} at startup.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #getCatalogId()} — unique catalog name (e.g. "yappc", "aep")</li>
 *   <li>{@link #getDefinitions()} — all agent definitions in this catalog</li>
 *   <li>{@link #findById(String)} — resolve a specific agent by ID</li>
 *   <li>{@link #findByCapability(String)} — discover agents with a given capability</li>
 * </ul>
 *
 * <h2>ServiceLoader Registration</h2>
 * <p>Product modules register their catalog implementation in
 * {@code META-INF/services/com.ghatana.agent.catalog.AgentCatalog}.
 *
 * @doc.type interface
 * @doc.purpose SPI for pluggable product agent catalogs
 * @doc.layer framework
 * @doc.pattern Service Provider Interface
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public interface AgentCatalog {

    /**
     * Unique catalog identifier (e.g. "yappc", "aep", "platform").
     *
     * @return catalog ID (never null)
     */
    String getCatalogId();

    /**
     * Human-readable catalog name.
     *
     * @return display name
     */
    String getDisplayName();

    /**
     * Returns all agent definitions in this catalog.
     *
     * @return unmodifiable list of definitions
     */
    List<CatalogAgentEntry> getDefinitions();

    /**
     * Finds an agent definition by its ID within this catalog.
     *
     * @param agentId the agent ID
     * @return the definition, or empty if not found
     */
    Optional<CatalogAgentEntry> findById(String agentId);

    /**
     * Finds all agent definitions that declare a given capability.
     *
     * @param capability the capability tag to search for
     * @return matching definitions (may be empty)
     */
    List<CatalogAgentEntry> findByCapability(String capability);

    /**
     * Finds all agent definitions at a given level (strategic, expert, worker).
     *
     * @param level the agent level
     * @return matching definitions (may be empty)
     */
    List<CatalogAgentEntry> findByLevel(String level);

    /**
     * Finds all agent definitions in a given domain.
     *
     * @param domain the domain name
     * @return matching definitions (may be empty)
     */
    List<CatalogAgentEntry> findByDomain(String domain);

    /**
     * Returns all capability tags declared across all agents in this catalog.
     *
     * @return set of capability tags
     */
    Set<String> getAllCapabilities();

    /**
     * Priority for catalog ordering when multiple catalogs provide agents
     * with the same ID. Lower values = higher priority.
     *
     * @return priority value (default 1000)
     */
    default int priority() {
        return 1000;
    }
}
