package com.ghatana.virtualorg.framework.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Input parameters for tool execution.
 *
 * <p>
 * <b>Purpose</b><br>
 * Wraps the input parameters passed to a tool, providing type-safe accessors
 * with default value support.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ToolInput input = ToolInput.of(Map.of(
 *     "repository", "owner/repo",
 *     "title", "Fix bug",
 *     "draft", true
 * ));
 *
 * String repo = input.getString("repository");
 * boolean draft = input.getBoolean("draft", false);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Tool input parameter wrapper
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ToolInput {

    private final Map<String, Object> parameters;

    private ToolInput(Map<String, Object> parameters) {
        this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    /**
     * Creates a ToolInput from a parameter map.
     *
     * @param parameters The parameters
     * @return A new ToolInput
     */
    public static ToolInput of(Map<String, Object> parameters) {
        return new ToolInput(parameters);
    }

    /**
     * Creates an empty ToolInput.
     *
     * @return An empty ToolInput
     */
    public static ToolInput empty() {
        return new ToolInput(Map.of());
    }

    /**
     * Gets all parameters.
     *
     * @return Immutable map of parameters
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Gets all parameter keys.
     *
     * @return Set of parameter keys
     */
    public java.util.Set<String> keys() {
        return parameters.keySet();
    }

    /**
     * Checks if a parameter exists.
     *
     * @param key The parameter key
     * @return true if the parameter exists
     */
    public boolean has(String key) {
        return parameters.containsKey(key);
    }

    /**
     * Gets a parameter value.
     *
     * @param key The parameter key
     * @return The value or null
     */
    public Object get(String key) {
        return parameters.get(key);
    }

    /**
     * Gets a string parameter.
     *
     * @param key The parameter key
     * @return The string value
     * @throws IllegalArgumentException if the parameter is missing or not a
     * string
     */
    public String getString(String key) {
        Object value = parameters.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return String.valueOf(value);
    }

    /**
     * Gets a string parameter with a default value.
     *
     * @param key The parameter key
     * @param defaultValue The default value
     * @return The string value or default
     */
    public String getString(String key, String defaultValue) {
        Object value = parameters.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    /**
     * Gets an integer parameter.
     *
     * @param key The parameter key
     * @return The integer value
     * @throws IllegalArgumentException if the parameter is missing or not
     * convertible to int
     */
    public int getInt(String key) {
        Object value = parameters.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * Gets an integer parameter with a default value.
     *
     * @param key The parameter key
     * @param defaultValue The default value
     * @return The integer value or default
     */
    public int getInt(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a long parameter with a default value.
     *
     * @param key The parameter key
     * @param defaultValue The default value
     * @return The long value or default
     */
    public long getLong(String key, long defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a double parameter with a default value.
     *
     * @param key The parameter key
     * @param defaultValue The default value
     * @return The double value or default
     */
    public double getDouble(String key, double defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a boolean parameter.
     *
     * @param key The parameter key
     * @param defaultValue The default value
     * @return The boolean value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String str = String.valueOf(value).toLowerCase();
        return "true".equals(str) || "1".equals(str) || "yes".equals(str);
    }

    /**
     * Gets a nested map parameter.
     *
     * @param key The parameter key
     * @return The map value or empty map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object value = parameters.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    /**
     * Gets a list parameter.
     *
     * @param key The parameter key
     * @return The list value or empty list
     */
    @SuppressWarnings("unchecked")
    public java.util.List<Object> getList(String key) {
        Object value = parameters.get(key);
        if (value instanceof java.util.List) {
            return (java.util.List<Object>) value;
        }
        return java.util.List.of();
    }

    /**
     * Creates a builder for ToolInput.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, Object> parameters = new HashMap<>();
        private String agentId;
        private String requestId;

        private Builder() {
        }

        public Builder put(String key, Object value) {
            parameters.put(key, value);
            return this;
        }

        public Builder putAll(Map<String, Object> params) {
            parameters.putAll(params);
            return this;
        }

        public Builder parameters(Map<String, Object> params) {
            parameters.putAll(params);
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public ToolInput build() {
            return new ToolInput(parameters);
        }
    }

    @Override
    public String toString() {
        return "ToolInput{parameters=" + parameters + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolInput toolInput = (ToolInput) o;
        return Objects.equals(parameters, toolInput.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters);
    }
}
