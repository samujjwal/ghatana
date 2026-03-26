package com.ghatana.datacloud.config.model;

import java.util.Map;
import java.util.Objects;

/**
 * Compiled object schema for nested object fields.
 *
 * <p>
 * Represents the structure of nested object fields with pre-computed property
 * lookups.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled schema for nested object fields
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledObjectSchema(
        Map<String, CompiledPropertyConfig> properties
        ) {

    /**
     * Creates a CompiledObjectSchema with defensive copy.
     */
    public CompiledObjectSchema {
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    /**
     * Get a property by name.
     *
     * @param name property name
     * @return property config or null if not found
     */
    public CompiledPropertyConfig getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Check if a property exists.
     *
     * @param name property name
     * @return true if property exists
     */
    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    /**
     * Property configuration within a nested object.
     */
    public record CompiledPropertyConfig(
            String name,
            FieldType type,
            String format
    ) {
        

    public CompiledPropertyConfig {
        Objects.requireNonNull(name, "Property name cannot be null");
        Objects.requireNonNull(type, "Property type cannot be null");
    }
}
}
