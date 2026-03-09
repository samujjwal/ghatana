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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads agent definitions from {@code agent-catalog.yaml} files distributed
 * across the repository.
 *
 * <p>Each product (YAPPC, AEP, Data-Cloud, etc.) may contain an
 * {@code agent-catalog.yaml} at its root that declares agent definition
 * paths via glob patterns. This loader discovers those files, parses
 * referenced agent definitions, and creates {@link FileBasedCatalog}
 * instances that implement {@link AgentCatalog}.
 *
 * <h2>Discovery Flow</h2>
 * <pre>
 * 1. Walk repository root for all agent-catalog.yaml files
 * 2. Parse each catalog YAML (id, name, agent glob patterns, metadata)
 * 3. Resolve agent globs relative to the catalog file's parent directory
 * 4. Parse each discovered agent YAML into AgentDefinition
 * 5. Return a FileBasedCatalog wrapping those definitions
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Discovers and loads agent-catalog.yaml files across products
 * @doc.layer framework
 * @doc.pattern Factory
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class CatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(CatalogLoader.class);
    private static final String CATALOG_FILE = "agent-catalog.yaml";

    private final ObjectMapper yamlMapper;

    public CatalogLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public CatalogLoader(@NotNull ObjectMapper yamlMapper) {
        this.yamlMapper = Objects.requireNonNull(yamlMapper, "yamlMapper");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Discovery
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Scans the repository root for all {@code agent-catalog.yaml} files and
     * returns the list of discovered paths.
     *
     * @param repositoryRoot the repository root directory
     * @return list of catalog file paths found
     * @throws IOException if the file system walk fails
     */
    @NotNull
    public List<Path> discoverCatalogFiles(@NotNull Path repositoryRoot) throws IOException {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        List<Path> found = new ArrayList<>();

        Files.walkFileTree(repositoryRoot, EnumSet.noneOf(FileVisitOption.class), 10,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (file.getFileName().toString().equals(CATALOG_FILE)) {
                            found.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName().toString();
                        if (name.equals(".git") || name.equals("node_modules")
                                || name.equals("build") || name.equals(".gradle")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        log.warn("Failed to visit {}: {}", file, exc.getMessage());
                        return FileVisitResult.CONTINUE;
                    }
                });

        log.info("Discovered {} catalog files under {}", found.size(), repositoryRoot);
        return Collections.unmodifiableList(found);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Loading
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Loads a single catalog from its {@code agent-catalog.yaml} path.
     *
     * @param catalogYamlPath absolute path to the agent-catalog.yaml file
     * @return a {@link FileBasedCatalog} wrapping the discovered agent definitions
     * @throws IOException if the file cannot be read or parsed
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public AgentCatalog loadFromFile(@NotNull Path catalogYamlPath) throws IOException {
        Objects.requireNonNull(catalogYamlPath, "catalogYamlPath");

        Map<String, Object> root = yamlMapper.readValue(
                catalogYamlPath.toFile(), Map.class);

        Map<String, Object> catalogDef = (Map<String, Object>) root.getOrDefault("catalog", root);

        String catalogId = (String) catalogDef.getOrDefault("id", "unknown");
        String displayName = (String) catalogDef.getOrDefault("name", catalogId);
        List<String> agentGlobs = (List<String>) catalogDef.getOrDefault("agents", List.of());
        List<String> extendsList = (List<String>) catalogDef.getOrDefault("extends", List.of());

        Map<String, Object> metadataMap = (Map<String, Object>) catalogDef.getOrDefault("metadata", Map.of());

        Path basePath = catalogYamlPath.getParent();
        List<CatalogAgentEntry> agents = resolveAgentDefinitions(basePath, agentGlobs, catalogId);

        log.info("Loaded catalog '{}' ({}) with {} agents from {}",
                catalogId, displayName, agents.size(), catalogYamlPath);

        return FileBasedCatalog.builder()
                .catalogId(catalogId)
                .displayName(displayName)
                .definitions(agents)
                .extendsCatalogs(extendsList)
                .metadata(metadataMap)
                .build();
    }

    /**
     * Convenience method: discovers all catalog files under the given root
     * and loads each one.
     *
     * @param repositoryRoot the repository root directory
     * @return list of loaded catalogs
     * @throws IOException if discovery or loading fails
     */
    @NotNull
    public List<AgentCatalog> discoverAndLoad(@NotNull Path repositoryRoot) throws IOException {
        return discoverCatalogFiles(repositoryRoot).stream()
                .map(path -> {
                    try {
                        return loadFromFile(path);
                    } catch (IOException e) {
                        log.error("Failed to load catalog from {}: {}", path, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Agent YAML Resolution
    // ═══════════════════════════════════════════════════════════════════════════

    private List<CatalogAgentEntry> resolveAgentDefinitions(
            Path basePath, List<String> agentGlobs, String catalogId) {

        List<CatalogAgentEntry> definitions = new ArrayList<>();

        for (String glob : agentGlobs) {
            PathMatcher matcher = FileSystems.getDefault()
                    .getPathMatcher("glob:" + basePath.resolve(glob));

            try {
                Files.walkFileTree(basePath, EnumSet.noneOf(FileVisitOption.class), 10,
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (matcher.matches(file) && file.toString().endsWith(".yaml")) {
                                    try {
                                        CatalogAgentEntry def = parseAgentYaml(file, catalogId);
                                        if (def != null) {
                                            definitions.add(def);
                                        }
                                    } catch (IOException e) {
                                        log.warn("Failed to parse agent YAML {}: {}",
                                                file, e.getMessage());
                                    }
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException e) {
                log.warn("Failed to walk {} for glob '{}': {}", basePath, glob, e.getMessage());
            }
        }

        return definitions;
    }

    @SuppressWarnings("unchecked")
    private CatalogAgentEntry parseAgentYaml(Path agentYamlPath, String catalogId)
            throws IOException {

        Map<String, Object> raw = yamlMapper.readValue(agentYamlPath.toFile(), Map.class);

        // Support both top-level and nested "agent:" format
        Map<String, Object> agentMap = raw.containsKey("agent")
                ? (Map<String, Object>) raw.get("agent")
                : raw;

        String id = (String) agentMap.get("id");
        if (id == null) {
            log.warn("Agent YAML {} has no 'id' field, skipping", agentYamlPath);
            return null;
        }

        return CatalogAgentEntry.builder()
                .id(id)
                .name((String) agentMap.getOrDefault("name", id))
                .version((String) agentMap.getOrDefault("version", "1.0.0"))
                .description((String) agentMap.get("description"))
                .metadata((Map<String, Object>) agentMap.getOrDefault("metadata", Map.of()))
                .generator((Map<String, Object>) agentMap.getOrDefault("generator", Map.of()))
                .memory((Map<String, Object>) agentMap.getOrDefault("memory", Map.of()))
                .tools(toStringList(agentMap.get("tools")))
                .capabilities(toStringSet(agentMap.get("capabilities")))
                .routing((Map<String, Object>) agentMap.getOrDefault("routing", Map.of()))
                .delegation((Map<String, Object>) agentMap.getOrDefault("delegation", Map.of()))
                .governance((Map<String, Object>) agentMap.getOrDefault("governance", Map.of()))
                .performance((Map<String, Object>) agentMap.getOrDefault("performance", Map.of()))
                .catalogId(catalogId)
                .build();
    }

    private static List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    private static Set<String> toStringSet(Object obj) {
        if (obj instanceof Collection<?> coll) {
            return coll.stream().map(Object::toString).collect(Collectors.toSet());
        }
        return Set.of();
    }
}
