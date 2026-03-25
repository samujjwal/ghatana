/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads YAPPC policy definitions from {@code config/policies/*.yaml} at service startup.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>External directory: {@code ${yappc.config.dir}/policies/*.yaml}</li>
 *   <li>Classpath fallback directory: {@code /policies/*.yaml}</li>
 * </ol>
 *
 * <p>All YAML files in the resolved directory are merged into a single policy index.
 * Duplicate policy IDs across files cause a fast-fail {@link IllegalStateException}.
 * Fail-fast parse errors surface as {@link IllegalStateException} so the service will
 * not start with broken configuration (plan item 8.2.7).
 *
 * <p>Thread-safe after construction — all mutable state is eliminated during startup.
 *
 * @doc.type class
 * @doc.purpose Loads and indexes YAPPC policy definitions from YAML directory at startup
 * @doc.layer product
 * @doc.pattern Service
 */
public class PolicyConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(PolicyConfigLoader.class);

    private static final String CONFIG_DIR_PROP  = "yappc.config.dir";
    private static final String RELATIVE_DIR     = "policies";
    private static final String CLASSPATH_POLICIES_DIR = "/policies";

    /** Immutable snapshot of all currently-loaded policies, held in an atomically swappable reference. */
    private record Snapshot(List<PolicyDefinition> all, Map<String, PolicyDefinition> byId) {}

    private final AtomicReference<Snapshot> snapshot;

    /**
     * Constructs the loader and eagerly loads all {@code *.yaml} policy files.
     *
     * @throws IllegalStateException if any YAML file cannot be parsed or contains
     *                               a duplicate policy ID
     */
    public PolicyConfigLoader() {
        List<PolicyDefinition> loaded = load();
        this.snapshot = new AtomicReference<>(new Snapshot(loaded, buildIndex(loaded)));
        int ruleCount = loaded.stream().mapToInt(p -> p.getRules().size()).sum();
        log.info("PolicyConfigLoader: loaded {} policy definitions with {} rules total",
                loaded.size(), ruleCount);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns all loaded, enabled {@link PolicyDefinition}s in the order they were
     * discovered across files (stable within a single file, unspecified across files).
     *
     * @return unmodifiable list of all enabled policy definitions
     */
    public List<PolicyDefinition> getAll() {
        return snapshot.get().all().stream()
                .filter(PolicyDefinition::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * Finds a policy definition by its unique ID.
     *
     * @param id the policy identifier (e.g., {@code "phase_advance_policy"})
     * @return matching definition, or {@link Optional#empty()} if not found
     */
    public Optional<PolicyDefinition> findById(String id) {
        return Optional.ofNullable(snapshot.get().byId().get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Returns all enabled rules whose {@code applies_to.to_phase} equals {@code toPhase}.
     *
     * <p>Used by the lifecycle engine to evaluate policies before advancing to a new phase.
     *
     * @param toPhase the destination phase (e.g., {@code "PLANNING"})
     * @return flat list of matching enabled rules across all policies
     */
    public List<PolicyDefinition.Rule> getRulesForPhase(String toPhase) {
        Objects.requireNonNull(toPhase, "toPhase");
        return snapshot.get().all().stream()
                .filter(PolicyDefinition::isEnabled)
                .flatMap(p -> p.getRules().stream())
                .filter(PolicyDefinition.Rule::isEnabled)
                .filter(r -> r.getAppliesTo() != null
                        && toPhase.equalsIgnoreCase(r.getAppliesTo().getToPhase()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all enabled rules matching a specific phase transition.
     *
     * @param fromPhase source phase (e.g., {@code "DESIGN"})
     * @param toPhase   destination phase (e.g., {@code "PLANNING"})
     * @return flat list of matching enabled rules across all policies
     */
    public List<PolicyDefinition.Rule> getRulesForTransition(String fromPhase, String toPhase) {
        Objects.requireNonNull(fromPhase, "fromPhase");
        Objects.requireNonNull(toPhase,   "toPhase");
        return snapshot.get().all().stream()
                .filter(PolicyDefinition::isEnabled)
                .flatMap(p -> p.getRules().stream())
                .filter(PolicyDefinition.Rule::isEnabled)
                .filter(r -> {
                    if (r.getAppliesTo() == null) return false;
                    return toPhase.equalsIgnoreCase(r.getAppliesTo().getToPhase())
                            && fromPhase.equalsIgnoreCase(r.getAppliesTo().getFromPhase());
                })
                .collect(Collectors.toList());
    }

    /** Returns the total number of loaded policy definitions (enabled + disabled). */
    public int size() {
        return snapshot.get().all().size();
    }

    /**
     * Atomically reloads all policies from the given directory and replaces the
     * current snapshot.
     *
     * <p>This method is thread-safe and lock-free. The new set of policies becomes
     * visible to all subsequent callers of {@link #getAll()}, {@link #findById(String)},
     * {@link #getRulesForPhase(String)}, and {@link #getRulesForTransition(String, String)}
     * as soon as this method returns.
     *
     * <p>If parsing fails, the method throws {@link IllegalStateException} and the
     * existing snapshot is preserved (no partial update).
     *
     * @param policiesDir directory to reload from (must exist and contain {@code *.yaml} files)
     * @throws IllegalStateException if YAML parsing fails or duplicates are found
     */
    public void atomicReload(Path policiesDir) {
        List<PolicyDefinition> reloaded = loadAll(policiesDir);
        Map<String, PolicyDefinition> newIndex = buildIndex(reloaded);  // throws on duplicate
        Snapshot newSnapshot = new Snapshot(reloaded, newIndex);
        snapshot.set(newSnapshot);
        int ruleCount = reloaded.stream().mapToInt(p -> p.getRules().size()).sum();
        log.info("PolicyConfigLoader: hot-reloaded {} policy definitions with {} rules total from {}",
                reloaded.size(), ruleCount, policiesDir);
    }

    // ─── Directory-based loading (plan item 8.2.4) ───────────────────────────

    /**
     * Loads all {@code *.yaml} files from the given directory and returns a merged
     * flat list of every {@link PolicyDefinition} found across all files.
     *
     * <p>This method is also used by {@link com.ghatana.yappc.services.lifecycle.config.PolicyReloadListener}
     * on hot-reload events.
     *
     * @param policiesDir directory containing policy YAML files
     * @return flat list of all policy definitions from all files in the directory
     * @throws IllegalStateException if any file cannot be parsed
     */
    public static List<PolicyDefinition> loadAll(Path policiesDir) {
        Objects.requireNonNull(policiesDir, "policiesDir");
        if (!Files.isDirectory(policiesDir)) {
            log.warn("PolicyConfigLoader.loadAll: '{}' is not a directory — returning empty list", policiesDir);
            return List.of();
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        List<PolicyDefinition> merged = new ArrayList<>();

        try (Stream<Path> files = Files.list(policiesDir)) {
            List<Path> yamlFiles = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()              // deterministic order
                    .collect(Collectors.toList());

            for (Path file : yamlFiles) {
                try (InputStream is = Files.newInputStream(file)) {
                    List<PolicyDefinition> fromFile = parseEnvelope(mapper, is);
                    log.debug("PolicyConfigLoader: parsed {} policy definitions from {}", fromFile.size(), file);
                    merged.addAll(fromFile);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "PolicyConfigLoader: failed to parse policy file '" + file + "'", e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "PolicyConfigLoader: failed to list directory '" + policiesDir + "'", e);
        }

        return List.copyOf(merged);
    }

    // ─── Private loading ──────────────────────────────────────────────────────

    private List<PolicyDefinition> load() {
        String configDir = System.getProperty(CONFIG_DIR_PROP);
        if (configDir != null && !configDir.isBlank()) {
            Path externalDir = Paths.get(configDir, RELATIVE_DIR);
            if (Files.isDirectory(externalDir)) {
                log.info("PolicyConfigLoader: loading from external directory {}", externalDir);
                return loadAll(externalDir);    // fails fast on bad YAML
            } else {
                log.debug("PolicyConfigLoader: external directory '{}' does not exist — using classpath fallback",
                        externalDir);
            }
        }

        return loadFromClasspath();
    }

    private List<PolicyDefinition> loadFromClasspath() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        List<PolicyDefinition> merged = new ArrayList<>();

        try {
            URI uri = getClass().getResource(CLASSPATH_POLICIES_DIR).toURI();

            // Support running inside a JAR (where the "directory" is a zip entry)
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                    Path dirInJar = fs.getPath(CLASSPATH_POLICIES_DIR);
                    merged.addAll(loadFromDirectory(mapper, dirInJar));
                }
            } else {
                Path dir = Paths.get(uri);
                merged.addAll(loadFromDirectory(mapper, dir));
            }

        } catch (NullPointerException e) {
            log.warn("PolicyConfigLoader: classpath resource '{}' not found — no policies loaded",
                    CLASSPATH_POLICIES_DIR);
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(
                    "PolicyConfigLoader: failed to load classpath policies from '"
                            + CLASSPATH_POLICIES_DIR + "'", e);
        }

        if (merged.isEmpty()) {
            log.warn("PolicyConfigLoader: no policy definitions found in classpath '{}'",
                    CLASSPATH_POLICIES_DIR);
        }
        return List.copyOf(merged);
    }

    private List<PolicyDefinition> loadFromDirectory(ObjectMapper mapper, Path dir) throws IOException {
        List<PolicyDefinition> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> yamlFiles = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    .collect(Collectors.toList());

            for (Path file : yamlFiles) {
                try (InputStream is = Files.newInputStream(file)) {
                    List<PolicyDefinition> fromFile = parseEnvelope(mapper, is);
                    log.debug("PolicyConfigLoader: parsed {} definitions from classpath {}", fromFile.size(), file);
                    result.addAll(fromFile);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "PolicyConfigLoader: failed to parse classpath file '" + file + "'", e);
                }
            }
        }
        return result;
    }

    private static List<PolicyDefinition> parseEnvelope(ObjectMapper mapper, InputStream is)
            throws IOException {
        PolicyConfigFile envelope = mapper.readValue(is, PolicyConfigFile.class);
        return envelope.policies == null ? List.of() : List.copyOf(envelope.policies);
    }

    private static Map<String, PolicyDefinition> buildIndex(List<PolicyDefinition> definitions) {
        Map<String, PolicyDefinition> index = definitions.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(
                        PolicyDefinition::getId,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "PolicyConfigLoader: duplicate policy id '"
                                            + a.getId() + "' found in policy files");
                        }
                ));
        return Map.copyOf(index);
    }

    // ─── YAML envelope ────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PolicyConfigFile {
        @JsonProperty("policies")
        List<PolicyDefinition> policies;
    }
}
