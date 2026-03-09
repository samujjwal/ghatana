/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.yappc.agent.catalog;

import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.loader.CatalogLoader;
import com.ghatana.agent.catalog.loader.FileBasedCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * YAPPC product catalog — exposes all YAPPC agent definitions to the platform
 * {@link com.ghatana.agent.catalog.CatalogRegistry} via {@link java.util.ServiceLoader}.
 *
 * <p>Discovers agent definitions from the YAPPC agent configuration directory.
 * The catalog is loaded from the classpath resource {@code yappc-agent-catalog.yaml}
 * or, when running in-tree, directly from {@code products/yappc/config/agents/}.
 *
 * <h2>ServiceLoader Registration</h2>
 * This class is registered in
 * {@code META-INF/services/com.ghatana.agent.catalog.AgentCatalog} so that
 * {@link com.ghatana.agent.catalog.CatalogRegistry#discover()} picks it up automatically
 * at runtime without any explicit wiring.
 *
 * @doc.type class
 * @doc.purpose YAPPC product agent catalog — ServiceLoader provider
 * @doc.layer product
 * @doc.pattern SPI, Registry
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
public class YappcAgentCatalog implements AgentCatalog {

    private static final Logger log = LoggerFactory.getLogger(YappcAgentCatalog.class);

    static final String CATALOG_ID = "yappc";
    static final String DISPLAY_NAME = "YAPPC Agent Catalog";

    private static final String CATALOG_RESOURCE = "yappc-agent-catalog.yaml";

    private volatile FileBasedCatalog delegate;

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentCatalog SPI
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public String getCatalogId() {
        return CATALOG_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<CatalogAgentEntry> getDefinitions() {
        return ensureLoaded().getDefinitions();
    }

    @Override
    public Optional<CatalogAgentEntry> findById(String agentId) {
        return ensureLoaded().findById(agentId);
    }

    @Override
    public List<CatalogAgentEntry> findByCapability(String capability) {
        return ensureLoaded().findByCapability(capability);
    }

    @Override
    public List<CatalogAgentEntry> findByLevel(String level) {
        return ensureLoaded().findByLevel(level);
    }

    @Override
    public List<CatalogAgentEntry> findByDomain(String domain) {
        return ensureLoaded().findByDomain(domain);
    }

    @Override
    public Set<String> getAllCapabilities() {
        return ensureLoaded().getAllCapabilities();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lazy loading
    // ═══════════════════════════════════════════════════════════════════════════

    private FileBasedCatalog ensureLoaded() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = load();
                }
            }
        }
        return delegate;
    }

    private FileBasedCatalog load() {
        try {
            Path catalogPath = resolveCatalogPath();
            if (catalogPath != null) {
                CatalogLoader loader = new CatalogLoader();
                FileBasedCatalog catalog = (FileBasedCatalog) loader.loadFromFile(catalogPath);
                log.info("YappcAgentCatalog: loaded {} agents from {}",
                        catalog.getDefinitions().size(), catalogPath);
                return catalog;
            }
        } catch (IOException | URISyntaxException e) {
            log.warn("YappcAgentCatalog: failed to load catalog, returning empty catalog: {}", e.getMessage());
        }
        return FileBasedCatalog.builder()
                .catalogId(CATALOG_ID)
                .displayName(DISPLAY_NAME)
                .definitions(Collections.emptyList())
                .build();
    }

    private Path resolveCatalogPath() throws URISyntaxException {
        URL resource = YappcAgentCatalog.class.getClassLoader().getResource(CATALOG_RESOURCE);
        if (resource != null) {
            return Paths.get(resource.toURI());
        }
        log.debug("YappcAgentCatalog: classpath resource '{}' not found, catalog will be empty", CATALOG_RESOURCE);
        return null;
    }
}
