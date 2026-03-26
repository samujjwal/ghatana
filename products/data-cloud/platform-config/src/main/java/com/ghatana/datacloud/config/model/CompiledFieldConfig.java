package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiled, immutable field configuration with pre-computed lookups.
 *
 * <p>
 * This is the runtime representation of a field definition after validation and
 * compilation. All fields are immutable and thread-safe.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled field configuration for runtime access
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledFieldConfig(
        String name,
        FieldType type,
        String format,
        boolean required,
        boolean unique,
        boolean indexed,
        boolean pii,
        boolean auto,
        boolean immutable,
        Object defaultValue,
        Integer minLength,
        Integer maxLength,
        Integer maxItems,
        List<String> enumValues,
        CompiledObjectSchema nestedSchema,
        FieldType arrayItemType,
        String description
        ) {

    /**
     * Creates a CompiledFieldConfig with validation.
     */
    public CompiledFieldConfig                 {
        Objects.requireNonNull(name, "Field name cannot be null");
        Objects.requireNonNull(type, "Field type cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Field name cannot be blank");
        }

        // Make defensive copies of mutable collections
        enumValues = enumValues != null ? List.copyOf(enumValues) : List.of();

        // Validate enum fields have values
        if (type == FieldType.ENUM && enumValues.isEmpty()) {
            throw new IllegalArgumentException("Enum field '" + name + "' must have values defined");
        }

        // Validate array fields have item type
        if (type == FieldType.ARRAY && arrayItemType == null) {
            throw new IllegalArgumentException("Array field '" + name + "' must have item type defined");
        }
    }

    /**
     * Check if this field is an event model required field.
     *
     * @return true if this field is a standard event model field
     */
    public boolean isEventModelField() {
        return switch (name) {
            case "eventId", "eventType", "aggregateId", "aggregateVersion", "causationId", "correlationId", "timestamp", "tenantId", "payload", "metadata" ->
                true;
            default ->
                false;
        };
    }

    /**
     * Check if this field can be used as a partition key.
     *
     * @return true if suitable for partitioning
     */
    public boolean canBePartitionKey() {
        return indexed && type.isIndexable() && !type.requiresNestedSchema();
    }

    /**
     * Check if this field can be used as a sort key.
     *
     * @return true if suitable for sorting
     */
    public boolean canBeSortKey() {
        return type.isIndexable() && !type.requiresNestedSchema();
    }

    /**
     * Builder for CompiledFieldConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder pattern for CompiledFieldConfig.
     */
    public static class Builder {

        private String name;
        private FieldType type;
        private String format;
        private boolean required;
        private boolean unique;
        private boolean indexed;
        private boolean pii;
        private boolean auto;
        private boolean immutable;
        private Object defaultValue;
        private Integer minLength;
        private Integer maxLength;
        private Integer maxItems;
        private List<String> enumValues;
        private CompiledObjectSchema nestedSchema;
        private FieldType arrayItemType;
        private String description;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(FieldType type) {
            this.type = type;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder unique(boolean unique) {
            this.unique = unique;
            return this;
        }

        public Builder indexed(boolean indexed) {
            this.indexed = indexed;
            return this;
        }

        public Builder pii(boolean pii) {
            this.pii = pii;
            return this;
        }

        public Builder auto(boolean auto) {
            this.auto = auto;
            return this;
        }

        public Builder immutable(boolean immutable) {
            this.immutable = immutable;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder minLength(Integer minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder maxItems(Integer maxItems) {
            this.maxItems = maxItems;
            return this;
        }

        public Builder enumValues(List<String> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public Builder nestedSchema(CompiledObjectSchema nestedSchema) {
            this.nestedSchema = nestedSchema;
            return this;
        }

        public Builder arrayItemType(FieldType arrayItemType) {
            this.arrayItemType = arrayItemType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public CompiledFieldConfig build() {
            return new CompiledFieldConfig(
                    name, type, format, required, unique, indexed, pii,
                    auto, immutable, defaultValue, minLength, maxLength,
                    maxItems, enumValues, nestedSchema, arrayItemType, description
            );
        }
    }
}
