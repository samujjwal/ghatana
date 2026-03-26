package com.ghatana.datacloud.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.With;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-safe field validation configuration.
 *
 * <p>Replaces {@code Map<String, Object>} for field validation rules,
 * providing compile-time type safety and better IDE support.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FieldValidation validation = FieldValidation.builder()
 *     .min(0.0)
 *     .max(999999.0)
 *     .required(true)
 *     .build();
 * }</pre>
 *
 * @see MetaField
 * @doc.type record
 * @doc.purpose Type-safe field validation configuration
 * @doc.layer domain
 * @doc.pattern Value Object
 */
@Builder
@With
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldValidation(
        // Numeric constraints
        Double min,
        Double max,
        
        // String constraints
        Integer minLength,
        Integer maxLength,
        String pattern,
        
        // Enum constraint
        List<String> enumValues,
        
        // Common constraints
        Boolean required,
        
        // Date constraints (ISO-8601 format strings)
        String minDate,
        String maxDate,
        
        // Custom validation message
        String message,
        
        // Reference validation
        String referenceCollection,
        String referenceField
) {
    
    /**
     * Creates an empty validation (no constraints).
     *
     * @return empty validation
     */
    public static FieldValidation empty() {
        return FieldValidation.builder().build();
    }
    
    /**
     * Creates validation for a required field.
     *
     * @return validation with required=true
     */
    public static FieldValidation requiredField() {
        return FieldValidation.builder().required(true).build();
    }
    
    /**
     * Creates validation with numeric range.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return validation with range constraint
     */
    public static FieldValidation range(double min, double max) {
        return FieldValidation.builder().min(min).max(max).build();
    }
    
    /**
     * Creates validation with string length constraints.
     *
     * @param minLength minimum length
     * @param maxLength maximum length
     * @return validation with length constraint
     */
    public static FieldValidation length(int minLength, int maxLength) {
        return FieldValidation.builder().minLength(minLength).maxLength(maxLength).build();
    }
    
    /**
     * Creates validation with regex pattern.
     *
     * @param pattern regex pattern
     * @return validation with pattern constraint
     */
    public static FieldValidation pattern(String pattern) {
        return FieldValidation.builder().pattern(pattern).build();
    }
    
    /**
     * Creates validation with enum values.
     *
     * @param values allowed values
     * @return validation with enum constraint
     */
    public static FieldValidation enumOf(List<String> values) {
        return FieldValidation.builder().enumValues(values).build();
    }
    
    /**
     * Creates a FieldValidation from a legacy Map.
     *
     * @param map the validation map
     * @return type-safe FieldValidation
     */
    @SuppressWarnings("unchecked")
    public static FieldValidation fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }
        
        return FieldValidation.builder()
                .min(getAsDouble(map.get("min")))
                .max(getAsDouble(map.get("max")))
                .minLength(getAsInteger(map.get("minLength")))
                .maxLength(getAsInteger(map.get("maxLength")))
                .pattern((String) map.get("pattern"))
                .enumValues(map.get("enum") instanceof List ? (List<String>) map.get("enum") : null)
                .required(getAsBoolean(map.get("required")))
                .minDate((String) map.get("minDate"))
                .maxDate((String) map.get("maxDate"))
                .message((String) map.get("message"))
                .referenceCollection((String) map.get("referenceCollection"))
                .referenceField((String) map.get("referenceField"))
                .build();
    }
    
    /**
     * Converts this validation to a Map for JSONB storage.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (min != null) map.put("min", min);
        if (max != null) map.put("max", max);
        if (minLength != null) map.put("minLength", minLength);
        if (maxLength != null) map.put("maxLength", maxLength);
        if (pattern != null) map.put("pattern", pattern);
        if (enumValues != null) map.put("enum", enumValues);
        if (required != null) map.put("required", required);
        if (minDate != null) map.put("minDate", minDate);
        if (maxDate != null) map.put("maxDate", maxDate);
        if (message != null) map.put("message", message);
        if (referenceCollection != null) map.put("referenceCollection", referenceCollection);
        if (referenceField != null) map.put("referenceField", referenceField);
        return map;
    }
    
    private static Double getAsDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private static Integer getAsInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private static Boolean getAsBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return null;
    }
}
