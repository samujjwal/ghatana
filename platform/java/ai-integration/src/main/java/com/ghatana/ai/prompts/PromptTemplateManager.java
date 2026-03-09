package com.ghatana.ai.prompts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized manager for prompt templates with validation and variable substitution.
 * 
 * Supports variable placeholders in the format {{variableName}} and provides
 * validation utilities for prompt consistency.
 * 
 * @doc.type class
 * @doc.purpose Provides centralized prompt validation and template management utilities for AI chains.
 * @doc.layer utility
 * @doc.pattern Registry
 */
public class PromptTemplateManager {
    private static final Logger log = LoggerFactory.getLogger(PromptTemplateManager.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    private final Map<String, String> templates = new ConcurrentHashMap<>();
    private final Map<String, PromptTemplate> compiledTemplates = new ConcurrentHashMap<>();
    
    /**
     * Registers a prompt template.
     *
     * @param name The template name
     * @param template The template string with {{variable}} placeholders
     * @throws IllegalArgumentException if name or template is empty
     */
    public void registerTemplate(String name, String template) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("template name cannot be null or empty");
        }
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("template cannot be null or empty");
        }
        
        templates.put(name, template);
        compiledTemplates.put(name, new PromptTemplate(template));
        log.debug("Registered prompt template: {}", name);
    }
    
    /**
     * Gets a registered template by name.
     *
     * @param name The template name
     * @return The template string
     * @throws IllegalArgumentException if template not found
     */
    public String getTemplate(String name) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + name);
        }
        return template;
    }
    
    /**
     * Renders a template with the given variables.
     *
     * @param name The template name
     * @param variables The variables to substitute
     * @return The rendered prompt
     * @throws IllegalArgumentException if template not found or required variables are missing
     */
    public String render(String name, Map<String, String> variables) {
        PromptTemplate template = compiledTemplates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + name);
        }
        
        return template.render(variables);
    }
    
    /**
     * Renders a template with the given variables, using default values for missing variables.
     *
     * @param name The template name
     * @param variables The variables to substitute
     * @param defaults Default values for missing variables
     * @return The rendered prompt
     * @throws IllegalArgumentException if template not found
     */
    public String renderWithDefaults(String name, Map<String, String> variables, Map<String, String> defaults) {
        Map<String, String> merged = new HashMap<>(defaults);
        if (variables != null) {
            merged.putAll(variables);
        }
        return render(name, merged);
    }
    
    /**
     * Gets all required variables for a template.
     *
     * @param name The template name
     * @return Set of required variable names
     * @throws IllegalArgumentException if template not found
     */
    public Set<String> getRequiredVariables(String name) {
        PromptTemplate template = compiledTemplates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + name);
        }
        return new HashSet<>(template.getRequiredVariables());
    }
    
    /**
     * Validates that all required variables are provided.
     *
     * @param name The template name
     * @param variables The variables to validate
     * @return true if all required variables are present, false otherwise
     * @throws IllegalArgumentException if template not found
     */
    public boolean validateVariables(String name, Map<String, String> variables) {
        Set<String> required = getRequiredVariables(name);
        if (variables == null) {
            return required.isEmpty();
        }
        return variables.keySet().containsAll(required);
    }
    
    /**
     * Gets missing variables for a template.
     *
     * @param name The template name
     * @param variables The variables provided
     * @return Set of missing variable names
     * @throws IllegalArgumentException if template not found
     */
    public Set<String> getMissingVariables(String name, Map<String, String> variables) {
        Set<String> required = getRequiredVariables(name);
        Set<String> missing = new HashSet<>(required);
        if (variables != null) {
            missing.removeAll(variables.keySet());
        }
        return missing;
    }
    
    /**
     * Lists all registered template names.
     *
     * @return Set of template names
     */
    public Set<String> listTemplates() {
        return new HashSet<>(templates.keySet());
    }
    
    /**
     * Removes a template.
     *
     * @param name The template name
     */
    public void removeTemplate(String name) {
        templates.remove(name);
        compiledTemplates.remove(name);
        log.debug("Removed prompt template: {}", name);
    }
    
    /**
     * Clears all templates.
     */
    public void clear() {
        templates.clear();
        compiledTemplates.clear();
        log.debug("Cleared all prompt templates");
    }
    
    /**
     * Inner class representing a compiled prompt template.
     */
    private static class PromptTemplate {
        private final String template;
        private final List<String> requiredVariables;
        
        PromptTemplate(String template) {
            this.template = template;
            this.requiredVariables = extractVariables(template);
        }
        
        String render(Map<String, String> variables) {
            // Validate all required variables are present
            Set<String> provided = variables != null ? variables.keySet() : Set.of();
            Set<String> missing = new HashSet<>(requiredVariables);
            missing.removeAll(provided);
            
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Missing required variables: " + missing
                );
            }
            
            String result = template;
            if (variables != null) {
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    String placeholder = "{{" + entry.getKey() + "}}";
                    result = result.replace(placeholder, entry.getValue());
                }
            }
            
            return result;
        }
        
        List<String> getRequiredVariables() {
            return new ArrayList<>(requiredVariables);
        }
        
        private static List<String> extractVariables(String template) {
            List<String> variables = new ArrayList<>();
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            while (matcher.find()) {
                variables.add(matcher.group(1).trim());
            }
            return variables;
        }
    }
}
