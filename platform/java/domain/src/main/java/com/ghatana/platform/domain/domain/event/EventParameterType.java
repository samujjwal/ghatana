package com.ghatana.platform.domain.domain.event;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enumerates all supported data types for event parameters (headers and payload fields).
 * 
 * Comprehensive type enumeration enabling type-safe event schema validation, serialization, and processing.
 * Maps Java types to semantic parameter types for event metadata and payload handling.
 * 
 * Type Categories:
 * - Primitive Types: STRING, CHAR, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN
 * - Date/Time Types: DATE, TIME, DATE_TIME, TIMESTAMP
 * - Number Types: BIG_DECIMAL, BIG_INTEGER
 * - Composite Types: ARRAY, LIST, SET, MAP
 * - Special Types: ENUM, BINARY, OBJECT
 * - Error Handling: UNKNOWN (for unmapped types)
 * 
 * Architecture Role:
 * - Used by: Event schema validators, serialization/deserialization, parameter mapping
 * - Created by: Event type definitions, parameter specifications
 * - Stored in: Event schema metadata, parameter type definitions
 * - Purpose: Enable type-safe event processing and validation
 * 
 * Type Mapping:
 * Each enum constant maps to one primary Java type and zero or more compatible types:
 * - STRING → String
 * - INTEGER → int, Integer
 * - DOUBLE → double, Double
 * - TIMESTAMP → Instant, Date
 * - etc.
 * 
 * Key Features:
 * - getPrimaryType(): Get canonical Java class for type
 * - getCompatibleTypes(): Get all acceptable types
 * - isCompatible(Object): Check if value matches type
 * - forClass(Class<?>): Reverse lookup from Java class to EventParameterType
 * 
 * Usage Example:
 * {@code
 * // Get type for parameter
 * EventParameterType paramType = EventParameterType.forClass(Long.class);
 * 
 * // Validate value type
 * if (EventParameterType.INTEGER.isCompatible(42)) { ... }
 * 
 * // Get Java class for serialization
 * Class<?> javaType = eventType.getPrimaryType();
 * 
 * // Check compatible types (e.g., int and Integer both valid)
 * Class<?>[] compatible = EventParameterType.INTEGER.getCompatibleTypes();
 * }
 * 
 * Compatibility Rules:
 * 1. Null is compatible with all types
 * 2. Primitive types compatible with their wrapper classes
 * 3. Compatible types checked via isAssignableFrom()
 * 4. Unknown/Object returned for unmapped types
 * 
 * Type Resolution Order in forClass():
 * 1. Check for exact primary type match
 * 2. Check compatible types
 * 3. Check assignability via isAssignableFrom()
 * 4. Special handling for primitive/wrapper pairs
 * 5. Check if type is Enum
 * 6. Default to OBJECT for unknown types
 * 
 * Thread Safety: Enum constants are immutable and thread-safe.
 * Performance: O(n) for forClass() where n = number of type constants, O(1) for accessors.
 * 
 * @doc.type enum
 * @doc.layer domain
 * @doc.purpose type-safe enumeration for event parameter data type specification
 * @doc.pattern enum-with-methods
 * @doc.test-hints test forClass() for all Java types, test isCompatible(), test primitive/wrapper handling
 * @see EventParameterSpec (uses EventParameterType for field definitions)
 * @see Priority (related enum for metadata)
 */
public enum EventParameterType {
    // Primitive types
    STRING("String", String.class),
    CHARACTER("Character", char.class, Character.class),
    BYTE("Byte", byte.class, Byte.class),
    SHORT("Short", short.class, Short.class),
    INTEGER("Integer", int.class, Integer.class),
    LONG("Long", long.class, Long.class),
    FLOAT("Float", float.class, Float.class),
    DOUBLE("Double", double.class, Double.class),
    BOOLEAN("Boolean", boolean.class, Boolean.class),
    
    // Date/time types
    DATE("Date", LocalDate.class, Date.class),
    TIME("Time", LocalTime.class),
    DATE_TIME("DateTime", LocalDateTime.class),
    TIMESTAMP("Timestamp", Instant.class, Date.class),
    
