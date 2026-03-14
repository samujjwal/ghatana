package com.ghatana.appplatform.config.domain;

import java.util.Objects;

/**
 * A fully resolved config value, annotated with the level it was resolved from.
 *
 * <p>Produced by {@link com.ghatana.appplatform.config.merge.ConfigMerger} and
 * returned from {@link com.ghatana.appplatform.config.port.ConfigStore#resolve}.
 *
 * @param key                 the config key
 * @param value               JSON-encoded resolved value string
 * @param resolvedFromLevel   the hierarchy level that provided this value
 * @param resolvedFromLevelId the level ID (e.g. tenantId) that provided this value
 *
 * @doc.type record
 * @doc.purpose A resolved config value with provenance information
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConfigValue(
    String key,
    String value,
    ConfigHierarchyLevel resolvedFromLevel,
    String resolvedFromLevelId
) {
    public ConfigValue {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(resolvedFromLevel, "resolvedFromLevel");
        Objects.requireNonNull(resolvedFromLevelId, "resolvedFromLevelId");
    }
}
