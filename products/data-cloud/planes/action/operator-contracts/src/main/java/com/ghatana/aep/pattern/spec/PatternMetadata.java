package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Typed model for PatternSpec metadata section.
 *
 * @doc.type record
 * @doc.purpose Typed representation of pattern metadata
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternMetadata(
        String name,
        String namespace,
        String version,
        String tenantId,
        String owner,
        String description,
        Map<String, Object> labels,
        Map<String, Object> annotations) {

    public PatternMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(version, "version");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("name", name);
        map.put("namespace", namespace);
        map.put("version", version);
        if (tenantId != null) map.put("tenantId", tenantId);
        if (owner != null) map.put("owner", owner);
        map.put("description", description != null ? description : "");
        map.put("labels", labels != null ? labels : Map.of());
        map.put("annotations", annotations != null ? annotations : Map.of());
        return map;
    }
}
