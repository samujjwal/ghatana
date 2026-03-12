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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YAML template engine supporting {@code {{ varName }}} variable substitution
 * and {@code extends:} file inheritance chains.
 *
 * <h2>Variable Substitution</h2>
 * <p>All {@code {{ varName }}} placeholders are replaced with values from a
 * {@link TemplateContext}. Unknown variables cause an {@link IllegalStateException}
 * rather than silent passthrough, forcing explicit declaration of all template
 * dependencies.
 *
 * <h2>Inheritance</h2>
 * <p>A YAML template may declare {@code extends: base-template.yaml} as a top-level
 * key. The engine resolves the chain recursively (max depth {@value MAX_EXTENDS_DEPTH})
 * and merges parent fields under child fields (child wins on conflict). Cycle detection
 * prevents infinite loops.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * YamlTemplateEngine engine = new YamlTemplateEngine();
 * TemplateContext ctx = TemplateContext.builder()
 *     .put("tenantId", "acme")
 *     .put("model",    "gpt-4o")
 *     .build();
 *
 * // Simple render
 * String rendered = engine.render(rawYamlString, ctx);
 *
 * // File with inheritance
 * String rendered = engine.renderWithInheritance(Path.of("agents/my-agent.yaml"), ctx);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose YAML template engine with variable substitution and file inheritance
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public class YamlTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(YamlTemplateEngine.class);

    /** Maximum {@code extends} inheritance depth to prevent runaway resolution. */
    public static final int MAX_EXTENDS_DEPTH = 3;

    /** Pattern matching {@code {{ varName }}} with optional surrounding whitespace. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /** YAML key that triggers inheritance resolution. */
    private static final String EXTENDS_KEY = "extends";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // ─── Core API ─────────────────────────────────────────────────────────────

    /**
     * Renders a raw YAML string by substituting all {@code {{ varName }}} placeholders
     * with values from {@code ctx}.
     *
     * @param rawYaml raw YAML string (may contain {@code {{ … }}} tokens)
     * @param ctx     variable bindings
     * @return rendered YAML string with all placeholders replaced
     * @throws IllegalStateException if any placeholder references a variable not in {@code ctx}
     */
    @NotNull
    public String render(@NotNull String rawYaml, @NotNull TemplateContext ctx) {
        Objects.requireNonNull(rawYaml, "rawYaml must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER.matcher(rawYaml);

        while (matcher.find()) {
            String varName = matcher.group(1);
            // delegating to TemplateContext.get() which throws ISE on missing key
            String replacement = ctx.get(varName);
            // Escape special chars in replacement for Matcher.appendReplacement
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Renders a YAML template file after resolving its {@code extends} chain.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Load {@code file} from filesystem.</li>
     *   <li>If it declares {@code extends: some-parent.yaml}, load the parent
     *       (relative to the same directory as {@code file}), resolving recursively up
     *       to {@value MAX_EXTENDS_DEPTH} levels.</li>
     *   <li>Merge all layers (grandparent ← parent ← child; child values win).</li>
     *   <li>Remove the {@code extends} key from the merged map.</li>
     *   <li>Serialise the merged map back to YAML.</li>
     *   <li>Run {@link #render(String, TemplateContext)} on the resulting string.</li>
     * </ol>
     *
     * @param file absolute or relative path to the root template file
     * @param ctx  variable bindings for placeholder substitution
     * @return rendered, merged YAML string
     * @throws IllegalStateException if the extends chain exceeds {@value MAX_EXTENDS_DEPTH},
     *                               a cycle is detected, or any placeholder has no binding
     * @throws IOException           if any referenced file cannot be read
     */
    @NotNull
    public String renderWithInheritance(@NotNull Path file, @NotNull TemplateContext ctx)
            throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        Map<String, Object> merged = resolveInheritance(file, new LinkedHashSet<>(), 0);
        // Strip the 'extends' key from the output
        merged.remove(EXTENDS_KEY);

        String mergedYaml = yamlMapper.writeValueAsString(merged);
        return render(mergedYaml, ctx);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Recursively resolves the inheritance chain and returns a fully-merged map.
     *
     * @param file    current file to load
     * @param visited canonical paths of already-visited files (cycle detection)
     * @param depth   current resolution depth
     * @return merged property map (child values override parent values)
     */
    @NotNull
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveInheritance(
            @NotNull Path file, @NotNull Set<String> visited, int depth) throws IOException {

        if (depth > MAX_EXTENDS_DEPTH) {
            throw new IllegalStateException(
                    "YAML template extends chain exceeds maximum depth of " + MAX_EXTENDS_DEPTH
                    + ". Aborting to prevent runaway inheritance at file: " + file);
        }

        String canonical = file.toAbsolutePath().normalize().toString();
        if (visited.contains(canonical)) {
            throw new IllegalStateException(
                    "Circular extends detected for template file: " + file
                    + ". Inheritance chain: " + visited);
        }
        visited.add(canonical);

        // Load the current file
        Map<String, Object> current = loadYamlMap(file);

        // Check for 'extends' key
        Object extendsValue = current.get(EXTENDS_KEY);
        if (extendsValue == null) {
            return current;
        }

        String parentFileName = extendsValue.toString();
        Path parentFile = file.toAbsolutePath().getParent().resolve(parentFileName).normalize();

        log.debug("Resolving YAML inheritance: {} extends {}", file.getFileName(), parentFileName);

        Map<String, Object> parent = resolveInheritance(parentFile, visited, depth + 1);

        // Merge: parent base, then child overrides
        Map<String, Object> merged = deepMerge(parent, current);
        return merged;
    }

    /**
     * Deep-merges {@code override} into {@code base}. For nested maps, recursion
     * applies so child nested values selectively override parent nested values.
     * Scalar and list values in {@code override} always win.
     *
     * @param base     source/parent map
     * @param override child/overriding map
     * @return merged map (new instance, does not mutate inputs)
     */
    @NotNull
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> deepMerge(
            @NotNull Map<String, Object> base,
            @NotNull Map<String, Object> override) {

        Map<String, Object> result = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object overrideVal = entry.getValue();
            Object baseVal = result.get(key);

            if (baseVal instanceof Map && overrideVal instanceof Map) {
                result.put(key, deepMerge((Map<String, Object>) baseVal,
                        (Map<String, Object>) overrideVal));
            } else {
                result.put(key, overrideVal);
            }
        }
        return result;
    }

    /**
     * Loads a YAML file from the filesystem and returns its content as a
     * {@code Map<String, Object>}.
     *
     * @param file path to the YAML file
     * @return parsed content
     * @throws IOException if the file cannot be read or parsed
     */
    @NotNull
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYamlMap(@NotNull Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("YAML template file not found: " + file);
        }
        try (InputStream is = Files.newInputStream(file)) {
            Map<String, Object> result = yamlMapper.readValue(
                    is, new TypeReference<Map<String, Object>>() {});
            return result != null ? result : new LinkedHashMap<>();
        }
    }

    /**
     * Loads a YAML file from the classpath and returns its content as a map.
     * Returns an empty map if the resource is not found.
     *
     * @param classpathResource classpath-relative path (e.g. {@code "values.yaml"})
     * @return parsed content or empty map if resource absent
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public Map<String, String> loadClasspathValues(@NotNull String classpathResource) {
        Objects.requireNonNull(classpathResource, "classpathResource must not be null");
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (is == null) {
                log.debug("Classpath resource '{}' not found — skipping", classpathResource);
                return Map.of();
            }
            Map<Object, Object> raw = yamlMapper.readValue(
                    is, new TypeReference<Map<Object, Object>>() {});
            if (raw == null) return Map.of();
            // Convert all values to strings for use in TemplateContext
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                if (k != null && v != null) result.put(k.toString(), v.toString());
            });
            return result;
        } catch (IOException e) {
            log.warn("Failed to load classpath values from '{}': {}", classpathResource, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Convenience method: renders a classpath resource (does not support {@code extends}).
     *
     * @param classpathResource classpath path to the YAML resource
     * @param ctx               variable context
     * @return rendered YAML string
     * @throws IOException           if resource cannot be read
     * @throws IllegalStateException if any placeholder has no binding
     */
    @NotNull
    public String renderClasspathResource(
            @NotNull String classpathResource, @NotNull TemplateContext ctx) throws IOException {
        Objects.requireNonNull(classpathResource, "classpathResource must not be null");
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + classpathResource);
            }
            String raw = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return render(raw, ctx);
        }
    }
}
