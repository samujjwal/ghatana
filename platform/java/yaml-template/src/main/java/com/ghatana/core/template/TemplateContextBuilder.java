package com.ghatana.core.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a {@link TemplateContext} by merging variable sources in ascending
 * priority order:
 *
 * <ol>
 *   <li><b>System environment variables</b> — lowest priority</li>
 *   <li><b>Platform catalog defaults</b> — {@code platform/agent-catalog/values.yaml}
 *       from the filesystem (relative to the working directory) or classpath</li>
 *   <li><b>Local values file</b> — optional caller-supplied path</li>
 *   <li><b>Explicit parameters</b> — highest priority</li>
 * </ol>
 *
 * <p>All YAML files are expected to contain a flat {@code key: value} mapping.
 * Nested structures are silently ignored during string coercion.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TemplateContext ctx = TemplateContextBuilder.build(
 *     "agents/my-agent/values.yaml",
 *     Map.of("tenantId", "acme")
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Merges environment variables and YAML files into a TemplateContext
 * @doc.layer platform
 * @doc.pattern Builder
 */
public final class TemplateContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(TemplateContextBuilder.class);

    /** Path to the platform-wide catalog defaults, relative to working directory. */
    static final String CATALOG_VALUES_PATH = "platform/agent-catalog/values.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // ─── Static factory ────────────────────────────────────────────────────────

    /**
     * Builds a {@link TemplateContext} from the environment, catalog defaults,
     * an optional local values file, and explicit overrides.
     *
     * <p>This is the primary entry point for obtaining a fully-populated context.
     *
     * @param localValuesPath path to a local {@code values.yaml} (may be {@code null})
     * @param explicitParams  highest-priority overrides (may be {@code null} or empty)
     * @return merged, immutable {@link TemplateContext}
     */
    @NotNull
    public static TemplateContext build(
            @Nullable String localValuesPath,
            @Nullable Map<String, String> explicitParams) {
        return new TemplateContextBuilder().buildContext(localValuesPath, explicitParams);
    }

    /**
     * Builds a {@link TemplateContext} from the environment and catalog defaults
     * only — convenience overload when no local overrides are needed.
     *
     * @return merged, immutable {@link TemplateContext}
     */
    @NotNull
    public static TemplateContext fromEnvironment() {
        return build(null, null);
    }

    // ─── Instance implementation ───────────────────────────────────────────────

    @NotNull
    private TemplateContext buildContext(
            @Nullable String localValuesPath,
            @Nullable Map<String, String> explicitParams) {

        Map<String, String> merged = new LinkedHashMap<>();

        // 1. System environment variables (lowest priority)
        System.getenv().forEach((k, v) -> {
            if (k != null && v != null) merged.put(k, v);
        });
        log.debug("Loaded {} environment variable(s)", merged.size());

        // 2. Platform catalog defaults
        Map<String, String> catalogValues = loadFlatYaml(CATALOG_VALUES_PATH);
        merged.putAll(catalogValues);
        log.debug("Loaded {} catalog default(s) from '{}'", catalogValues.size(), CATALOG_VALUES_PATH);

        // 3. Local values file (optional)
        if (localValuesPath != null) {
            Map<String, String> localValues = loadFlatYaml(localValuesPath);
            merged.putAll(localValues);
            log.debug("Loaded {} local value(s) from '{}'", localValues.size(), localValuesPath);
        }

        // 4. Explicit parameters (highest priority)
        if (explicitParams != null && !explicitParams.isEmpty()) {
            merged.putAll(explicitParams);
            log.debug("Applied {} explicit override(s)", explicitParams.size());
        }

        return TemplateContext.of(merged);
    }

    /**
     * Loads a flat {@code key: value} YAML file from the filesystem, falling back to
     * the classpath. Returns an empty map if the resource is completely absent.
     *
     * <p>Non-string values are coerced via {@link Object#toString()}.
     * Only top-level scalar entries are included — nested objects are skipped.
     *
     * @param pathStr filesystem path (relative or absolute) or classpath resource name
     * @return flattened string map, never {@code null}
     */
    @NotNull
    Map<String, String> loadFlatYaml(@NotNull String pathStr) {
        Objects.requireNonNull(pathStr, "pathStr must not be null");

        // Try filesystem first
        Path fsPath = Path.of(pathStr);
        if (Files.exists(fsPath)) {
            try (InputStream is = Files.newInputStream(fsPath)) {
                return flattenYaml(yamlMapper.readValue(is, new TypeReference<Map<Object, Object>>() {}));
            } catch (IOException e) {
                log.warn("Failed to load YAML from filesystem path '{}': {}", pathStr, e.getMessage());
            }
        }

        // Fallback to classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(pathStr)) {
            if (is == null) {
                log.debug("YAML values file '{}' not found on filesystem or classpath — skipping", pathStr);
                return Map.of();
            }
            return flattenYaml(yamlMapper.readValue(is, new TypeReference<Map<Object, Object>>() {}));
        } catch (IOException e) {
            log.warn("Failed to load YAML from classpath resource '{}': {}", pathStr, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Converts a raw YAML parse result into a flat {@code Map<String, String>}.
     * Only top-level entries whose values are non-null scalars (not maps or lists)
     * are included.
     *
     * @param raw raw parse result (may be {@code null} for empty files)
     * @return flat string map
     */
    @NotNull
    private Map<String, String> flattenYaml(@Nullable Map<Object, Object> raw) {
        if (raw == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k == null || v == null) return;
            if (v instanceof Map || v instanceof Iterable) {
                log.debug("Skipping nested YAML key '{}' (only flat entries supported)", k);
                return;
            }
            result.put(k.toString(), v.toString());
        });
        return result;
    }
}
