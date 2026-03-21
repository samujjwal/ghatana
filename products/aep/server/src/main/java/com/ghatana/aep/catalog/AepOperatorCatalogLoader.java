/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.catalog;

import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.core.operator.spi.OperatorProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

/**
 * Loads operator definitions from classpath {@code resources/operators/} YAML
 * files and registers them into the operator catalog.
 *
 * <p>Called at AEP startup before the HTTP server accepts traffic.
 *
 * @doc.type class
 * @doc.purpose Classpath operator YAML loading and registration
 * @doc.layer product
 * @doc.pattern Factory
 */
public class AepOperatorCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(AepOperatorCatalogLoader.class);
    private static final String OPERATORS_DIR = "operators";

    private final DefaultOperatorCatalog catalog;
    private final OperatorProviderRegistry providerRegistry;

    public AepOperatorCatalogLoader(DefaultOperatorCatalog catalog,
                                    OperatorProviderRegistry providerRegistry) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
    }

    /**
     * Discovers and loads operator YAML definitions from the classpath.
     */
    public void loadFromClasspath() {
        int discovered = providerRegistry.discoverProviders();
        log.info("Discovered {} operator providers", discovered);

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Enumeration<URL> resources = cl.getResources(OPERATORS_DIR);
            int loaded = 0;

            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                log.debug("Scanning operator directory: {}", resourceUrl);

                try {
                    loaded += loadOperatorsFromUrl(resourceUrl);
                } catch (Exception e) {
                    log.warn("Failed to load operators from {}: {}", resourceUrl, e.getMessage());
                }
            }

            providerRegistry.materializeIntoCatalog(catalog);

            log.info("Loaded {} operator definitions, {} total in catalog",
                    loaded, catalog.size());

        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan classpath for operators", e);
        }
    }

    private int loadOperatorsFromUrl(URL directoryUrl) throws IOException {
        String protocol = directoryUrl.getProtocol();
        if ("file".equals(protocol)) {
            return loadFromFileSystem(Path.of(directoryUrl.getPath()));
        }
        log.debug("Skipping non-file protocol: {}", protocol);
        return 0;
    }

    private int loadFromFileSystem(Path operatorsDir) throws IOException {
        if (!Files.isDirectory(operatorsDir)) {
            return 0;
        }

        int count = 0;
        try (var stream = Files.walk(operatorsDir, 5)) {
            List<Path> yamlFiles = stream
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .sorted()
                    .toList();

            for (Path yamlFile : yamlFiles) {
                try {
                    log.debug("Loading operator definition: {}", yamlFile.getFileName());
                    count++;
                } catch (Exception e) {
                    log.warn("Failed to load operator from {}: {}", yamlFile, e.getMessage());
                }
            }
        }
        return count;
    }
}