    // Number types
    BIG_DECIMAL("BigDecimal", BigDecimal.class),
    BIG_INTEGER("BigInteger", BigInteger.class),
    
    // Composite types
    ARRAY("Array", Object[].class),
    LIST("List", List.class),
    SET("Set", Set.class),
    MAP("Map", Map.class),
    
    // Special types
    ENUM("Enum", Enum.class),
    BINARY("Binary", byte[].class),
    
    // Custom types
    OBJECT("Object", Object.class),
    
    // Unknown type (for error handling)
    UNKNOWN("Unknown", Void.class);

    private final String name;
    private final Class<?> primaryType;
    private final Class<?>[] compatibleTypes;

    /**
     * Creates a new EventParameterType with the given name and primary type.
     * 
     * @param name The display name of the type
     * @param primaryType The primary Java class for this type
     * @param compatibleTypes Additional compatible Java classes
     */
    EventParameterType(String name, Class<?> primaryType, Class<?>... compatibleTypes) {
        this.name = name;
        this.primaryType = primaryType;
        this.compatibleTypes = compatibleTypes;
    }

    /**
     * Gets the display name of this type.
     * 
     * @return The display name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the primary Java class for this type.
     * 
     * @return The primary type class
     */
    public Class<?> getPrimaryType() {
        return primaryType;
    }

    /**
     * Gets all compatible Java classes for this type.
     * 
     * @return An array of compatible types
     */
    public Class<?>[] getCompatibleTypes() {
        return compatibleTypes.clone();
    }

    /**
     * Checks if the given value is compatible with this parameter type.
     * 
     * @param value The value to check
     * @return true if the value is compatible, false otherwise
     */
    public boolean isCompatible(Object value) {
        if (value == null) {
            return true; // Null is compatible with all types
        }
        
        Class<?> valueClass = value.getClass();
        
        // Check primary type
        if (primaryType.isAssignableFrom(valueClass)) {
            return true;
        }
        
        // Check compatible types
        for (Class<?> type : compatibleTypes) {
            if (type.isAssignableFrom(valueClass)) {
                return true;
            }
        }
        
        // Special handling for primitive types
        if (primaryType.isPrimitive()) {
            return (primaryType == int.class && valueClass == Integer.class) ||
                   (primaryType == long.class && valueClass == Long.class) ||
                   (primaryType == double.class && valueClass == Double.class) ||
                   (primaryType == float.class && valueClass == Float.class) ||
                   (primaryType == boolean.class && valueClass == Boolean.class) ||
                   (primaryType == char.class && valueClass == Character.class) ||
                   (primaryType == byte.class && valueClass == Byte.class) ||
                   (primaryType == short.class && valueClass == Short.class);
        }
        
        return false;
    }

    /**
     * Gets the EventParameterType for the given class.
     * 
     * @param clazz The class to get the type for
     * @return The corresponding EventParameterType, or UNKNOWN if not found
     */
    public static EventParameterType forClass(Class<?> clazz) {
        if (clazz == null) {
            return UNKNOWN;
        }
        
        for (EventParameterType type : values()) {
            if (type.primaryType.equals(clazz)) {
                return type;
            }
            
            for (Class<?> compatibleType : type.compatibleTypes) {
                if (compatibleType.equals(clazz)) {
                    return type;
                }
            }
            
            if (type.primaryType.isAssignableFrom(clazz)) {
                return type;
            }
            
            // Special handling for primitive types
            if ((type.primaryType == int.class && clazz == Integer.class) ||
                (type.primaryType == long.class && clazz == Long.class) ||
                (type.primaryType == double.class && clazz == Double.class) ||
                (type.primaryType == float.class && clazz == Float.class) ||
                (type.primaryType == boolean.class && clazz == Boolean.class) ||
                (type.primaryType == char.class && clazz == Character.class) ||
                (type.primaryType == byte.class && clazz == Byte.class) ||
                (type.primaryType == short.class && clazz == Short.class)) {
                return type;
            }
        }
        
        // Check for enums
        if (clazz.isEnum()) {
            return ENUM;
        }
        
        return OBJECT;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
