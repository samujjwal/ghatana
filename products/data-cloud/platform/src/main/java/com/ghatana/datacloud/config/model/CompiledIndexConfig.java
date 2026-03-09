package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Objects;

/**
 * Compiled index configuration.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled index configuration for runtime access
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledIndexConfig(
        String name,
        List<String> fields,
        boolean unique,
        IndexType type
        ) {

    /**
     * Creates a CompiledIndexConfig with validation.
     */
    public CompiledIndexConfig    {
        Objects.requireNonNull(name, "Index name cannot be null");
        Objects.requireNonNull(fields, "Index fields cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Index name cannot be blank");
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Index must have at least one field");
        }

        fields = List.copyOf(fields);
        type = type != null ? type : IndexType.BTREE;
    }

    /**
     * Check if this is a single-field index.
     *
     * @return true if single-field index
     */
    public boolean isSingleField() {
        return fields.size() == 1;
    }

    /**
     * Check if this is a composite (multi-field) index.
     *
     * @return true if composite index
     */
    public boolean isComposite() {
        return fields.size() > 1;
    }

    /**
     * Index types supported by the platform.
     */
    public enum IndexType {
        /**
         * B-tree index - general purpose
         */
        BTREE,
        /**
         * Hash index - equality lookups only
         */
        HASH,
        /**
         * GIN index - for arrays and full-text
         */
        GIN,
        /**
         * GiST index - for geometric/spatial data
         */
        GIST,
        /**
         * BRIN index - for sorted data
         */
        BRIN
    }
}
