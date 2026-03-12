package com.ghatana.platform.schema;

/**
 * Compatibility mode governing schema evolution rules.
 *
 * @doc.type enum
 * @doc.purpose Schema evolution compatibility constraint
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum CompatibilityMode {

    /**
     * New schema can read data written by old schema.
     * Allowed changes: add optional fields, remove required fields.
     * Forbidden changes: remove required fields that have no default.
     */
    BACKWARD,

    /**
     * Old schema can read data written by new schema.
     * Allowed changes: add required fields (old consumers ignore unknown fields).
     * Forbidden changes: remove required fields present in old schema.
     */
    FORWARD,

    /**
     * Both BACKWARD and FORWARD compatibility must hold.
     * Most restrictive: only type-preserving, additive optional changes permitted.
     */
    FULL
}
