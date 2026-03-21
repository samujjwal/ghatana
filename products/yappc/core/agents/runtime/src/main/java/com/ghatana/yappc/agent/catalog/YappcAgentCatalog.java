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
import java.nio.file.Files;
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
 * The catalog resolves its root from the canonical product layout
 * ({@code products/yappc/config/agents/agent-catalog.yaml}) and falls back to
 * classpath resolution for classpath-only deployments.
 *
 * <h2>Central Catalog Integration</h2>
 * As of v2.4, the central {@code AepCentralCatalogService} also discovers
 * YAPPC definitions from the product root. This ServiceLoader provider is
 * retained for backward-compatible classpath-based discovery.
 *
 * <h2>ServiceLoader Registration</h2>
 * This class is registered in
 * {@code META-INF/services/com.ghatana.agent.catalog.AgentCatalog} so that
 * {@link com.ghatana.agent.catalog.CatalogRegistry#discover()} picks it up automatically
 * at runtime without any explicit wiring.
 *
 * @deprecated Since v2.4. The central {@code AepCentralCatalogService} now discovers
 *     YAPPC definitions automatically from the product root. This ServiceLoader-based
 *     provider is retained for backward compatibility and will be removed in v3.0.
 *     See {@code docs/AGENT_REGISTRY_MIGRATION_GUIDE.md}.
 *
 * @doc.type class
 * @doc.purpose YAPPC product agent catalog — ServiceLoader provider (deprecated)
 * @doc.layer product
 * @doc.pattern SPI, Registry
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
@Deprecated(since = "2.4.0", forRemoval = true)
public class YappcAgentCatalog implements AgentCatalog {

    private static final Logger log = LoggerFactory.getLogger(YappcAgentCatalog.class);

    static final String CATALOG_ID = "yappc";
    static final String DISPLAY_NAME = "YAPPC Agent Catalog";

    private static final String CATALOG_RESOURCE = "yappc-agent-catalog.yaml";

    /**
     * Canonical in-tree path relative to the repository root.
     * {@code AepCentralCatalogService} also uses this path for multi-root discovery.
     */
    private static final String CANONICAL_PRODUCT_PATH = "products/yappc/config/agents/agent-catalog.yaml";

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
        // 1. Try canonical in-tree path (for monorepo development / central catalog)
        Path canonicalPath = Path.of(CANONICAL_PRODUCT_PATH);
        if (Files.exists(canonicalPath)) {
            log.debug("YappcAgentCatalog: using canonical product path '{}'", canonicalPath);
            return canonicalPath;
        }

        // 2. Fallback to classpath resource (for packaged deployments)
        URL resource = YappcAgentCatalog.class.getClassLoader().getResource(CATALOG_RESOURCE);
        if (resource != null) {
            return Paths.get(resource.toURI());
        }
        log.debug("YappcAgentCatalog: classpath resource '{}' not found, catalog will be empty", CATALOG_RESOURCE);
        return null;
    }
}
