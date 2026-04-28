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
import java.util.List;
import java.util.Optional;

/**
 * Loads lifecycle phase transition rules from {@code config/lifecycle/transitions.yaml}
 * at service startup.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>External: {@code ${yappc.config.dir}/lifecycle/transitions.yaml}</li>
 *   <li>Classpath fallback: {@code /lifecycle/transitions.yaml}</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Loads lifecycle transition rules from YAML at startup
 * @doc.layer product
 * @doc.pattern Service
 */
public class TransitionConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(TransitionConfigLoader.class);
    private static final String CONFIG_DIR_PROP   = "yappc.config.dir";
    private static final String RELATIVE_PATH     = "lifecycle/transitions.yaml";

    private final List<TransitionSpec> transitions;

    public TransitionConfigLoader() {
        this.transitions = load();
        log.info("TransitionConfigLoader: loaded {} transition rules", transitions.size());
    }

    /**
     * Returns the {@link TransitionSpec} that matches the given from/to phases,
     * or {@link Optional#empty()} if no rule permits the transition.
     */
    public Optional<TransitionSpec> findTransition(String fromPhase, String toPhase) {
        return transitions.stream()
            .filter(t -> t.matches(fromPhase, toPhase))
            .findFirst();
    }

    /** Returns an unmodifiable view of all loaded transition specs. */
    public List<TransitionSpec> getAll() {
        return transitions;
    }

    // ─── Private loading ──────────────────────────────────────────────────────

    private List<TransitionSpec> load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        String configDir = System.getProperty(CONFIG_DIR_PROP);
        if (configDir != null && !configDir.isBlank()) {
            Path external = Paths.get(configDir, RELATIVE_PATH);
            if (Files.exists(external)) {
                try (InputStream is = Files.newInputStream(external)) {
                    List<TransitionSpec> result = parse(mapper, is);
                    log.info("TransitionConfigLoader: loaded from external path {}", external);
                    return result;
                } catch (IOException e) {
                    log.warn("TransitionConfigLoader: could not read {}, falling back to classpath", external, e);
                }
            }
        }

        InputStream is = TransitionConfigLoader.class.getResourceAsStream("/" + RELATIVE_PATH);
        if (is == null) {
            log.warn("TransitionConfigLoader: {} not found on classpath — no transition rules loaded", RELATIVE_PATH);
            return List.of();
        }
        try (is) {
            return parse(mapper, is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse classpath transitions.yaml", e);
        }
    }

    private List<TransitionSpec> parse(ObjectMapper mapper, InputStream is) throws IOException {
        TransitionsManifest manifest = mapper.readValue(is, TransitionsManifest.class);
        if (manifest.transitions == null) return List.of();
        return List.copyOf(manifest.transitions);
    }

    // ─── Inner manifest model ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class TransitionsManifest {
        @JsonProperty("transitions")
        List<TransitionSpec> transitions;
    }
}
