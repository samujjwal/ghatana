package com.ghatana.ai.llm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definition of a tool that can be used by an LLM for function calling.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a tool/function that the LLM can invoke during completion. Used
 * for structured output and agent tool use.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ToolDefinition tool = ToolDefinition.builder()
 *     .name("search_code")
 *     .description("Search the codebase for relevant code snippets")
 *     .addParameter("query", "string", "The search query", true)
 *     .addParameter("limit", "integer", "Max results to return", false)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose LLM tool/function definition for function calling
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, ParameterDefinition> parameters;
    private final List<String> requiredParameters;

    private ToolDefinition(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.description = Objects.requireNonNull(builder.description, "description must not be null");
        this.parameters = builder.parameters != null ? Map.copyOf(builder.parameters) : Map.of();
        this.requiredParameters = builder.requiredParameters != null ? List.copyOf(builder.requiredParameters) : List.of();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, ParameterDefinition> getParameters() {
        return parameters;
    }

    public List<String> getRequiredParameters() {
        return requiredParameters;
    }

    /**
     * Converts to OpenAI function calling format.
     *
     * @return Map representation for OpenAI API
     */
    public Map<String, Object> toOpenAIFormat() {
        Map<String, Object> properties = new java.util.HashMap<>();
        for (var entry : parameters.entrySet()) {
            properties.put(entry.getKey(), Map.of(
                    "type", entry.getValue().type(),
                    "description", entry.getValue().description()
            ));
        }

        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", properties,
                                "required", requiredParameters
                        )
                )
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parameter definition for a tool.
     */
    public record ParameterDefinition(String type, String description) {
    public ParameterDefinition

    {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}

public static final class Builder {

    private String name;
    private String description;
    private java.util.Map<String, ParameterDefinition> parameters = new java.util.HashMap<>();
    private java.util.List<String> requiredParameters = new java.util.ArrayList<>();

    private Builder() {
    }

    public Builder name(String name) {
        this.name = name;
        return this;
    }

    public Builder description(String description) {
        this.description = description;
        return this;
    }

    public Builder addParameter(String name, String type, String description, boolean required) {
        this.parameters.put(name, new ParameterDefinition(type, description));
        if (required) {
            this.requiredParameters.add(name);
        }
        return this;
    }

    public Builder parameters(Map<String, ParameterDefinition> parameters) {
        this.parameters = new java.util.HashMap<>(parameters);
        return this;
    }

    public Builder requiredParameters(List<String> requiredParameters) {
        this.requiredParameters = new java.util.ArrayList<>(requiredParameters);
        return this;
    }

    public ToolDefinition build() {
        return new ToolDefinition(this);
    }
}
}
