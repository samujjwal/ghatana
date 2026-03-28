package com.ghatana.pattern.codegen.naming;

/**
 * Strategy used to convert schema names into valid Java identifiers.
 *
 * @doc.type interface
 * @doc.purpose Strategy contract for converting schema names to valid Java field identifiers
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface FieldNamingStrategy {
    /**
     * Converts the supplied name into a lowerCamelCase Java field name.
     */
    String toFieldName(String candidate);
}
