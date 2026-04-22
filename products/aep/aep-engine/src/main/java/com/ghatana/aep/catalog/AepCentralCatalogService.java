/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.catalog;

import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.catalog.loader.CatalogLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * AEP-owned central catalog service that discovers, loads, merges, and
 * validates agent definitions from all configured product roots.
 *
 * <p>Replaces product-local catalog discovery with a single coordinated
 * path. Each product root is expected to contain an
 * {@code agent-catalog.yaml} manifest.
 *
 * <h2>Supported Layouts</h2>
 * <ul>
 *   <li>{@code products/aep/agent-catalog/agent-catalog.yaml} (canonical)</li>
 *   <li>{@code products/data-cloud/agent-catalog/agent-catalog.yaml} (canonical)</li>
 *   <li>{@code products/yappc/config/agents/agent-catalog.yaml} (legacy)</li>
 *   <li>{@code platform/agent-catalog/agent-catalog.yaml} (platform core)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Central catalog loading, merging, and validation
 * @doc.layer product
 * @doc.pattern Service
 */
public class AepCentralCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AepCentralCatalogService.class);
    private static final String CATALOG_ROOTS_MANIFEST = "platform/agent-catalog/catalog-roots.txt";

    private final CatalogLoader loader;
    private final CatalogRegistry registry;
    private final List<Path> productRoots;

    /**
     * Creates the service with explicit product roots.
     *
     * @param productRoots ordered list of directories containing agent-catalog.yaml
     */
    public AepCentralCatalogService(List<Path> productRoots) {
        this.loader = new CatalogLoader();
        this.registry = CatalogRegistry.empty();
        this.productRoots = List.copyOf(productRoots);
    }

    /**
     * Creates the service with a repository root, using default product paths.
     */
    public static AepCentralCatalogService fromRepositoryRoot(Path repositoryRoot) {
        List<Path> roots = loadCatalogRoots(repositoryRoot);
        return new AepCentralCatalogService(roots);
    }

    private static List<Path> loadCatalogRoots(Path repositoryRoot) {
        Path manifest = repositoryRoot.resolve(CATALOG_ROOTS_MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            log.warn("Catalog-roots manifest not found at {}. Falling back to built-in defaults.", manifest);
            return defaultRoots(repositoryRoot);
        }

        try {
            List<Path> roots = Files.readAllLines(manifest).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .map(repositoryRoot::resolve)
                    .toList();

            if (roots.isEmpty()) {
                log.warn("Catalog-roots manifest at {} is empty. Falling back to built-in defaults.", manifest);
                return defaultRoots(repositoryRoot);
            }
            return roots;
        } catch (IOException e) {
            log.warn("Failed to read catalog-roots manifest at {}: {}. Falling back to built-in defaults.",
                    manifest,
                    e.getMessage());
            return defaultRoots(repositoryRoot);
        }
    }

    private static List<Path> defaultRoots(Path repositoryRoot) {
        return List.of(
                repositoryRoot.resolve("platform/agent-catalog"),
                repositoryRoot.resolve("products/aep/agent-catalog"),
                repositoryRoot.resolve("products/data-cloud/agent-catalog"),
                repositoryRoot.resolve("products/yappc/config/agents"),
                repositoryRoot.resolve("products/software-org/config/agents"),
                repositoryRoot.resolve("products/virtual-org/config/agents"),
                repositoryRoot.resolve("products/finance/config/agents"),
                repositoryRoot.resolve("products/tutorputor/config/agents")
        );
    }

    /**
     * Loads all catalogs from configured product roots and builds the
     * merged index.
     *
     * @return validation report
     */
    public CatalogValidationReport loadAndValidate() {
        CatalogValidationReport.Builder report = CatalogValidationReport.builder();

        for (Path root : productRoots) {
            Path catalogFile = root.resolve("agent-catalog.yaml");
            if (!Files.isRegularFile(catalogFile)) {
                log.debug("No agent-catalog.yaml at {}, skipping", root);
                continue;
            }

            try {
                AgentCatalog catalog = loader.loadFromFile(catalogFile);
                registry.register(catalog);
                report.addLoadedCatalog(catalog.getCatalogId(),
                        catalog.getDefinitions().size());
                log.info("Loaded catalog '{}' with {} definitions from {}",
                        catalog.getCatalogId(),
                        catalog.getDefinitions().size(),
                        catalogFile);
            } catch (Exception e) {
                report.addError("LOAD_FAILED",
                        "Failed to load catalog from " + catalogFile + ": " + e.getMessage());
                log.error("Failed to load catalog from {}: {}", catalogFile, e.getMessage(), e);
            }
        }

        // Run validation
        validate(report);

        CatalogValidationReport finalReport = report.build();
        log.info("Catalog validation: {} catalogs, {} agents, {} errors, {} warnings",
                finalReport.catalogCount(), finalReport.totalAgents(),
                finalReport.errors().size(), finalReport.warnings().size());

        return finalReport;
    }

    /**
     * Returns the underlying registry for query operations.
     */
    public CatalogRegistry getRegistry() {
        return registry;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation
    // ═══════════════════════════════════════════════════════════════════════════

    private void validate(CatalogValidationReport.Builder report) {
        validateDuplicates(report);
        validateOwnership(report);
        validateRequiredFields(report);
    }

    /**
     * Detects agents with the same ID across multiple catalogs.
     */
    private void validateDuplicates(CatalogValidationReport.Builder report) {
        Map<String, List<String>> agentToCatalogs = new HashMap<>();

        for (String catalogId : registry.getCatalogIds()) {
            registry.getCatalog(catalogId).ifPresent(catalog -> {
                for (CatalogAgentEntry entry : catalog.getDefinitions()) {
                    agentToCatalogs
                            .computeIfAbsent(entry.getId(), k -> new ArrayList<>())
                            .add(catalogId);
                }
            });
        }

        for (Map.Entry<String, List<String>> e : agentToCatalogs.entrySet()) {
            if (e.getValue().size() > 1) {
                report.addWarning("DUPLICATE_AGENT",
                        "Agent '" + e.getKey() + "' defined in multiple catalogs: "
                                + e.getValue() + ". Highest priority wins.");
            }
        }
    }

    /**
     * Validates that each agent has a catalogId matching its hosting catalog.
     */
    private void validateOwnership(CatalogValidationReport.Builder report) {
        for (CatalogAgentEntry entry : registry.allDefinitions()) {
            if (entry.getCatalogId() == null || entry.getCatalogId().isEmpty()) {
                report.addError("MISSING_OWNERSHIP",
                        "Agent '" + entry.getId() + "' has no catalogId ownership");
            }
        }
    }

    /**
     * Validates that each agent has required fields.
     */
    private void validateRequiredFields(CatalogValidationReport.Builder report) {
        for (CatalogAgentEntry entry : registry.allDefinitions()) {
            if (entry.getId() == null || entry.getId().isBlank()) {
                report.addError("MISSING_ID",
                        "Agent entry missing 'id' field in catalog '" + entry.getCatalogId() + "'");
            }
            if (entry.getName() == null || entry.getName().isBlank()) {
                report.addWarning("MISSING_NAME",
                        "Agent '" + entry.getId() + "' has no 'name' field");
            }
            if (entry.getCapabilities().isEmpty()) {
                report.addWarning("NO_CAPABILITIES",
                        "Agent '" + entry.getId() + "' declares no capabilities");
            }
        }
    }
}
