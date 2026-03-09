package com.ghatana.virtualorg.framework.config;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for configuration templates.
 *
 * <p><b>Purpose</b><br>
 * Manages template definitions and provides template resolution/merging
 * for agent and department configurations. Enables "DRY" configuration
 * by allowing common patterns to be defined once and reused.
 *
 * <p><b>Key Features</b><br>
 * - Template registration and lookup
 * - Default value merging
 * - Override handling (additive and replacement)
 * - Parameter validation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TemplateRegistry registry = new TemplateRegistry();
 * registry.register(seniorEngineerTemplate);
 *
 * // Apply template to agent config
 * AgentConfig mergedConfig = registry.apply("senior-engineer", aliceConfig);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Template management and resolution
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class TemplateRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateRegistry.class);

    private final Map<String, TemplateConfig> templates = new ConcurrentHashMap<>();

    /**
     * Registers a template.
     *
     * @param template the template to register
     * @return promise completing when registered
     */
    public Promise<Void> register(TemplateConfig template) {
        templates.put(template.getName(), template);
        LOG.debug("Registered template: {} (kind: {})",
                template.getName(), template.spec().targetKind());
        return Promise.complete();
    }

    /**
     * Gets a template by name.
     *
     * @param name the template name
     * @return promise with optional template
     */
    public Promise<Optional<TemplateConfig>> get(String name) {
        return Promise.of(Optional.ofNullable(templates.get(name)));
    }

    /**
     * Gets all templates for a target kind.
     *
     * @param targetKind the kind (e.g., "Agent", "Department")
     * @return promise with list of templates
     */
    public Promise<List<TemplateConfig>> getByKind(String targetKind) {
        List<TemplateConfig> result = new ArrayList<>();
        for (TemplateConfig template : templates.values()) {
            if (targetKind.equals(template.spec().targetKind())) {
                result.add(template);
            }
        }
        return Promise.of(result);
    }

    /**
     * Applies a template to an agent configuration.
     *
     * @param templateName the template name
     * @param config the agent config to enhance
     * @return promise with merged config, or original if template not found
     */
    public Promise<Map<String, Object>> applyTemplate(String templateName, Map<String, Object> config) {
        TemplateConfig template = templates.get(templateName);
        if (template == null) {
            LOG.warn("Template '{}' not found, returning original config", templateName);
            return Promise.of(config);
        }

        Map<String, Object> merged = new LinkedHashMap<>();

        // Start with template defaults
        if (template.spec().defaults() != null) {
            merged.putAll(deepCopy(template.spec().defaults()));
        }

        // Merge in the config values (config wins)
        deepMerge(merged, config);

        LOG.debug("Applied template '{}' to config", templateName);
        return Promise.of(merged);
    }

    /**
     * Deep copies a map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) value));
            } else if (value instanceof List) {
                copy.put(entry.getKey(), new ArrayList<>((List<?>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    /**
     * Deep merges source into target.
     */
    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();
            Object targetValue = target.get(key);

            if (sourceValue instanceof Map && targetValue instanceof Map) {
                // Recursive merge for nested maps
                deepMerge((Map<String, Object>) targetValue, (Map<String, Object>) sourceValue);
            } else if (sourceValue instanceof List && targetValue instanceof List) {
                // Handle list merging (additive)
                List<Object> merged = new ArrayList<>((List<?>) targetValue);
                for (Object item : (List<?>) sourceValue) {
                    if (item instanceof String && ((String) item).startsWith("+")) {
                        // Additive item (e.g., "+security")
                        merged.add(((String) item).substring(1));
                    } else if (item instanceof String && ((String) item).startsWith("-")) {
                        // Subtractive item (e.g., "-legacy")
                        merged.remove(((String) item).substring(1));
                    } else if (!merged.contains(item)) {
                        merged.add(item);
                    }
                }
                target.put(key, merged);
            } else {
                // Direct replacement
                target.put(key, sourceValue);
            }
        }
    }

    /**
     * Gets all registered templates.
     */
    public Promise<List<TemplateConfig>> getAll() {
        return Promise.of(new ArrayList<>(templates.values()));
    }

    /**
     * Clears all templates.
     */
    public void clear() {
        templates.clear();
    }
}
