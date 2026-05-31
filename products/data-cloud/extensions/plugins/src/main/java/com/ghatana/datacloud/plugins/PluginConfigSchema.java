/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

import java.util.Map;
import java.util.Set;

/**
 * Plugin configuration schema (P8).
 *
 * <p>Defines the expected configuration structure for a plugin,
 * including required fields, types, and validation rules.
 *
 * @doc.type record
 * @doc.purpose Plugin configuration schema for validation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginConfigSchema(
        Map<String, ConfigField> fields,
        Set<String> requiredFields,
        boolean strictMode
) {
    public PluginConfigSchema {
        if (fields == null) {
            fields = Map.of();
        }
        if (requiredFields == null) {
            requiredFields = Set.of();
        }
    }

    /**
     * Returns an empty config schema.
     */
    public static PluginConfigSchema empty() {
        return new PluginConfigSchema(Map.of(), Set.of(), false);
    }

    /**
     * Validates a configuration map against this schema.
     */
    public ValidationResult validate(Map<String, Object> config) {
        if (config == null) {
            return new ValidationResult(false, "Configuration is null", Map.of());
        }

        Map<String, String> errors = Map.of();

        // Check required fields
        for (String requiredField : requiredFields) {
            if (!config.containsKey(requiredField)) {
                errors = Map.of(requiredField, "Required field is missing");
            }
        }

        // Check field types if strict mode is enabled
        if (strictMode) {
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                ConfigField fieldDef = fields.get(fieldName);
                
                if (fieldDef != null && value != null) {
                    if (!fieldDef.type().isInstance(value)) {
                        errors = Map.of(fieldName, "Expected type: " + fieldDef.type().getSimpleName());
                    }
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors.isEmpty() ? "Valid" : "Validation failed", errors);
    }

    /**
     * Configuration field definition.
     */
    public record ConfigField(
            String name,
            Class<?> type,
            String description,
            boolean required,
            Object defaultValue,
            Map<String, Object> constraints
    ) {
        public ConfigField {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (type == null) {
                type = String.class;
            }
            if (constraints == null) {
                constraints = Map.of();
            }
        }
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
            boolean valid,
            String message,
            Map<String, String> errors
    ) {}
}
