package com.ghatana.pattern.codegen.model;

/**
 * Logical grouping for generated fields.
 *
 * @doc.type class
 * @doc.purpose Logical category grouping for code-generated event schema fields
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum FieldCategory {
    HEADER,
    PAYLOAD,
    DERIVED
}
