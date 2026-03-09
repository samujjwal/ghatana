package com.ghatana.pattern.codegen.naming;

/**
 * Strategy used to convert schema names into valid Java identifiers.
 */
public interface FieldNamingStrategy {
    /**
     * Converts the supplied name into a lowerCamelCase Java field name.
     */
    String toFieldName(String candidate);
}
