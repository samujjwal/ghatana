package com.ghatana.products.collection.domain.generation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable value object for prompt templates.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates a prompt template with variable substitution support.
 * Supports system prompts, user prompts, and nested variable resolution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PromptTemplate template = PromptTemplate.builder()
 *     .systemPrompt("You are a helpful assistant.")
 *     .userPrompt("Summarize the following: {{text}}")
 *     .variable("text", "Lorem ipsum...")
 *     .build();
 * 
 * String resolved = template.resolve();  // Replaces {{text}} with value
 * }</pre>
 *
 * <p><b>Variable Format</b><br>
 * Variables are enclosed in double curly braces: {@code {{variableName}}}
 * Variable names must be alphanumeric with underscores.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after construction; thread-safe.
 *
 * @doc.type class
 * @doc.purpose Immutable prompt template with variable substitution
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class PromptTemplate {

    private final String systemPrompt;
    private final String userPrompt;
    private final Map<String, String> variables;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}");

    /**
     * Creates a new prompt template.
     *
     * @param systemPrompt optional system prompt (can be null)
     * @param userPrompt user prompt template (non-null)
     * @param variables map of variable names to values (non-null)
     * @throws NullPointerException if userPrompt or variables is null
     * @throws IllegalArgumentException if systemPrompt or userPrompt exceed 10,000 chars
     */
    private PromptTemplate(String systemPrompt, String userPrompt, Map<String, String> variables) {
        this.systemPrompt = validatePrompt(systemPrompt, "systemPrompt");
        this.userPrompt = Objects.requireNonNull(
                validatePrompt(userPrompt, "userPrompt"),
                "userPrompt cannot be null"
        );
        this.variables = Collections.unmodifiableMap(new HashMap<>(
                Objects.requireNonNull(variables, "variables cannot be null")
        ));
    }

    /**
     * Gets the system prompt.
     *
     * @return system prompt or null if not set
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Gets the user prompt template.
     *
     * @return user prompt template (non-null)
     */
    public String getUserPrompt() {
        return userPrompt;
    }

    /**
     * Gets all variables.
     *
     * @return unmodifiable map of variables
     */
    public Map<String, String> getVariables() {
        return variables;
    }

    /**
     * Resolves the template by substituting variables.
     *
     * <p>Replaces all {@code {{variableName}}} occurrences with corresponding values.
     * If a variable is referenced in template but not provided, returns original placeholder.
     *
     * @return resolved user prompt with variables substituted
     */
    public String resolveUserPrompt() {
        return resolve(userPrompt);
    }

    /**
     * Resolves the system prompt by substituting variables.
     *
     * @return resolved system prompt with variables substituted (or null if system prompt not set)
     */
    public String resolveSystemPrompt() {
        return systemPrompt != null ? resolve(systemPrompt) : null;
    }

    /**
     * Gets all referenced variables in the template.
     *
     * <p>Scans both system and user prompts for variable references.
     *
     * @return set of variable names referenced in prompts
     */
    public Set<String> getReferencedVariables() {
        Set<String> referenced = new HashSet<>();
        extractVariables(systemPrompt, referenced);
        extractVariables(userPrompt, referenced);
        return referenced;
    }

    /**
     * Validates that all referenced variables are provided.
     *
     * @return true if all variables used in prompts are provided
     */
    public boolean isComplete() {
        Set<String> referenced = getReferencedVariables();
        return variables.keySet().containsAll(referenced);
    }

    /**
     * Gets list of missing variables.
     *
     * @return set of variable names referenced but not provided
     */
    public Set<String> getMissingVariables() {
        Set<String> referenced = getReferencedVariables();
        Set<String> missing = new HashSet<>(referenced);
        missing.removeAll(variables.keySet());
        return missing;
    }

    /**
     * Creates a new builder for PromptTemplate.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves a prompt string by substituting variables.
     *
     * @param prompt prompt template to resolve (can be null)
     * @return resolved prompt with variables substituted (null if input null)
     */
    private String resolve(String prompt) {
        if (prompt == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(prompt);
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before variable
            result.append(prompt, lastEnd, matcher.start());

            // Append variable value or original placeholder if not found
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, matcher.group(0));
            result.append(value);

            lastEnd = matcher.end();
        }

        // Append remaining text
        result.append(prompt.substring(lastEnd));
        return result.toString();
    }

    /**
     * Extracts variable names from a prompt.
     *
     * @param prompt prompt to scan (can be null)
     * @param variables set to collect variable names
     */
    private void extractVariables(String prompt, Set<String> variables) {
        if (prompt == null) {
            return;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(prompt);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
    }

    /**
     * Validates prompt length.
     *
     * @param prompt prompt to validate (can be null)
     * @param name parameter name for error messages
     * @return validated prompt (can be null)
     * @throws IllegalArgumentException if prompt exceeds 10,000 characters
     */
    private String validatePrompt(String prompt, String name) {
        if (prompt != null && prompt.length() > 10000) {
            throw new IllegalArgumentException(name + " cannot exceed 10,000 characters");
        }
        return prompt;
    }

    @Override
    public String toString() {
        return "PromptTemplate{" +
                "systemPrompt=" + (systemPrompt != null ? systemPrompt.length() + " chars" : "null") +
                ", userPrompt=" + userPrompt.length() + " chars" +
                ", variables=" + variables.size() +
                '}';
    }

    /**
     * Builder for PromptTemplate construction.
     *
     * <p>Provides fluent API for building templates with optional components.
     */
    public static class Builder {
        private String systemPrompt;
        private String userPrompt;
        private final Map<String, String> variables = new HashMap<>();

        /**
         * Sets the system prompt.
         *
         * @param systemPrompt system prompt text
         * @return this builder
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Sets the user prompt.
         *
         * @param userPrompt user prompt template
         * @return this builder
         * @throws NullPointerException if userPrompt is null
         */
        public Builder userPrompt(String userPrompt) {
            this.userPrompt = Objects.requireNonNull(userPrompt, "userPrompt cannot be null");
            return this;
        }

        /**
         * Adds a variable.
         *
         * @param name variable name
         * @param value variable value
         * @return this builder
         * @throws NullPointerException if name or value is null
         */
        public Builder variable(String name, String value) {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            variables.put(name, value);
            return this;
        }

        /**
         * Adds multiple variables.
         *
         * @param vars map of variables
         * @return this builder
         * @throws NullPointerException if vars is null
         */
        public Builder variables(Map<String, String> vars) {
            Objects.requireNonNull(vars, "vars cannot be null");
            variables.putAll(vars);
            return this;
        }

        /**
         * Builds the PromptTemplate.
         *
         * @return configured PromptTemplate
         * @throws NullPointerException if userPrompt not set
         */
        public PromptTemplate build() {
            return new PromptTemplate(systemPrompt, userPrompt, variables);
        }
    }
}
