package com.ghatana.yappc.ai.requirements.ai.prompts;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template engine for LLM prompts with variable substitution.
 *
 * <p><b>Purpose:</b> Supports reusable prompt templates with variable placeholders
 * using {{variable}} syntax. Ensures consistent, maintainable prompt engineering.
 *
 * <p><b>Thread Safety:</b> Immutable after construction - safe for concurrent use.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   PromptTemplate template = new PromptTemplate(
 *       "Generate 5 requirements for the {{featureName}} feature. " +
 *       "Context: {{context}}. Format: {{format}}"
 *   );
 *
 *   String prompt = template.render(Map.of(
 *       "featureName", "User Authentication",
 *       "context", "OAuth 2.0 integration",
 *       "format", "JSON array of requirement objects"
 *   ));
 *
 *   // Result:
 *   // "Generate 5 requirements for the User Authentication feature. ..."
 * }</pre>
 *
 * @doc.type Utility Class
 * @doc.purpose LLM prompt template engine
 * @doc.layer Application
 * @doc.pattern Template method with variable substitution
 * @since 1.0.0
 */
public class PromptTemplate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}");

    private final String template;

    /**
     * Constructs a PromptTemplate with the given template string.
     *
     * @param template Template string with {{variable}} placeholders
     * @throws NullPointerException if template is null
     * @throws IllegalArgumentException if template is empty
     */
    public PromptTemplate(String template) {
        this.template = Objects.requireNonNull(template, "template is required");
        if (template.isEmpty()) {
            throw new IllegalArgumentException("template cannot be empty");
        }
    }

    /**
     * Renders the template with provided variable values.
     *
     * @param variables Map of variable names to values
     * @return Rendered prompt string with all {{variable}} replaced
     * @throws NullPointerException if variables is null or any required variable is missing
     * @throws IllegalArgumentException if template contains undefined variables
     */
    public String render(Map<String, String> variables) {
        Objects.requireNonNull(variables, "variables is required");

        String result = template;
        Matcher matcher = VARIABLE_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String variable = matcher.group(1);
            String value = variables.get(variable);

            if (value == null) {
                throw new IllegalArgumentException("Undefined variable: " + variable);
            }

            // Escape backslashes and dollar signs in the replacement to avoid regex interpretation
            String escapedValue = Matcher.quoteReplacement(value);
            matcher.appendReplacement(sb, escapedValue);
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Renders the template with variable values from an Object map.
     *
     * @param variables Map where values are converted to String
     * @return Rendered prompt string
     */
    public String renderFromObjects(Map<String, Object> variables) {
        Map<String, String> stringVars = new HashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            stringVars.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "null");
        }
        return render(stringVars);
    }

    /**
     * Extracts all variable names from the template.
     *
     * @return Set of variable names (without {{}} delimiters)
     */
    public java.util.Set<String> getVariableNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * @return The raw template string
     */
    public String getTemplate() {
        return template;
    }

    /**
     * @return Number of variables in the template
     */
    public int getVariableCount() {
        return getVariableNames().size();
    }

    /**
     * Validates that all required variables are present.
     *
     * @param variables Map to validate
     * @return True if all template variables are present in the map
     */
    public boolean isValid(Map<String, String> variables) {
        try {
            getVariableNames().forEach(varName -> {
                if (!variables.containsKey(varName)) {
                    throw new IllegalArgumentException("Missing variable: " + varName);
                }
            });
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "PromptTemplate{" +
                "variableCount=" + getVariableCount() +
                ", templateLength=" + template.length() +
                '}';
    }
}