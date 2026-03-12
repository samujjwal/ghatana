/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.operator.spi.OperatorProvider;
import com.ghatana.core.operator.spi.OperatorProviderRegistry;
import com.ghatana.core.template.TemplateContext;
import com.ghatana.core.template.YamlTemplateEngine;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Loads operator definitions from YAML files on the classpath and registers them
 * in the {@link OperatorCatalog} via the {@link OperatorProviderRegistry} SPI.
 *
 * <h2>Loading Pipeline</h2>
 * <pre>
 *   resources/operators/*.yaml
 *       │
 *       ▼  YamlTemplateEngine.render(rawYaml, context)
 *   rendered YAML string (all {@code {{ varName }}} placeholders resolved)
 *       │
 *       ▼  Jackson YAML → Map&lt;String, Object&gt;
 *   operator definition (id, version, aep.operatorType, …)
 *       │
 *       ▼  OperatorProviderRegistry.findByOperatorId()
 *   OperatorProvider (ISE if not found — no silent skip)
 *       │
 *       ▼  provider.createOperator(id, config)
 *   UnifiedOperator
 *       │
 *       ▼  OperatorCatalog.register(operator)
 *   registered
 * </pre>
 *
 * <h2>YAML Format</h2>
 * <pre>{@code
 * id: fraud-detector
 * name: Fraud Detector Operator
 * version: 1.0.0
 * aep:
 *   operatorType: STREAM
 * # any other fields become OperatorConfig properties
 * }</pre>
 *
 * <h2>Error Policy</h2>
 * <ul>
 *   <li>If no {@link OperatorProvider} is registered for an operator's declared
 *       type, an {@link IllegalStateException} is thrown (no silent skip).</li>
 *   <li>YAMLs missing the {@code id} field are skipped with a warning.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AepOperatorCatalogLoader loader = new AepOperatorCatalogLoader(catalog, providerRegistry);
 * int count = loader.loadFromClasspath(); // scans resources/operators/
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Loads operator YAML definitions into OperatorCatalog via OperatorProvider SPI
 * @doc.layer product
 * @doc.pattern Factory, Strategy
 */
