package com.ghatana.virtualorg.framework.tools;

import java.util.*;

/**
 * Schema definition for tool input/output parameters.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the JSON Schema for tool parameters, enabling validation and
 * documentation of tool inputs and outputs.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ToolSchema schema = ToolSchema.builder()
 *     .property("repository", SchemaType.STRING, "Owner/repo format", true)
 *     .property("title", SchemaType.STRING, "PR title", true)
 *     .property("draft", SchemaType.BOOLEAN, "Create as draft", false)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Tool parameter schema definition
 * @doc.layer product
 * @doc.pattern Builder
 */
public final class ToolSchema {

    /**
     * Schema property types.
     */
    public enum SchemaType {
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        ARRAY,
        OBJECT
    }

    /**
     * Property definition.
     */
    public record Property(
            String name,
            SchemaType type,
            String description,
            boolean required,
            Object defaultValue,
            List<String> enumValues
    ) {
        

    public Property(String name, SchemaType type, String description, boolean required) {
        this(name, type, description, required, null, null);
    }
}

private final Map<String, Property> properties;
    private final Set<String> requiredProperties;
    private final String description;

    private ToolSchema(Builder builder) {
        this.properties = Map.copyOf(builder.properties);
        this.requiredProperties = Set.copyOf(builder.requiredProperties);
        this.description = builder.description;
    }

    /**
     * Gets all properties.
     *
     * @return Immutable map of property name to definition
     */
    public Map<String, Property> getProperties() {
        return properties;
    }

    /**
     * Gets required property names.
     *
     * @return Set of required property names
     */
    public Set<String> getRequiredProperties() {
        return requiredProperties;
    }

    /**
     * Gets the schema description.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets a property definition.
     *
     * @param name The property name
     * @return The property or null if not found
     */
    public Property getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Checks if a property is required.
     *
     * @param name The property name
     * @return true if required
     */
    public boolean isRequired(String name) {
        return requiredProperties.contains(name);
    }

    /**
     * Validates input against this schema.
     *
     * @param input The input to validate
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate(ToolInput input) {
        List<String> errors = new ArrayList<>();

        // Check required properties
        for (String required : requiredProperties) {
            if (!input.has(required)) {
                errors.add("Missing required property: " + required);
            }
        }

        // Check property types
        for (Map.Entry<String, Property> entry : properties.entrySet()) {
            String name = entry.getKey();
            Property prop = entry.getValue();
            if (input.has(name)) {
                Object value = input.get(name);
                if (!isValidType(value, prop.type())) {
                    errors.add(String.format("Property '%s' should be %s but was %s",
                            name, prop.type(), value.getClass().getSimpleName()));
                }
            }
        }

        return errors;
    }

    private boolean isValidType(Object value, SchemaType expectedType) {
        if (value == null) {
            return true; // null is valid for any type (required check is separate)
        }
        return switch (expectedType) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof List;
            case OBJECT -> value instanceof Map;
        };
    }

    /**
     * Converts this schema to a JSON-like map representation.
     *
     * @return Map representation of the schema
     */
    public Map<String, Object> toMap() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        if (description != null) {
            schema.put("description", description);
        }

        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<String, Property> entry : properties.entrySet()) {
            Property prop = entry.getValue();
            Map<String, Object> propDef = new LinkedHashMap<>();
            propDef.put("type", prop.type().name().toLowerCase());
            propDef.put("description", prop.description());
            if (prop.defaultValue() != null) {
                propDef.put("default", prop.defaultValue());
            }
            if (prop.enumValues() != null && !prop.enumValues().isEmpty()) {
                propDef.put("enum", prop.enumValues());
            }
            props.put(entry.getKey(), propDef);
        }
        schema.put("properties", props);

        if (!requiredProperties.isEmpty()) {
            schema.put("required", new ArrayList<>(requiredProperties));
        }

        return schema;
    }

    /**
     * Creates a new builder.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty schema.
     *
     * @return An empty schema
     */
    public static ToolSchema empty() {
        return new Builder().build();

}

    /**
     * Builder for ToolSchema.
     */
    public static final class Builder {

    private final Map<String, Property> properties = new LinkedHashMap<>();
    private final Set<String> requiredProperties = new HashSet<>();
    private String description;

    private Builder() {
    }

    /**
     * Sets the schema description.
     *
     * @param description The description
     * @return This builder
     */
    public Builder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Adds a property.
     *
     * @param name Property name
     * @param type Property type
     * @param description Property description
     * @param required Whether the property is required
     * @return This builder
     */
    public Builder property(String name, SchemaType type, String description, boolean required) {
        properties.put(name, new Property(name, type, description, required));
        if (required) {
            requiredProperties.add(name);
        }
        return this;
    }

    /**
     * Adds a property with default value.
     *
     * @param name Property name
     * @param type Property type
     * @param description Property description
     * @param required Whether the property is required
     * @param defaultValue Default value
     * @return This builder
     */
    public Builder property(String name, SchemaType type, String description, boolean required, Object defaultValue) {
        properties.put(name, new Property(name, type, description, required, defaultValue, null));
        if (required) {
            requiredProperties.add(name);
        }
        return this;
    }

    /**
     * Adds an enum property.
     *
     * @param name Property name
     * @param description Property description
     * @param required Whether the property is required
     * @param enumValues Allowed enum values
     * @return This builder
     */
    public Builder enumProperty(String name, String description, boolean required, List<String> enumValues) {
        properties.put(name, new Property(name, SchemaType.STRING, description, required, null, enumValues));
        if (required) {
            requiredProperties.add(name);
        }
        return this;
    }

    /**
     * Builds the schema.
     *
     * @return The built schema
     */
    public ToolSchema build() {
        return new ToolSchema(this);
    }
}

@Override
public String toString() {
        return "ToolSchema{properties=" + properties.keySet() + ", required=" + requiredProperties + '}';
    }
}
