package com.ghatana.datacloud.config.model;

/**
 * Enumeration of supported field data types in collection schemas.
 *
 * <p>
 * Field types define the data type and validation rules for collection fields.
 * Each type maps to appropriate storage representations in different backends.
 *
 * @doc.type enum
 * @doc.purpose Define supported field types for collection schemas
 * @doc.layer core
 * @doc.pattern Enumeration
 */
public enum FieldType {

    /**
     * String type - UTF-8 text data.
     */
    STRING("string"),
    /**
     * Integer type - 32-bit signed integer.
     */
    INTEGER("integer"),
    /**
     * Long type - 64-bit signed integer.
     */
    LONG("long"),
    /**
     * Double type - 64-bit floating point.
     */
    DOUBLE("double"),
    /**
     * Boolean type - true/false.
     */
    BOOLEAN("boolean"),
    /**
     * UUID type - 128-bit universally unique identifier.
     */
    UUID("uuid"),
    /**
     * Timestamp type - instant in time with nanosecond precision.
     */
    TIMESTAMP("timestamp"),
    /**
     * Date type - calendar date without time.
     */
    DATE("date"),
    /**
     * Time type - time without date.
     */
    TIME("time"),
    /**
     * Enum type - one of a predefined set of values.
     */
    ENUM("enum"),
    /**
     * Object type - nested structured data.
     */
    OBJECT("object"),
    /**
     * Array type - list of values.
     */
    ARRAY("array"),
    /**
     * Binary type - raw bytes.
     */
    BINARY("binary"),
    /**
     * JSON type - arbitrary JSON data.
     */
    JSON("json"),
    /**
     * Decimal type - arbitrary precision decimal.
     */
    DECIMAL("decimal"),
    /**
     * Duration type - ISO 8601 duration.
     */
    DURATION("duration");

    private final String yamlValue;

    FieldType(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    /**
     * Get the YAML representation of this field type.
     *
     * @return YAML string value
     */
    public String getYamlValue() {
        return yamlValue;
    }

    /**
     * Parse a field type from its YAML string representation.
     *
     * @param value YAML string value
     * @return the corresponding FieldType
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static FieldType fromYaml(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Field type cannot be null");
        }
        String normalized = value.toLowerCase().trim();
        for (FieldType type : values()) {
            if (type.yamlValue.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + value);
    }

    /**
     * Check if this type requires additional schema definition (e.g., object,
     * array).
     *
     * @return true if nested schema is required
     */
    public boolean requiresNestedSchema() {
        return this == OBJECT || this == ARRAY;
    }

    /**
     * Check if this type supports indexing natively.
     *
     * @return true if indexable
     */
    public boolean isIndexable() {
        return switch (this) {
            case STRING, INTEGER, LONG, DOUBLE, BOOLEAN, UUID, TIMESTAMP, DATE, TIME, ENUM, DECIMAL ->
                true;
            case OBJECT, ARRAY, BINARY, JSON, DURATION ->
                false;
        };
    }

    /**
     * Check if this type supports uniqueness constraints.
     *
     * @return true if unique constraint is supported
     */
    public boolean supportsUnique() {
        return switch (this) {
            case STRING, INTEGER, LONG, UUID, DATE, TIME, DECIMAL ->
                true;
            default ->
                false;
        };
    }
}
