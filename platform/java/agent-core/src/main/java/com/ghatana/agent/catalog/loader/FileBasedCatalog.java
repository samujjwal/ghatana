/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.catalog.loader;

import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import lombok.Builder;
import lombok.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An {@link AgentCatalog} implementation backed by a parsed
 * {@code agent-catalog.yaml} file and its associated agent YAML definitions.
 *
 * <p>Created by {@link CatalogLoader#loadFromFile} during catalog discovery.
 * Immutable after construction — all filtering methods operate over a
 * pre-built in-memory index.
 *
 * @doc.type class
 * @doc.purpose File-system-backed AgentCatalog implementation
 * @doc.layer framework
 * @doc.pattern Value Object, Adapter
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@Value
@Builder
public class FileBasedCatalog implements AgentCatalog {

    /** Unique catalog identifier. */
    String catalogId;

    /** Human-readable name. */
    String displayName;

    /** All agent definitions discovered by the loader. */
    @Builder.Default
    List<CatalogAgentEntry> definitions = List.of();

    /** Parent catalog IDs this catalog extends. */
    @Builder.Default
    List<String> extendsCatalogs = List.of();

    /** Catalog metadata from the YAML. */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentCatalog SPI
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<CatalogAgentEntry> getDefinitions() {
        return Collections.unmodifiableList(definitions);
    }

    @Override
    public Optional<CatalogAgentEntry> findById(String agentId) {
        return definitions.stream()
                .filter(d -> d.getId().equals(agentId))
                .findFirst();
    }

    @Override
    public List<CatalogAgentEntry> findByCapability(String capability) {
        return definitions.stream()
                .filter(d -> d.getCapabilities().contains(capability))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<CatalogAgentEntry> findByLevel(String level) {
        return definitions.stream()
                .filter(d -> d.getLevel().equals(level))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<CatalogAgentEntry> findByDomain(String domain) {
        return definitions.stream()
                .filter(d -> d.getDomain().equals(domain))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Set<String> getAllCapabilities() {
        return definitions.stream()
                .flatMap(d -> d.getCapabilities().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int priority() {
        Object p = metadata.get("priority");
        if (p instanceof Number n) {
            return n.intValue();
        }
        return 1000;
    }
}
