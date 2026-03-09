package com.ghatana.datacloud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Field definition within a collection schema.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the schema for a single field in a collection, including:
 * <ul>
 * <li>Data type (STRING, INTEGER, BOOLEAN, etc.)</li>
 * <li>Validation constraints (required, min/max, pattern)</li>
 * <li>Display hints (label, description)</li>
 * <li>Storage hints (indexed, unique)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * FieldDefinition emailField = FieldDefinition.builder()
 *         .name("email")
 *         .label("Email Address")
 *         .type(FieldType.STRING)
 *         .required(true)
 *         .unique(true)
 *         .pattern("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$")
 *         .build();
 *
 * // Or using factory methods
 * FieldDefinition.string("name").required().label("Full Name");
 * FieldDefinition.integer("age").min(0).max(150);
 * FieldDefinition.decimal("amount").required().min(0);
 * FieldDefinition.boolean_("active").defaultValue(true);
 * }</pre>
 *
 * @see Collection
 * @see FieldType
 * @doc.type class
 * @doc.purpose Field schema definition
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Field name (must be valid identifier).
     */
    private String name;

    /**
     * Human-readable label.
     */
    private String label;

    /**
     * Field description.
     */
    private String description;

    /**
     * Data type.
     */
    @Builder.Default
    private FieldType type = FieldType.STRING;

    /**
     * Whether field is required.
     */
    @Builder.Default
    private Boolean required = false;

    /**
     * Whether field must be unique within collection.
     */
    @Builder.Default
    private Boolean unique = false;

    /**
     * Whether field should be indexed for queries.
     */
    @Builder.Default
    private Boolean indexed = false;

    /**
     * Default value if not provided.
     */
    private Object defaultValue;

    /**
     * Minimum value (for numeric types) or length (for strings).
     */
    private Number min;

    /**
     * Maximum value (for numeric types) or length (for strings).
     */
    private Number max;

    /**
     * Regex pattern for validation (STRING type).
     */
    private String pattern;

    /**
     * Allowed values (for ENUM-like fields).
     */
    private List<Object> allowedValues;

    /**
     * Nested field definitions (for OBJECT type).
     */
    private List<FieldDefinition> nestedFields;

    /**
     * Element type (for ARRAY type).
     */
    private FieldType elementType;

    /**
     * Reference to another collection (for REFERENCE type).
     */
    private String referenceCollection;

    /**
     * Additional type-specific configuration.
     */
    private Map<String, Object> config;

    // ==================== Field Types ====================
    /**
     * Supported field data types.
     */
    public enum FieldType {
        STRING,
        INTEGER,
        LONG,
        DECIMAL,
        BOOLEAN,
        DATE,
        DATETIME,
        TIMESTAMP,
        UUID,
        OBJECT,
        ARRAY,
        REFERENCE,
        JSON,
        BINARY
    }

    // ==================== Factory Methods ====================
//    public static FieldDefinitionBuilder string(String name) {
//        return builder().name(name).type(FieldType.STRING);
//    }
//
//    public static FieldDefinitionBuilder integer(String name) {
//        return builder().name(name).type(FieldType.INTEGER);
//    }
//
//    public static FieldDefinitionBuilder long_(String name) {
//        return builder().name(name).type(FieldType.LONG);
//    }
//
//    public static FieldDefinitionBuilder decimal(String name) {
//        return builder().name(name).type(FieldType.DECIMAL);
//    }
//
//    public static FieldDefinitionBuilder boolean_(String name) {
//        return builder().name(name).type(FieldType.BOOLEAN);
//    }
//
//    public static FieldDefinitionBuilder date(String name) {
//        return builder().name(name).type(FieldType.DATE);
//    }
//
//    public static FieldDefinitionBuilder datetime(String name) {
//        return builder().name(name).type(FieldType.DATETIME);
//    }
//
//    public static FieldDefinitionBuilder timestamp(String name) {
//        return builder().name(name).type(FieldType.TIMESTAMP);
//    }
//
//    public static FieldDefinitionBuilder uuid(String name) {
//        return builder().name(name).type(FieldType.UUID);
//    }
//
//    public static FieldDefinitionBuilder object(String name) {
//        return builder().name(name).type(FieldType.OBJECT);
//    }
//
//    public static FieldDefinitionBuilder array(String name, FieldType elementType) {
//        return builder().name(name).type(FieldType.ARRAY).elementType(elementType);
//    }
//
//    public static FieldDefinitionBuilder reference(String name, String collectionName) {
//        return builder().name(name).type(FieldType.REFERENCE).referenceCollection(collectionName);
//    }
//
//    public static FieldDefinitionBuilder json(String name) {
//        return builder().name(name).type(FieldType.JSON);
//    }

    // ==================== Fluent Setters ====================
    public FieldDefinition required() {
        this.required = true;
        return this;
    }

    public FieldDefinition optional() {
        this.required = false;
        return this;
    }

    public FieldDefinition unique() {
        this.unique = true;
        return this;
    }

    public FieldDefinition indexed() {
        this.indexed = true;
        return this;
    }

    public FieldDefinition label(String label) {
        this.label = label;
        return this;
    }

    public FieldDefinition description(String description) {
        this.description = description;
        return this;
    }

    public FieldDefinition defaultValue(Object value) {
        this.defaultValue = value;
        return this;
    }

    public FieldDefinition min(Number min) {
        this.min = min;
        return this;
    }

    public FieldDefinition max(Number max) {
        this.max = max;
        return this;
    }

    public FieldDefinition pattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public FieldDefinition allowedValues(List<Object> values) {
        this.allowedValues = values;
        return this;
    }
}
