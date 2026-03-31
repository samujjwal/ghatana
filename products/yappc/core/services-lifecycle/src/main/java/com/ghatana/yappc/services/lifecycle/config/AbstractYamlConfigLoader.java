/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for YAML-file-based configuration loaders.
 *
 * <p>Provides the common directory-walking, classpath-fallback, and file-filtering logic
 * shared by {@link PolicyConfigLoader} and {@link StageConfigLoader} (and any future config
 * loaders).  Concrete subclasses only need to implement {@link #parseFile} to deserialize
 * a single file's {@link InputStream} into a list of domain objects.
 *
 * <p><b>Resolution order:</b></p>
 * <ol>
 *   <li>External directory: {@code ${yappc.config.dir}/<relativePath>}</li>
 *   <li>Classpath fallback: {@code /<classpathPath>} (supports running inside a JAR)</li>
 * </ol>
 *
 * <p>Only {@code *.yaml} and {@code *.yml} files are considered.  Files are processed in
 * lexicographic order for deterministic behaviour across restarts.
 *
 * @param <T> the domain type produced by each YAML file
 *
 * @doc.type class
 * @doc.purpose Abstract base for YAML config loaders - eliminates duplicated loading logic
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class AbstractYamlConfigLoader<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractYamlConfigLoader.class);

    private static final String CONFIG_DIR_PROP = "yappc.config.dir";

    /** Shared, thread-safe YAML mapper (ObjectMapper is thread-safe after construction). */
    protected final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /** Relative sub-path appended to {@code yappc.config.dir} for the external directory. */
    private final String externalRelativePath;

    /** Absolute classpath path used as the fallback (e.g. {@code "/policies"}). */
    private final String classpathPath;

    /** Human-readable loader name for log messages. */
    private final String loaderName;

    /**
     * @param externalRelativePath sub-path under {@code yappc.config.dir}
     *                             (e.g. {@code "policies"})
     * @param classpathPath        absolute classpath path for the fallback directory or single file
     *                             (e.g. {@code "/policies"})
     * @param loaderName           label used in log messages (e.g. {@code "PolicyConfigLoader"})
     */
    protected AbstractYamlConfigLoader(
            String externalRelativePath,
            String classpathPath,
            String loaderName) {
        this.externalRelativePath = externalRelativePath;
        this.classpathPath        = classpathPath;
        this.loaderName           = loaderName;
    }

    /**
     * Loads all {@code *.yaml}/{@code *.yml} files from the resolved directory (external or
     * classpath fallback) and returns a merged, unmodifiable list of domain objects.
     *
     * @return merged list from all discovered YAML files; never {@code null}
     * @throws IllegalStateException if any file cannot be parsed
     */
    protected List<T> loadAll() {
        String configDir = System.getProperty(CONFIG_DIR_PROP);
        if (configDir != null && !configDir.isBlank()) {
            Path externalDir = Paths.get(configDir, externalRelativePath);
            if (Files.isDirectory(externalDir)) {
                log.info("{}: loading from external directory {}", loaderName, externalDir);
                return loadFromDirectory(externalDir);
            }
            log.debug("{}: external path '{}' not found — using classpath fallback",
                    loaderName, externalDir);
        }
        return loadFromClasspath();
    }

    /**
     * Loads all YAML files inside a single (already-resolved) filesystem directory.
     *
     * <p>Can also be called directly for hot-reload scenarios.
     *
     * @param dir the directory to scan
     * @return merged list from all {@code *.yaml}/{@code *.yml} files; never {@code null}
     * @throws IllegalStateException if any file cannot be parsed or the directory cannot be listed
     */
    protected List<T> loadFromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            log.warn("{}.loadFromDirectory: '{}' is not a directory — returning empty list",
                    loaderName, dir);
            return List.of();
        }

        List<T> merged = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> yamlFiles = files
                    .filter(AbstractYamlConfigLoader::isYamlFile)
                    .sorted()
                    .collect(Collectors.toList());

            for (Path file : yamlFiles) {
                try (InputStream is = Files.newInputStream(file)) {
                    List<T> fromFile = parseFile(mapper, is);
                    log.debug("{}: parsed {} items from {}", loaderName, fromFile.size(), file);
                    merged.addAll(fromFile);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            loaderName + ": failed to parse file '" + file + "'", e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    loaderName + ": failed to list directory '" + dir + "'", e);
        }
        return List.copyOf(merged);
    }

    // ─── Subclass contract ────────────────────────────────────────────────────

    /**
     * Deserializes a single YAML file's content into a list of domain objects.
     *
     * <p>Implementations should not close {@code is}; the caller manages the stream lifecycle.
     *
     * @param mapper the shared {@link ObjectMapper} configured for YAML
     * @param is     the input stream of a single YAML file
     * @return items parsed from the file (must not be {@code null})
     * @throws IOException if the file cannot be read or deserialized
     */
    protected abstract List<T> parseFile(ObjectMapper mapper, InputStream is) throws IOException;

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<T> loadFromClasspath() {
        List<T> merged = new ArrayList<>();
        try {
            java.net.URL resource = getClass().getResource(classpathPath);
            if (resource == null) {
                log.warn("{}: classpath resource '{}' not found — returning empty list",
                        loaderName, classpathPath);
                return List.of();
            }

            URI uri = resource.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                    Path dirInJar = fs.getPath(classpathPath);
                    if (Files.isDirectory(dirInJar)) {
                        try (Stream<Path> files = Files.list(dirInJar)) {
                            merged.addAll(loadEntries(files));
                        }
                    } else {
                        // single file fallback
                        try (InputStream is = Files.newInputStream(dirInJar)) {
                            merged.addAll(parseFile(mapper, is));
                        }
                    }
                }
            } else {
                Path path = Paths.get(uri);
                if (Files.isDirectory(path)) {
                    try (Stream<Path> files = Files.list(path)) {
                        merged.addAll(loadEntries(files));
                    }
                } else {
                    try (InputStream is = Files.newInputStream(path)) {
                        merged.addAll(parseFile(mapper, is));
                    }
                }
            }
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(
                    loaderName + ": failed to load classpath resource '" + classpathPath + "'", e);
        }

        if (merged.isEmpty()) {
            log.warn("{}: no items found in classpath '{}'", loaderName, classpathPath);
        }
        return List.copyOf(merged);
    }

    private List<T> loadEntries(Stream<Path> files) throws IOException {
        List<T> result = new ArrayList<>();
        List<Path> yamlFiles = files
                .filter(AbstractYamlConfigLoader::isYamlFile)
                .sorted()
                .collect(Collectors.toList());

        for (Path file : yamlFiles) {
            try (InputStream is = Files.newInputStream(file)) {
                result.addAll(parseFile(mapper, is));
            } catch (IOException e) {
                throw new IllegalStateException(
                        loaderName + ": failed to parse classpath file '" + file + "'", e);
            }
        }
        return result;
    }

    private static boolean isYamlFile(Path p) {
        String name = p.getFileName().toString();
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }
}
