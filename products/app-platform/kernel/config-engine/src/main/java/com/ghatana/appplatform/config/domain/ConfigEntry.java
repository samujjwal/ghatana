package com.ghatana.appplatform.config.domain;

import java.util.Objects;

/**
 * A single configuration value at a specific hierarchy level.
 *
 * <p>The {@code value} is stored as a JSON string — it may be a JSON scalar
 * (string, number, boolean) or a JSON object for structured config.
 * Callers deserialize using Jackson after retrieval.
 *
 * @param namespace       the config namespace this entry belongs to (e.g. "payments")
 * @param key             the config key within the namespace (e.g. "max_transfer_limit")
 * @param value           JSON-encoded value string
 * @param level           which hierarchy level this entry is scoped to
 * @param levelId         the identifier for the scope (e.g. tenantId, userId, jurisdiction code)
 * @param schemaNamespace the namespace of the schema this entry's value must validate against
 *
 * @doc.type record
 * @doc.purpose A config value scoped to a specific hierarchy level and identity
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConfigEntry(
    String namespace,
    String key,
    String value,
    ConfigHierarchyLevel level,
    String levelId,
    String schemaNamespace
) {
    public ConfigEntry {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(levelId, "levelId");
        Objects.requireNonNull(schemaNamespace, "schemaNamespace");
        if (namespace.isBlank()) throw new IllegalArgumentException("namespace must not be blank");
        if (key.isBlank())       throw new IllegalArgumentException("key must not be blank");
    }
}
