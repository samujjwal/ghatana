/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads YAPPC lifecycle stage definitions from {@code config/lifecycle/stages.yaml}
 * at service startup.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>External: {@code ${yappc.config.dir}/lifecycle/stages.yaml}</li>
 *   <li>Classpath fallback: {@code /lifecycle/stages.yaml}</li>
 * </ol>
 *
 * <p>Stages are indexed by {@link StageSpec#getId()} for O(1) lookup.
 *
 * @doc.type class
 * @doc.purpose Loads YAPPC lifecycle stage definitions from YAML at startup
 * @doc.layer product
 * @doc.pattern Service
 */
public class StageConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(StageConfigLoader.class);
    private static final String CONFIG_DIR_PROP = "yappc.config.dir";
    private static final String RELATIVE_PATH   = "lifecycle/stages.yaml";
    private static final String CLASSPATH_PATH  = "/lifecycle/stages.yaml";

    private final List<StageSpec> stages;
    private final Map<String, StageSpec> byId;

    /** Constructs the loader and eagerly loads {@code stages.yaml}. */
    public StageConfigLoader() {
        this.stages = load();
        this.byId = stages.stream()
                .collect(Collectors.toMap(StageSpec::getId, Function.identity()));
        log.info("StageConfigLoader: loaded {} stage definitions: {}",
                stages.size(), stages.stream().map(StageSpec::getId).collect(Collectors.joining(", ")));
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the {@link StageSpec} for the given stage ID, or {@link Optional#empty()}
     * if no stage with that ID was loaded.
     *
     * @param stageId the stage identifier (e.g., {@code "intent"}, {@code "plan"})
     * @return matching stage spec, if any
     */
    public Optional<StageSpec> findById(String stageId) {
        return Optional.ofNullable(byId.get(stageId));
    }

    /**
     * Returns the spec for the given stage ID.
     *
     * @throws IllegalArgumentException if no stage with that ID is configured
     */
    public StageSpec getById(String stageId) {
        return findById(stageId).orElseThrow(() ->
                new IllegalArgumentException("Unknown stage id: '" + stageId + "'. "
                        + "Available: " + byId.keySet()));
    }

    /** Returns all stages sorted by their natural {@link StageSpec#getOrder()} value. */
    public List<StageSpec> getAll() {
        return stages.stream()
                .sorted(Comparator.comparingInt(StageSpec::getOrder))
                .collect(Collectors.toList());
    }

    /** Returns the number of configured stages. */
    public int size() {
        return stages.size();
    }

    // ─── Private loading ──────────────────────────────────────────────────────

    private List<StageSpec> load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        String configDir = System.getProperty(CONFIG_DIR_PROP);
        if (configDir != null && !configDir.isBlank()) {
            Path external = Paths.get(configDir, RELATIVE_PATH);
            if (Files.exists(external)) {
                try (InputStream is = Files.newInputStream(external)) {
                    List<StageSpec> result = parse(mapper, is);
                    log.info("StageConfigLoader: loaded from external path {}", external);
                    return result;
                } catch (IOException e) {
                    log.warn("StageConfigLoader: could not read {}, falling back to classpath", external, e);
                }
            }
        }

        // Classpath fallback
        InputStream classpathStream = getClass().getResourceAsStream(CLASSPATH_PATH);
        if (classpathStream == null) {
            log.error("StageConfigLoader: no '{}' found on classpath — returning empty stage list", CLASSPATH_PATH);
            return List.of();
        }
        try (InputStream is = classpathStream) {
            List<StageSpec> result = parse(mapper, is);
            log.info("StageConfigLoader: loaded from classpath {}", CLASSPATH_PATH);
            return result;
        } catch (IOException e) {
            log.error("StageConfigLoader: failed to parse classpath {}", CLASSPATH_PATH, e);
            return List.of();
        }
    }

    private List<StageSpec> parse(ObjectMapper mapper, InputStream is) throws IOException {
        StageConfigFile config = mapper.readValue(is, StageConfigFile.class);
        return config.stages == null ? List.of() : List.copyOf(config.stages);
    }

    // ─── JSON envelope ────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class StageConfigFile {
        @JsonProperty("stages")
        List<StageSpec> stages;
    }
}
