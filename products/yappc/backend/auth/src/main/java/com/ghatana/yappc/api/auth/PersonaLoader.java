/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Backend Auth Module
 */
package com.ghatana.yappc.api.auth;

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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads persona definitions from {@code personas.yaml} at class-initialization time.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>External file: {@code ${yappc.config.dir}/personas/personas.yaml} (system property)</li>
 *   <li>Classpath fallback: {@code /personas/personas.yaml}</li>
 * </ol>
 *
 * <p>The loaded registry is immutable and available through static accessors. {@link PersonaMapping}
 * delegates all role and permission lookups to this class.
 *
 * @doc.type class
 * @doc.purpose Loads authoritative persona definitions from YAML at startup
 * @doc.layer api
 * @doc.pattern Service
 */
public final class PersonaLoader {

    private static final Logger log = LoggerFactory.getLogger(PersonaLoader.class);
    private static final String CONFIG_DIR_PROP = "yappc.config.dir";
    private static final String RELATIVE_PATH   = "personas/personas.yaml";

    /** Lazily-initialized + eagerly-loaded registry: personaId → PersonaDefinition. */
    private static final Map<String, PersonaDefinition> REGISTRY = load();

    private PersonaLoader() {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the {@link PersonaDefinition} for the given persona ID, or {@code null} if unknown.
     *
     * @param personaId upper-snake-case ID (e.g., {@code "DEVELOPER"})
     * @return definition, or {@code null}
     */
    public static PersonaDefinition get(String personaId) {
        return REGISTRY.get(personaId);
    }

    /**
     * Returns an unmodifiable view of all loaded persona definitions keyed by persona ID.
     *
     * @return all personas
     */
    public static Map<String, PersonaDefinition> getAll() {
        return REGISTRY;
    }

    /** Returns the number of loaded persona definitions. */
    public static int count() {
        return REGISTRY.size();
    }

    // ─── Internal loading ─────────────────────────────────────────────────

    private static Map<String, PersonaDefinition> load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // 1. Try external config directory first
        String configDir = System.getProperty(CONFIG_DIR_PROP);
        if (configDir != null && !configDir.isBlank()) {
            Path externalPath = Paths.get(configDir, RELATIVE_PATH);
            if (Files.exists(externalPath)) {
                try {
                    Map<String, PersonaDefinition> result = parse(mapper,
                        Files.newInputStream(externalPath));
                    log.info("PersonaLoader: loaded {} personas from external path {}",
                        result.size(), externalPath);
                    return result;
                } catch (IOException e) {
                    log.warn("PersonaLoader: failed to read external personas.yaml at {}, " +
                             "falling back to classpath", externalPath, e);
                }
            }
        }

        // 2. Classpath fallback
        InputStream classpathStream = PersonaLoader.class.getResourceAsStream(
            "/" + RELATIVE_PATH);
        if (classpathStream == null) {
            throw new IllegalStateException(
                "PersonaLoader: personas.yaml not found on classpath at /" + RELATIVE_PATH +
                " and no external path configured via -D" + CONFIG_DIR_PROP);
        }
        try {
            Map<String, PersonaDefinition> result = parse(mapper, classpathStream);
            log.info("PersonaLoader: loaded {} personas from classpath", result.size());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(
                "PersonaLoader: failed to parse classpath personas.yaml", e);
        }
    }

    private static Map<String, PersonaDefinition> parse(
            ObjectMapper mapper, InputStream stream) throws IOException {
        PersonasManifest manifest = mapper.readValue(stream, PersonasManifest.class);
        if (manifest.personas == null || manifest.personas.isEmpty()) {
            throw new IllegalStateException("personas.yaml contains no persona definitions");
        }
        return manifest.personas.stream()
            .collect(Collectors.toUnmodifiableMap(
                PersonaDefinition::getId,
                Function.identity()));
    }

    // ─── Inner manifest model ─────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class PersonasManifest {
        @JsonProperty("personas")
        List<PersonaDefinition> personas;
    }
}
