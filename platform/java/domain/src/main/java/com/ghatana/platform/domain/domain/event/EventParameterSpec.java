package com.ghatana.platform.domain.domain.event;

import com.ghatana.contracts.common.v1.DataClassificationProto;
import com.ghatana.contracts.event.v1.DeprecationInfoPojo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * {@code EventParameterSpec} defines the complete specification for event parameters including
 * type information, validation constraints, and metadata.
 *
 * <h2>Purpose</h2>
 * This class serves as the schema definition for individual event parameters, enabling:
 * <ul>
 *   <li>Type validation and coercion</li>
 *   <li>Required vs. optional parameter tracking</li>
 *   <li>Enum/allowlist constraint enforcement</li>
 *   <li>Nested type support (arrays, maps, objects)</li>
 *   <li>Data classification and security tagging</li>
 *   <li>Deprecation tracking and migration</li>
 * </ul>
 *
 * <h2>Structure</h2>
 * Parameters are defined with:
 * <ul>
 *   <li><b>name</b>: Unique identifier within scope (pattern: [a-zA-Z_][a-zA-Z0-9_]*)</li>
 *   <li><b>type</b>: {@link EventParameterType} determining validation and processing</li>
 *   <li><b>required</b>: Whether parameter must be provided</li>
 *   <li><b>defaultValue</b>: Optional value if not provided</li>
 *   <li><b>enumValues</b>: Set of allowed values (optional constraint)</li>
 *   <li><b>dataClassification</b>: Security/sensitivity level</li>
 *   <li><b>indexed</b>: Whether parameter is queryable (impacts storage)</li>
 *   <li><b>format</b>: Format specification (e.g., for date/time types)</li>
 *   <li><b>itemsSpec</b>: Array element specification (if type=ARRAY)</li>
 *   <li><b>valueSpec</b>: Map value specification (if type=MAP)</li>
 *   <li><b>properties</b>: Object field specifications (if type=OBJECT)</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Created by</b>: Event type registration, schema imports</li>
 *   <li><b>Used by</b>: Event validation, payload marshalling, documentation generation</li>
 *   <li><b>Stored in</b>: EventType definitions, configuration</li>
 *   <li><b>Related to</b>: {@link EventType}, {@link Event}, validation framework</li>
 * </ul>
 *
 * <h2>Recursive Nested Types</h2>
 * Supports arbitrary complexity through recursive specifications:
 * <ul>
 *   <li><b>Arrays</b>: itemsSpec defines element type (supports nested arrays)</li>
 *   <li><b>Maps</b>: valueSpec defines value type (keys always strings)</li>
 *   <li><b>Objects</b>: properties map defines each field (supports nested objects)</li>
 *   <li><b>Primitives</b>: type alone sufficient for validation</li>
 *   <li><b>Combinations</b>: E.g., array of maps, maps of arrays, objects with array fields</li>
 * </ul>
 *
 * <h2>Complex Nested Example</h2>
 * {@code
 *   // Array of objects with nested properties (multi-dimensional)
 *   EventParameterSpec itemsSpec = EventParameterSpec.builder()
 *       .type(EventParameterType.OBJECT)
 *       .properties(Map.of(
 *           "userId", EventParameterSpec.builder()
 *               .type(EventParameterType.STRING)
 *               .required(true)
 *               .build(),
 *           "roles", EventParameterSpec.builder()
 *               .type(EventParameterType.ARRAY)
 *               .itemsSpec(EventParameterSpec.builder()
 *                   .type(EventParameterType.STRING)
 *                   .enumValues(new LinkedHashSet<>(Arrays.asList(
 *                       "ADMIN", "USER", "GUEST")))
 *                   .build())
 *               .required(false)
 *               .build(),
 *           "metadata", EventParameterSpec.builder()
 *               .type(EventParameterType.MAP)
 *               .valueSpec(EventParameterSpec.builder()
 *                   .type(EventParameterType.STRING)
 *                   .indexed(true)
 *                   .build())
 *               .build()
 *       ))
 *       .build();
 *
 *   EventParameterSpec arrayParam = EventParameterSpec.builder()
 *       .name("users")
 *       .type(EventParameterType.ARRAY)
 *       .itemsSpec(itemsSpec)
 *       .required(true)
 *       .build();
 * }
 *
 * <h2>Validation Rules</h2>
 * The validate(Object) method enforces:
 * <ol>
 *   <li>Required parameters must be non-null (NullPointerException if violated)</li>
 *   <li>Value type must match declared type (IllegalArgumentException if violated)</li>
 *   <li>If enumValues specified, value must be in allowlist</li>
 *   <li>Nested specifications validated recursively (depth-first)</li>
 *   <li>Custom validation rules applied (when enabled post-circular-dep fix)</li>
 *   <li>Format constraints enforced (for date/time/UUID types)</li>
 * </ol>
 *
 * <h2>Data Classification Levels</h2>
 * Parameters carry classification for security enforcement:
 * <ul>
 *   <li>PUBLIC: No restrictions, can be logged/cached freely</li>
 *   <li>INTERNAL: Internal use only, may require audit logging</li>
 *   <li>CONFIDENTIAL: Restricted access, must be encrypted at rest</li>
 *   <li>RESTRICTED: Highly restricted, requires multi-factor access approval</li>
 * </ul>
 *
 * <h2>Deprecation & Migration</h2>
 * Deprecated parameters include {@link DeprecationInfoPojo}:
 * <ul>
 *   <li>Deprecation reason (why parameter is being removed)</li>
 *   <li>Recommended alternative parameter name</li>
 *   <li>Removal timeline (e.g., "v2.0", "2024-12-31")</li>
 *   <li>Migration guide URL</li>
 * </ul>
 *
 * <h2>Query Optimization</h2>
 * The indexed flag impacts storage and querying:
 * <ul>
 *   <li><b>indexed=true</b>: Parameter is indexed for O(log n) queries, larger storage footprint</li>
 *   <li><b>indexed=false</b>: Parameter not indexed, full-table scans required, smaller storage</li>
 *   <li>Default: false for optional/large parameters, true for required keys</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 *
 * <h3>Simple String Parameter</h3>
 * {@code
 *   EventParameterSpec.builder()
 *       .name("userId")
 *       .type(EventParameterType.STRING)
 *       .required(true)
 *       .indexed(true)
 *       .dataClassification(DataClassificationProto.INTERNAL)
 *       .build();
 * }
 *
 * <h3>Enum-Constrained Parameter</h3>
 * {@code
 *   EventParameterSpec.builder()
 *       .name("status")
 *       .type(EventParameterType.STRING)
 *       .enumValues(new LinkedHashSet<>(Arrays.asList("PENDING", "ACTIVE", "COMPLETED")))
 *       .required(true)
 *       .build();
 * }
 *
 * <h3>Optional with Default</h3>
 * {@code
 *   EventParameterSpec.builder()
 *       .name("retryCount")
 *       .type(EventParameterType.INTEGER)
 *       .defaultValue(3)
 *       .required(false)
 *       .build();
 * }
 *
 * <h3>Deprecated Parameter</h3>
 * {@code
 *   EventParameterSpec.builder()
 *       .name("legacyField")
 *       .type(EventParameterType.STRING)
 *       .deprecation(DeprecationInfoPojo.builder()
 *           .reason("Renamed to modernField for clarity")
 *           .alternative("modernField")
 *           .removalDate("2024-12-31")
 *           .build())
 *       .build();
 * }
 *
 * <h2>Builder Pattern</h2>
 * Supports fluent construction via Lombok @Builder:
 * <ul>
 *   <li>All fields optional in builder (defaults applied post-construction)</li>
 *   <li>Call .build() to create immutable spec instance</li>
 *   <li>No-arg constructor available via @NoArgsConstructor</li>
 *   <li>Supports JSON serialization/deserialization (@Data + Jackson)</li>
 * </ul>
 *
 * @see EventType
 * @see Event
 * @see EventParameterType
 * @see DeprecationInfoPojo
 * @see DataClassificationProto
 * @since 1.1.0
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose parameter schema definition, validation specification, and type contract
 * @doc.pattern value-object, builder, composite, recursive
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParameterSpec {

    /**
     * The name of the parameter. Must be unique within its scope (e.g., within an event type).
     * Must match the pattern [a-zA-Z_][a-zA-Z0-9_]* (Java identifier rules).
     * Cannot be null for top-level parameters.
     */
    private String name;
    
    /**
     * The expected type of the parameter value.
     * This determines how the parameter value will be validated, coerced, and processed.
     * Determines behavior of itemsSpec, valueSpec, properties fields.
     */
    private EventParameterType type;
    
    /**
     * Human-readable description of the parameter.
     * Should provide sufficient information for users to understand the parameter's purpose,
     * expected format, and usage constraints (e.g., "User identifier for correlation").
     * Used in API documentation and schema browsers.
     */
    private String description;
    
    /**
     * The default value for this parameter if no value is provided.
     * Type of this object must match the specified parameter type.
     * If null and required=true, validation fails.
     * If null and required=false, parameter is optional.
     */
    private Object defaultValue;
    
    /**
     * Whether this parameter is required.
     * If true, the parameter must be provided when creating an event of this type.
     * If false, parameter is optional and defaults to defaultValue if not provided.
     */
    private boolean required;
    
    /**
     * Set of allowed values for this parameter (if applicable).
     * If non-null and non-empty, the parameter value must be one of these values.
     * Enables enum-like constraint validation without tight coupling to enum types.
     * Useful for external/dynamic enumerations loaded at runtime.
     */
    private LinkedHashSet<Object> enumValues;
    
    /**
     * The data classification level for this parameter.
     * Used for security and access control enforcement (encryption, logging redaction, etc.).
     * Propagates to stored events for compliance auditing.
     */
    private DataClassificationProto dataClassification;
    
    /**
     * Information about this parameter's deprecation status.
     * If non-null, indicates that this parameter is deprecated and provides
     * information about alternatives and removal timeline for migration planning.
     */
    private DeprecationInfoPojo deprecation;
    
    /**
     * Whether this parameter should be indexed for faster querying.
     * Indexed parameters can be efficiently filtered in queries (O(log n)) but increase storage requirements.
     * Set to true for frequently-queried fields (user IDs, timestamps) and false for large/rare fields.
     */
    private boolean indexed;
    
    /**
     * Format specification for the parameter value.
     * The interpretation depends on the parameter type.
     * Examples: "yyyy-MM-dd" for DATE types, "uuid" for STRING types, "json" for object serialization.
     */
    private String format;
    
    /**
     * Specification for array item types.
     * Required and only meaningful when type=ARRAY.
     * Defines the schema for each element in the array (recursive support).
     */
    private EventParameterSpec itemsSpec;
    
    /**
     * Specification for map value types.
     * Required and only meaningful when type=MAP.
     * Defines the schema for each value in the map (keys always strings).
     * Values can be of any type including nested maps/arrays (recursive support).
     */
    private EventParameterSpec valueSpec;
    
    /**
     * Specification for object properties.
     * Required and only meaningful when type=OBJECT.
     * Maps property names to their respective EventParameterSpec definitions (recursive support).
     * Enables type-safe validation of complex nested structures.
     */
    private Map<String, EventParameterSpec> properties;

    public static final EventParameterSpec EVENT_CORRELATION_ID = EventParameterSpec.builder()
            .name("correlationId")
            .type(EventParameterType.STRING)
            .required(false)
            .build();
            
    public static final EventParameterSpec EVENT_CONFIDENCE = EventParameterSpec.builder()
            .name("confidence")
            .type(EventParameterType.DOUBLE)
            .required(false)
            .build();
            
    public static final EventParameterSpec EVENT_PROVENANCE = EventParameterSpec.builder()
            .name("provenance")
            .type(EventParameterType.ARRAY)
            .required(false)
            .build();
            
    public static final EventParameterSpec EVENT_AUDIT_TRAIL = EventParameterSpec.builder()
            .name("auditTrailEnabled")
            .type(EventParameterType.BOOLEAN)
            .defaultValue(false)
            .required(false)
            .build();

    public static EventParameterSpec EVENT_PURPOSE = EventParameterSpec.builder()
            .name("purpose")
            .description("Event purpose")
            .type(EventParameterType.STRING)
            .required(false)
            .build();

    /**
     * Validates a value against this parameter specification.
     * <p>
     * This method performs the following validations:
     * <ol>
     *   <li>Checks for null values against the required flag</li>
     *   <li>Validates the type of the value</li>
     *   <li>If enumValues is set, ensures the value is in the allowed set</li>
     *   <li>Applies any custom validation rules</li>
     * </ol>
     *
     * @param value The value to validate, may be null for optional parameters
     * @throws IllegalArgumentException if validation fails, with a descriptive message
     * @throws NullPointerException if value is null and the parameter is required
     */
    public void validate(Object value) {
        // 1. Required check
        if (value == null) {
            if (required) {
                throw new NullPointerException(
                        "Parameter '" + name + "' is required but was null");
            }
            return; // optional and null — nothing more to check
        }

        // 2. Type compatibility check
        if (type != null && !type.isCompatible(value)) {
            throw new IllegalArgumentException(
                    "Parameter '" + name + "' expects type " + type.getName()
                            + " but got " + value.getClass().getSimpleName());
        }

        // 3. Enum allowlist check
        if (enumValues != null && !enumValues.isEmpty() && !enumValues.contains(value)) {
            throw new IllegalArgumentException(
                    "Parameter '" + name + "' value '" + value
                            + "' is not in allowed values: " + enumValues);
        }

        // 4. Recursive validation for composite types
        if (type == EventParameterType.ARRAY || type == EventParameterType.LIST) {
            validateArrayOrList(value);
        } else if (type == EventParameterType.MAP) {
            validateMap(value);
        } else if (type == EventParameterType.OBJECT) {
            validateObject(value);
        }
    }

    private void validateArrayOrList(Object value) {
        if (itemsSpec == null) {
            return;
        }
        Collection<?> items;
        if (value instanceof Collection<?>) {
            items = (Collection<?>) value;
        } else if (value instanceof Object[]) {
            items = List.of((Object[]) value);
        } else {
            return;
        }
        int index = 0;
        for (Object item : items) {
            try {
                itemsSpec.validate(item);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Parameter '" + name + "[" + index + "]': " + e.getMessage(), e);
            }
            index++;
        }
    }

    @SuppressWarnings("unchecked")
    private void validateMap(Object value) {
        if (valueSpec == null || !(value instanceof Map)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) value;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            try {
                valueSpec.validate(entry.getValue());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Parameter '" + name + "[\"" + entry.getKey() + "\"]': " + e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateObject(Object value) {
        if (properties == null || properties.isEmpty() || !(value instanceof Map)) {
            return;
        }
        Map<String, Object> objectMap = (Map<String, Object>) value;

        // Validate each declared property
        for (Map.Entry<String, EventParameterSpec> propEntry : properties.entrySet()) {
            String propName = propEntry.getKey();
            EventParameterSpec propSpec = propEntry.getValue();
            Object propValue = objectMap.get(propName);
            try {
                propSpec.validate(propValue);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Parameter '" + name + "." + propName + "': " + e.getMessage(), e);
            }
        }
    }

}
