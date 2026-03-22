package com.ghatana.core.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable bag of named variables used by {@link YamlTemplateEngine} to resolve
 * {@code {{ varName }}} placeholders in YAML template files.
 *
 * <p>Instances are created via {@link #of(Map)} or {@link #builder()}. Keys are
 * case-sensitive. {@link #get(String)} throws {@link IllegalStateException} if the
 * key is absent — this is intentional: templates must be fully-specified; silent
 * defaults mask configuration errors.
 *
 * @doc.type class
 * @doc.purpose Immutable variable context for YAML template rendering
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class TemplateContext {

    private final Map<String, String> variables;

    private TemplateContext(Map<String, String> variables) {
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a context from an existing map. The map is defensively copied.
     *
     * @param variables variable map (may not be null)
     * @return a new immutable context
     */
    @NotNull
    public static TemplateContext of(@NotNull Map<String, String> variables) {
        Objects.requireNonNull(variables, "variables must not be null");
        return new TemplateContext(variables);
    }

    /**
     * Creates an empty context (useful for templates with no placeholders).
     */
    @NotNull
    public static TemplateContext empty() {
        return new TemplateContext(Map.of());
    }

    /**
     * Returns a new {@link Builder} for constructing a context programmatically.
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the value for {@code key}.
     *
     * @param key variable name
     * @return the resolved value
     * @throws IllegalStateException if the key is absent
     */
    @NotNull
    public String get(@NotNull String key) {
        String value = variables.get(Objects.requireNonNull(key, "key must not be null"));
        if (value == null) {
            throw new IllegalStateException(
                    "Template variable '" + key + "' is not defined in this TemplateContext. "
                    + "Available keys: " + variables.keySet());
        }
        return value;
    }

    /**
     * Returns the value for {@code key}, or {@code defaultValue} if absent.
     *
     * @param key          variable name
     * @param defaultValue fallback value
     * @return resolved value or fallback
     */
    @Nullable
    public String getOrDefault(@NotNull String key, @Nullable String defaultValue) {
        return variables.getOrDefault(
                Objects.requireNonNull(key, "key must not be null"), defaultValue);
    }

    /**
     * Returns {@code true} if this context contains a binding for {@code key}.
     */
    public boolean has(@NotNull String key) {
        return variables.containsKey(Objects.requireNonNull(key, "key must not be null"));
    }

    /**
     * Returns an unmodifiable view of all variable bindings.
     */
    @NotNull
    public Map<String, String> variables() {
        return variables;
    }

    /**
     * Returns a new context that is the combination of {@code this} and {@code other}.
     * Values in {@code other} win on conflict.
     *
     * @param other the overriding context
     * @return merged context
     */
    @NotNull
    public TemplateContext mergedWith(@NotNull TemplateContext other) {
        Objects.requireNonNull(other, "other must not be null");
        Map<String, String> merged = new LinkedHashMap<>(variables);
        merged.putAll(other.variables);
        return new TemplateContext(merged);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link TemplateContext}.
     */
    public static final class Builder {

        private final Map<String, String> map = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Adds or replaces a single variable binding.
         */
        @NotNull
        public Builder put(@NotNull String key, @NotNull String value) {
            map.put(
                Objects.requireNonNull(key, "key must not be null"),
                Objects.requireNonNull(value, "value must not be null"));
            return this;
        }

        /**
         * Adds all entries from {@code entries}. Existing keys are overwritten.
         */
        @NotNull
        public Builder putAll(@NotNull Map<String, String> entries) {
            map.putAll(Objects.requireNonNull(entries, "entries must not be null"));
            return this;
        }

        /**
         * Builds the immutable {@link TemplateContext}.
         */
        @NotNull
        public TemplateContext build() {
            return new TemplateContext(map);
        }
    }

    @Override
    public String toString() {
        return "TemplateContext{variables=" + variables.keySet() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TemplateContext that)) return false;
        return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
        return variables.hashCode();
    }
}
