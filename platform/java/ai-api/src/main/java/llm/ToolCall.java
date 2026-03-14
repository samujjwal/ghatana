package com.ghatana.ai.llm;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a tool call made by an LLM during completion.
 *
 * <p>
 * <b>Purpose</b><br>
 * Captures the tool invocation request from an LLM response, including the tool
 * name and parsed arguments.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ToolCall call = ToolCall.of("search_code", Map.of("query", "authentication"));
 * String toolName = call.getName();
 * String query = call.getArgument("query", String.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Represents an LLM tool/function call
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class ToolCall {

    private final String id;
    private final String name;
    private final Map<String, Object> arguments;

    private ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
    }

    /**
     * Creates a new tool call.
     *
     * @param name The tool name
     * @param arguments The tool arguments
     * @return A new ToolCall instance
     */
    public static ToolCall of(String name, Map<String, Object> arguments) {
        return new ToolCall(java.util.UUID.randomUUID().toString(), name, arguments);
    }

    /**
     * Creates a new tool call with a specific ID.
     *
     * @param id The tool call ID (from LLM response)
     * @param name The tool name
     * @param arguments The tool arguments
     * @return A new ToolCall instance
     */
    public static ToolCall of(String id, String name, Map<String, Object> arguments) {
        return new ToolCall(id, name, arguments);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * Gets a typed argument value.
     *
     * @param key The argument key
     * @param type The expected type
     * @param <T> The type parameter
     * @return The argument value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getArgument(String key, Class<T> type) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        // Handle common type conversions
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (type == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        if (type == Boolean.class && value instanceof Boolean) {
            return (T) value;
        }
        throw new IllegalArgumentException(
                "Cannot convert argument '" + key + "' of type " + value.getClass() + " to " + type);
    }

    /**
     * Gets a string argument with a default value.
     *
     * @param key The argument key
     * @param defaultValue The default value if not found
     * @return The argument value or default
     */
    public String getString(String key, String defaultValue) {
        String value = getArgument(key, String.class);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets an integer argument with a default value.
     *
     * @param key The argument key
     * @param defaultValue The default value if not found
     * @return The argument value or default
     */
    public int getInt(String key, int defaultValue) {
        Integer value = getArgument(key, Integer.class);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets a boolean argument with a default value.
     *
     * @param key The argument key
     * @param defaultValue The default value if not found
     * @return The argument value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Boolean value = getArgument(key, Boolean.class);
        return value != null ? value : defaultValue;
    }

    @Override
    public String toString() {
        return "ToolCall{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", arguments=" + arguments
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolCall toolCall = (ToolCall) o;
        return Objects.equals(id, toolCall.id)
                && Objects.equals(name, toolCall.name)
                && Objects.equals(arguments, toolCall.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, arguments);
    }
}