public class AepOperatorCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(AepOperatorCatalogLoader.class);

    /** Default classpath directory scanned for operator YAML files. */
    public static final String DEFAULT_OPERATORS_DIR = "operators";

    private static final String DEFAULT_NAMESPACE = "aep";
    private static final String DEFAULT_VERSION    = "1.0.0";
    private static final String DEFAULT_TYPE       = "agent";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final OperatorCatalog catalog;
    private final OperatorProviderRegistry providerRegistry;
    private final YamlTemplateEngine templateEngine;
    private final TemplateContext templateContext;

    /**
     * Creates a loader with a custom template context.
     *
     * @param catalog          target catalog to register operators into
     * @param providerRegistry SPI provider registry used to resolve operator types
     * @param templateEngine   YAML template rendering engine
     * @param templateContext  variable bindings for {@code {{ varName }}} placeholders
     */
    public AepOperatorCatalogLoader(
            @NotNull OperatorCatalog catalog,
            @NotNull OperatorProviderRegistry providerRegistry,
            @NotNull YamlTemplateEngine templateEngine,
            @NotNull TemplateContext templateContext) {
        this.catalog          = Objects.requireNonNull(catalog,          "catalog cannot be null");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry cannot be null");
        this.templateEngine   = Objects.requireNonNull(templateEngine,   "templateEngine cannot be null");
        this.templateContext  = Objects.requireNonNull(templateContext,  "templateContext cannot be null");
    }

    /**
     * Convenience constructor using an empty template context (for YAMLs with no
     * {@code {{ … }}} placeholders).
     *
     * @param catalog          target catalog
     * @param providerRegistry SPI provider registry
     */
    public AepOperatorCatalogLoader(
            @NotNull OperatorCatalog catalog,
            @NotNull OperatorProviderRegistry providerRegistry) {
        this(catalog, providerRegistry, new YamlTemplateEngine(), TemplateContext.empty());
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Loads all operator YAML files from {@value DEFAULT_OPERATORS_DIR} on the classpath.
     *
     * <p>Discover → render → parse → register. If any YAML declares a type for
     * which no {@link OperatorProvider} is registered, an {@link IllegalStateException}
     * is thrown immediately to surface misconfigured deployments at startup.
     *
     * @return number of operators successfully registered
     * @throws IllegalStateException if an operator type has no registered provider
     */
    public int loadFromClasspath() {
        return loadFromClasspath(DEFAULT_OPERATORS_DIR);
    }

    /**
     * Loads all operator YAML files from the specified classpath directory.
     *
     * @param classpathDir classpath-relative directory (e.g. {@code "operators"})
     * @return number of operators successfully registered
     * @throws IllegalStateException if an operator type has no registered provider
     */
    public int loadFromClasspath(@NotNull String classpathDir) {
        Objects.requireNonNull(classpathDir, "classpathDir must not be null");

        URL dirUrl = Thread.currentThread().getContextClassLoader().getResource(classpathDir);
        if (dirUrl == null) {
            log.debug("Classpath directory '{}' not found — no operators loaded", classpathDir);
            return 0;
        }
        if (!"file".equals(dirUrl.getProtocol())) {
            log.warn("Classpath directory '{}' is inside a JAR; use loadFromDirectory() for JAR support",
                    classpathDir);
            return 0;
        }

        Path dirPath = Path.of(dirUrl.getPath());
        try {
            return loadFromDirectory(dirPath);
        } catch (IllegalStateException e) {
            throw e; // ISE for missing provider propagates unchanged
        } catch (IOException e) {
            log.warn("Failed to load operators from classpath '{}': {}", classpathDir, e.getMessage());
            return 0;
        }
    }

    /**
     * Loads all {@code *.yaml} and {@code *.yml} files from the given filesystem directory.
     *
     * @param directory directory to scan (must exist and be a directory)
     * @return number of operators successfully registered
     * @throws IOException           if the directory cannot be read
     * @throws IllegalStateException if an operator type has no registered provider
     */
    public int loadFromDirectory(@NotNull Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        if (!Files.isDirectory(directory)) {
            throw new IOException("Not a directory: " + directory);
        }

        int count = 0;
        List<Path> yamlFiles;
        try (Stream<Path> entries = Files.list(directory)) {
            yamlFiles = entries
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    .toList();
        }

        for (Path yamlFile : yamlFiles) {
            try {
                int loaded = loadOperatorFromPath(yamlFile);
                count += loaded;
            } catch (IllegalStateException e) {
                throw e; // ISE for missing provider propagates unchanged
            } catch (Exception e) {
                log.warn("Failed to load operator YAML '{}': {}", yamlFile.getFileName(), e.getMessage());
            }
        }

        log.info("AepOperatorCatalogLoader: registered {} operators from '{}'", count, directory);
        return count;
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /**
     * Processes a single operator YAML file:
     * <ol>
     *   <li>Read raw YAML bytes</li>
     *   <li>Render via {@link YamlTemplateEngine#render} (substitutes any {@code {{ … }}} tokens)</li>
     *   <li>Parse into {@code Map<String, Object>}</li>
     *   <li>Extract {@code id}, {@code version}, {@code namespace}, {@code aep.operatorType}</li>
     *   <li>Resolve {@link OperatorProvider} — throws {@link IllegalStateException} if absent</li>
     *   <li>Create and register the {@link UnifiedOperator}</li>
     * </ol>
     *
     * @param yamlFile path to the operator YAML file
     * @return 1 if registered, 0 if skipped (missing required {@code id} field)
     * @throws IOException           if the file cannot be read
     * @throws IllegalStateException if no provider supports the declared operator type
     */
    private int loadOperatorFromPath(@NotNull Path yamlFile) throws IOException {
        String rawYaml;
        try (InputStream in = Files.newInputStream(yamlFile)) {
            rawYaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Apply template substitution before parsing
        String renderedYaml = templateEngine.render(rawYaml, templateContext);

        // Parse into a flat Map for flexible field extraction
        Map<String, Object> def = YAML_MAPPER.readValue(renderedYaml,
                new TypeReference<Map<String, Object>>() {});

        // Extract required 'id' field
        String id = getString(def, "id");
        if (id == null || id.isBlank()) {
            log.warn("Operator YAML '{}' has no 'id' field — skipping", yamlFile.getFileName());
            return 0;
        }

        // Extract version (optional, defaults to 1.0.0)
        String version = getString(def, "version");
        if (version == null || version.isBlank()) {
            version = DEFAULT_VERSION;
        }

        // Extract namespace and operatorType from the 'aep' sub-section or top level
        String namespace    = DEFAULT_NAMESPACE;
        String operatorType = resolveOperatorType(def);

        OperatorId operatorId = OperatorId.of(namespace, operatorType, id, version);
        OperatorConfig config = buildConfig(def);

        // Look up provider — throw ISE if not found (no silent skip per spec)
        OperatorProvider provider = providerRegistry.findByOperatorId(operatorId)
                .orElseThrow(() -> new IllegalStateException(
                        ("No OperatorProvider is registered for operator '%s' (type='%s'). " +
                         "Ensure a 'META-INF/services/com.ghatana.core.operator.spi.OperatorProvider' " +
                         "entry is present in the declaring module's resources.")
                        .formatted(operatorId, operatorType)));

        UnifiedOperator operator = provider.createOperator(operatorId, config);

        // DefaultOperatorCatalog.register() returns Promise.complete() — safe to ignore promise
        catalog.register(operator);

        log.info("Registered operator {} from '{}'", operatorId, yamlFile.getFileName());
        return 1;
    }

    /**
     * Resolves the operator type from the YAML definition.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code aep.operatorType} (preferred — section-scoped)</li>
     *   <li>{@code operatorType} (top-level fallback)</li>
     *   <li>{@code "agent"} (default)</li>
     * </ol>
     */
    private String resolveOperatorType(Map<String, Object> def) {
        @SuppressWarnings("unchecked")
        Map<String, Object> aepSection = (Map<String, Object>) def.get("aep");
        if (aepSection != null) {
            Object opType = aepSection.get("operatorType");
            if (opType != null && !opType.toString().isBlank()) {
                return opType.toString().toLowerCase();
            }
        }
        Object topLevel = def.get("operatorType");
        return (topLevel != null && !topLevel.toString().isBlank())
                ? topLevel.toString().toLowerCase()
                : DEFAULT_TYPE;
    }

    /**
     * Converts flat YAML fields into an {@link OperatorConfig} by extracting
     * all top-level string-valued fields as config properties.
     *
     * <p>Non-string values are converted via {@code toString()}.
     * Null values are omitted.
     */
    private OperatorConfig buildConfig(Map<String, Object> def) {
        OperatorConfig.Builder builder = OperatorConfig.builder();
        for (Map.Entry<String, Object> entry : def.entrySet()) {
            Object val = entry.getValue();
            if (val != null) {
                builder.withProperty(entry.getKey(), val.toString());
            }
        }
        return builder.build();
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
