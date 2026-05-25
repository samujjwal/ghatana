package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Typed model for PatternSpec.
 *
 * @doc.type record
 * @doc.purpose Typed representation of a pattern specification
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternSpec(
        String apiVersion,
        String kind,
        PatternMetadata metadata,
        PatternSemantics semantics,
        PatternExpression pattern,
        PatternEmit emit,
        PatternLifecycle lifecycle,
        PatternGovernance governance,
        PatternObservability observability) {

    public PatternSpec {
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(semantics, "semantics");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(emit, "emit");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(governance, "governance");
        Objects.requireNonNull(observability, "observability");
    }

    /**
     * Converts this typed PatternSpec to a map representation for serialization.
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "apiVersion", apiVersion,
            "kind", kind,
            "metadata", metadata.toMap(),
            "semantics", semantics.toMap(),
            "pattern", pattern.toMap(),
            "emit", emit.toMap(),
            "lifecycle", lifecycle.toMap(),
            "governance", governance.toMap(),
            "observability", observability.toMap());
    }
}
