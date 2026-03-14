package com.ghatana.appplatform.config.bundle;

import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;

import java.util.Objects;

/**
 * A single config entry captured inside an air-gap bundle.
 *
 * <p>Mirrors {@link com.ghatana.appplatform.config.domain.ConfigEntry} but is
 * a standalone record to decouple the bundle format from the live domain model.
 *
 * @param namespace       config namespace (e.g. "payments")
 * @param key             config key within the namespace
 * @param value           JSON-encoded value string
 * @param level           hierarchy level this entry is scoped to
 * @param levelId         scope identifier (e.g. tenantId, jurisdiction code)
 * @param schemaNamespace namespace of the validating schema
 *
 * @doc.type record
 * @doc.purpose Portable config entry snapshot for air-gap bundle export (K02-012)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConfigBundleEntry(
    String namespace,
    String key,
    String value,
    ConfigHierarchyLevel level,
    String levelId,
    String schemaNamespace
) {
    public ConfigBundleEntry {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(levelId, "levelId");
        Objects.requireNonNull(schemaNamespace, "schemaNamespace");
    }
}
