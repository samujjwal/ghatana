package com.ghatana.datacloud.entity;

/**
 * Enumeration of supported field data types in the collection entity system.
 *
 * <p><b>Purpose</b><br>
 * Defines the canonical set of data types for dynamic schema fields, supporting primitive types,
 * references, arrays, and rich media. Used for field type validation, UI rendering, and SQL
 * column type mapping.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetaField field = MetaField.builder()
 *     .name("price")
 *     .type(DataType.NUMBER)
 *     .label("Price")
 *     .required(true)
 *     .build();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Value type in domain model layer
 * - Used by MetaField for field type validation
 * - Consumed by DynamicQueryBuilder for SQL generation
 * - Used by UI layer for form field rendering
 *
 * <p><b>Thread Safety</b><br>
 * Immutable enum - inherently thread-safe.
 *
 * <p><b>Extensibility</b><br>
 * To add new types:
 * 1. Add enum constant with documentation
 * 2. Update DynamicQueryBuilder SQL mapping
 * 3. Update UI field renderer
 * 4. Add validation rules in ValidationService
 * 5. Update database schema if needed
 *
 * @see MetaField
 * @see com.ghatana.datacloud.application.DynamicQueryBuilder
 * @see com.ghatana.datacloud.application.ValidationService
 * @doc.type enum
 * @doc.purpose Canonical field data types for dynamic schemas
 * @doc.layer product
 * @doc.pattern Value Object (Enum)
 */
public enum DataType {
    /**
     * Text string (VARCHAR/TEXT).
     * SQL: VARCHAR(255) or TEXT
     * Validation: Length constraints
     */
    STRING,

    /**
     * Numeric value (INTEGER/DECIMAL).
     * SQL: INTEGER or DECIMAL(19,2)
     * Validation: Min/max value constraints
     */
    NUMBER,

    /**
     * Boolean flag (BOOLEAN).
     * SQL: BOOLEAN
     * Validation: None (true/false only)
     */
    BOOLEAN,

    /**
     * Date only (DATE).
     * SQL: DATE
     * Validation: Date format, range
     */
    DATE,

    /**
     * Date and time (TIMESTAMP).
     * SQL: TIMESTAMP
     * Validation: DateTime format, range
     */
    DATETIME,

    /**
     * Time only (TIME).
     * SQL: TIME
     * Validation: Time format
     */
    TIME,

    /**
     * Reference to another collection (FOREIGN KEY).
     * SQL: UUID (references other collection's entity)
     * Validation: Referenced entity exists
     */
    REFERENCE,

    /**
     * Array of values (JSONB ARRAY).
     * SQL: JSONB
     * Validation: Array element type, length
     */
    ARRAY,

    /**
     * Embedded object (JSONB OBJECT).
     * SQL: JSONB
     * Validation: JSON Schema validation
     */
    EMBEDDED,

    /**
     * Image URL or binary (TEXT/BYTEA).
     * SQL: TEXT (URL) or BYTEA (binary)
     * Validation: URL format, file size
     */
    IMAGE,

    /**
     * File URL or binary (TEXT/BYTEA).
     * SQL: TEXT (URL) or BYTEA (binary)
     * Validation: URL format, file size, extension
     */
    FILE,

    /**
     * Email address (VARCHAR with validation).
     * SQL: VARCHAR(255)
     * Validation: Email format
     */
    EMAIL,

    /**
     * URL (VARCHAR with validation).
     * SQL: VARCHAR(2048)
     * Validation: URL format
     */
    URL,

    /**
     * Phone number (VARCHAR with validation).
     * SQL: VARCHAR(20)
     * Validation: Phone format
     */
    PHONE,

    /**
     * Rich text/HTML (TEXT).
     * SQL: TEXT
     * Validation: HTML sanitization
     */
    RICHTEXT,

    /**
     * JSON object (JSONB).
     * SQL: JSONB
     * Validation: JSON Schema validation
     */
    JSON,

    /**
     * UUID identifier (UUID).
     * SQL: UUID
     * Validation: UUID format
     */
    UUID,

    /**
     * Enumeration (VARCHAR with constraints).
     * SQL: VARCHAR(255)
     * Validation: Value in allowed set
     */
    ENUM,

    /**
     * Geographic coordinates (POINT/GEOMETRY).
     * SQL: POINT or GEOMETRY
     * Validation: Latitude/longitude range
     */
    GEOLOCATION,

    /**
     * Color value (VARCHAR hex code).
     * SQL: VARCHAR(7)
     * Validation: Hex color format
     */
    COLOR,

    /**
     * Currency amount (DECIMAL with currency code).
     * SQL: DECIMAL(19,2)
     * Validation: Positive amount, currency code
     */
    CURRENCY,

    /**
     * Percentage (DECIMAL 0-100).
     * SQL: DECIMAL(5,2)
     * Validation: 0-100 range
     */
    PERCENTAGE,

    /**
     * Rating (INTEGER 1-5).
     * SQL: INTEGER
     * Validation: 1-5 range
     */
    RATING,

    /**
     * Tags/labels (TEXT ARRAY).
     * SQL: TEXT[]
     * Validation: Array of strings, length
     */
    TAGS,

    /**
     * Markdown text (TEXT).
     * SQL: TEXT
     * Validation: Markdown format
     */
    MARKDOWN
}
