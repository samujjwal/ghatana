package com.ghatana.pattern.codegen.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable descriptor for a generated field.
 */
public final class FieldDefinition {
    private final String originalName;
    private final String javaName;
    private final Class<?> javaType;
    private final boolean required;
    private final FieldCategory category;
    private final String description;

    public FieldDefinition(String originalName,
                           String javaName,
                           Class<?> javaType,
                           boolean required,
                           FieldCategory category,
                           String description) {
        this.originalName = Objects.requireNonNull(originalName, "originalName");
        this.javaName = Objects.requireNonNull(javaName, "javaName");
        this.javaType = Objects.requireNonNull(javaType, "javaType");
        this.category = Objects.requireNonNull(category, "category");
        this.description = description == null ? "" : description;
        this.required = required;
    }

    public String originalName() {
        return originalName;
    }

    public String javaName() {
        return javaName;
    }

    public Class<?> javaType() {
        return javaType;
    }

    public boolean required() {
        return required;
    }

    public FieldCategory category() {
        return category;
    }

    public String description() {
        return description;
    }

    public String getterName() {
        return "get" + capitalize(javaName);
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        if (value.length() == 1) {
            return value.toUpperCase(Locale.ROOT);
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
