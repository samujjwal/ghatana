/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.registry;

import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.catalog.loader.CatalogLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * Bootstraps the platform {@link CatalogRegistry} by discovering and loading all
 * {@code agent-catalog.yaml} files from a configured root directory.
 *
 * <p>This is the production wiring point for the 228+ YAML agent definitions
 * stored under {@code platform/agent-catalog/}. At application startup, call
 * {@link #loadInto(CatalogRegistry)} to populate the registry before the
 * event loop starts serving requests.
 *
 * <h2>Configuration</h2>
 * <p>The catalog root directory is resolved in this priority order:
 * <ol>
 *   <li>Constructor argument (explicit path)</li>
 *   <li>System property {@code ghatana.catalog.root}</li>
 *   <li>Environment variable {@code GHATANA_CATALOG_ROOT}</li>
 *   <li>Default: {@code ./platform/agent-catalog} relative to working directory</li>
 * </ol>
 *
 * <h2>Usage in ProductionModule</h2>
 * <pre>{@code
 * @Provides
 * CatalogRegistry catalogRegistry() {
 *     CatalogRegistry registry = CatalogRegistry.empty();
 *     new YamlAgentCatalogLoader().loadInto(registry);
 *     return registry;
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Startup bootstrap for YAML-backed CatalogRegistry population
 * @doc.layer registry
 * @doc.pattern Service, Factory
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
public final class YamlAgentCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlAgentCatalogLoader.class);

    private static final String PROP_CATALOG_ROOT = "ghatana.catalog.root";
    private static final String ENV_CATALOG_ROOT = "GHATANA_CATALOG_ROOT";
    private static final String DEFAULT_CATALOG_ROOT = "platform/agent-catalog";

    private final Path catalogRoot;
    private final CatalogLoader loader;

    /**
     * Creates a loader that resolves its catalog root from the environment
     * (system property → env var → default relative path).
     */
    public YamlAgentCatalogLoader() {
        this(resolveCatalogRoot(), new CatalogLoader());
    }

    /**
     * Creates a loader from an explicit catalog root path.
     *
     * @param catalogRoot absolute path to the directory containing {@code agent-catalog.yaml} files
     */
    public YamlAgentCatalogLoader(@NotNull Path catalogRoot) {
        this(catalogRoot, new CatalogLoader());
    }

    /**
     * Full constructor for testing — allows injecting a custom {@link CatalogLoader}.
     *
     * @param catalogRoot the root catalog directory
     * @param loader      the {@link CatalogLoader} to use for parsing
     */
    public YamlAgentCatalogLoader(@NotNull Path catalogRoot, @NotNull CatalogLoader loader) {
        this.catalogRoot = Objects.requireNonNull(catalogRoot, "catalogRoot");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Discovers all {@code agent-catalog.yaml} files under {@link #catalogRoot},
     * loads each one, and registers the resulting {@link AgentCatalog} instances
     * into the supplied {@link CatalogRegistry}.
     *
     * <p>This method performs blocking file-system IO and should be called at
     * application startup <em>before</em> the ActiveJ event loop begins serving
     * requests.
     *
     * @param registry the target registry to populate
     * @return the total number of agent definitions loaded across all catalogs
     */
    public int loadInto(@NotNull CatalogRegistry registry) {
        Objects.requireNonNull(registry, "registry");

        if (!Files.isDirectory(catalogRoot)) {
            log.warn("YamlAgentCatalogLoader: catalog root '{}' does not exist or is not a directory." +
                    " No agent definitions will be loaded.", catalogRoot.toAbsolutePath());
            return 0;
        }

        List<AgentCatalog> catalogs;
        try {
            catalogs = loader.discoverAndLoad(catalogRoot);
        } catch (IOException e) {
            log.error("YamlAgentCatalogLoader: failed to discover catalogs under '{}': {}",
                    catalogRoot.toAbsolutePath(), e.getMessage(), e);
            return 0;
        }

        if (catalogs.isEmpty()) {
            log.warn("YamlAgentCatalogLoader: no agent-catalog.yaml files found under '{}'",
                    catalogRoot.toAbsolutePath());
            return 0;
        }

        int totalAgents = 0;
        for (AgentCatalog catalog : catalogs) {
            registry.register(catalog);
            totalAgents += catalog.getDefinitions().size();
        }

        log.info("YamlAgentCatalogLoader: loaded {} catalog(s) with {} total agent definitions " +
                "from '{}'", catalogs.size(), totalAgents, catalogRoot.toAbsolutePath());
        return totalAgents;
    }

    /**
     * Convenience factory: creates a new {@link CatalogRegistry}, loads all catalogs
     * into it, and returns it. Equivalent to:
     * <pre>{@code
     * CatalogRegistry r = CatalogRegistry.empty();
     * new YamlAgentCatalogLoader().loadInto(r);
     * return r;
     * }</pre>
     *
     * @return a populated (or empty-on-failure) {@link CatalogRegistry}
     */
    @NotNull
    public CatalogRegistry loadAndBuildRegistry() {
        CatalogRegistry registry = CatalogRegistry.empty();
        loadInto(registry);
        return registry;
    }

    /**
     * Returns the resolved catalog root path that this loader will scan.
     *
     * @return the catalog root directory
     */
    @NotNull
    public Path getCatalogRoot() {
        return catalogRoot;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration resolution
    // ═══════════════════════════════════════════════════════════════════════════

    private static Path resolveCatalogRoot() {
        // 1. System property
        String propValue = System.getProperty(PROP_CATALOG_ROOT);
        if (propValue != null && !propValue.isBlank()) {
            log.debug("YamlAgentCatalogLoader: using catalog root from system property '{}': {}",
                    PROP_CATALOG_ROOT, propValue);
            return Paths.get(propValue).toAbsolutePath();
        }

        // 2. Environment variable
        String envValue = System.getenv(ENV_CATALOG_ROOT);
        if (envValue != null && !envValue.isBlank()) {
            log.debug("YamlAgentCatalogLoader: using catalog root from env var '{}': {}",
                    ENV_CATALOG_ROOT, envValue);
            return Paths.get(envValue).toAbsolutePath();
        }

        // 3. Default: platform/agent-catalog relative to working directory
        Path defaultPath = Paths.get(DEFAULT_CATALOG_ROOT).toAbsolutePath();
        log.debug("YamlAgentCatalogLoader: using default catalog root: {}", defaultPath);
        return defaultPath;
    }
}
