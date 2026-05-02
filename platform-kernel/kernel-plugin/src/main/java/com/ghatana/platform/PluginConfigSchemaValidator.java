/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates plugin-supplied configuration against the JSON Schema property graph
 * declared in {@link PluginManifest#getConfigSchema()}.
 *
 * <h3>Schema format</h3>
 * <p>The schema is a flat property graph where each key is a config property
 * name and the value is a descriptor map. Supported descriptor keys:</p>
 * <ul>
 *   <li>{@code type} (String) — {@code "string"}, {@code "number"}, {@code "boolean"}</li>
 *   <li>{@code required} (Boolean) — whether the property must be present</li>
 *   <li>{@code secret} (Boolean) — if {@code true}, the value must NOT be a literal;
 *       it must be an env reference (e.g. {@code "env:MY_SECRET"})</li>
 * </ul>
 *
 * <h3>Secret-handling constraints</h3>
 * <p>Any config property marked {@code secret=true} in the schema must carry a
 * reference value starting with {@code "env:"}, {@code "vault:"}, or
 * {@code "k8s-secret:"}. Literal secret values are rejected with an actionable
 * error message pointing to the offending key.</p>
 *
 * @doc.type class
 * @doc.purpose Validates plugin config against the schema declared in PluginManifest at install time
 * @doc.layer platform
 * @doc.pattern Validator
 * @since 1.2.0
 */
public final class PluginConfigSchemaValidator {

    private static final List<String> ALLOWED_SECRET_PREFIXES = List.of("env:", "vault:", "k8s-secret:");

    /**
     * Validates the supplied {@code config} map against {@code schema}.
     *
     * @param pluginId the plugin identifier (used in error messages)
     * @param schema   the JSON Schema property graph from {@link PluginManifest#getConfigSchema()}
     * @param config   the plugin configuration supplied at install time
     * @throws PluginConfigSchemaViolationException if validation fails
     */
    public void validate(String pluginId, Map<String, Object> schema, Map<String, Object> config) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(config, "config");

        if (schema.isEmpty()) {
            return; // plugin opted out of schema validation
        }

        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            Object descriptorRaw = entry.getValue();
            if (!(descriptorRaw instanceof Map<?, ?> descriptor)) {
                continue; // malformed schema entry; skip
            }

            boolean required = Boolean.TRUE.equals(descriptor.get("required"));
            boolean secret = Boolean.TRUE.equals(descriptor.get("secret"));
            boolean present = config.containsKey(key);

            if (required && !present) {
                errors.add("Missing required config property '" + key + "' for plugin '" + pluginId + "'.");
                continue;
            }

            if (!present) {
                continue; // optional and absent
            }

            Object value = config.get(key);

            if (secret) {
                validateSecretProperty(pluginId, key, value, errors);
            }

            // Type validation
            String expectedType = (String) descriptor.get("type");
            if (expectedType != null && value != null) {
                validateType(pluginId, key, value, expectedType, errors);
            }
        }

        if (!errors.isEmpty()) {
            throw new PluginConfigSchemaViolationException(pluginId, errors);
        }
    }

    private void validateSecretProperty(String pluginId, String key, Object value, List<String> errors) {
        if (!(value instanceof String strValue)) {
            errors.add(
                "Config property '" + key + "' for plugin '" + pluginId + "' is marked as secret " +
                "but its value is not a string reference. Use one of: " + ALLOWED_SECRET_PREFIXES + ".");
            return;
        }
        boolean isReference = ALLOWED_SECRET_PREFIXES.stream().anyMatch(strValue::startsWith);
        if (!isReference) {
            errors.add(
                "Config property '" + key + "' for plugin '" + pluginId + "' is marked as secret " +
                "but contains a literal value. Replace with a reference (e.g. 'env:MY_SECRET', " +
                "'vault:secret/my-plugin/key', 'k8s-secret:my-namespace/my-secret/key').");
        }
    }

    private void validateType(String pluginId, String key, Object value, String expectedType, List<String> errors) {
        boolean typeOk = switch (expectedType) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            default -> true; // unknown type: skip type check
        };
        if (!typeOk) {
            errors.add(
                "Config property '" + key + "' for plugin '" + pluginId + "' must be of type '" +
                expectedType + "' but got '" + value.getClass().getSimpleName() + "'.");
        }
    }

    /**
     * Exception thrown when plugin config violates the declared schema.
     */
    public static final class PluginConfigSchemaViolationException extends RuntimeException {

        private final String pluginId;
        private final List<String> violations;

        public PluginConfigSchemaViolationException(String pluginId, List<String> violations) {
            super("Plugin config schema violations for '" + pluginId + "': " + violations);
            this.pluginId = pluginId;
            this.violations = List.copyOf(violations);
        }

        /**
         * Returns the plugin ID that failed validation.
         *
         * @return the plugin ID
         */
        public String getPluginId() {
            return pluginId;
        }

        /**
         * Returns the list of violation messages.
         *
         * @return immutable list of human-readable violation messages
         */
        public List<String> getViolations() {
            return violations;
        }
    }
}
