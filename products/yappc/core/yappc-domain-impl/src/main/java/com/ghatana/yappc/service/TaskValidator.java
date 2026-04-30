package com.ghatana.yappc.service;

import com.ghatana.yappc.domain.task.ParameterSpec;
import com.ghatana.yappc.domain.task.TaskDefinition;
import com.ghatana.platform.core.validation.ValidationResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates task definitions and inputs.
 *
 * @doc.type class
 * @doc.purpose Task validation logic
 * @doc.layer product
 * @doc.pattern Validator
 */
public class TaskValidator {

    private static final Logger LOG = LoggerFactory.getLogger(TaskValidator.class);

    /**
     * Validates a task definition.
     *
     * @param definition Task definition to validate
     * @return Validation result
     */
    @NotNull
    public ValidationResult validateTaskDefinition(@NotNull TaskDefinition definition) {
        List<ValidationResult.Violation> errors = new ArrayList<>();

        // Validate ID
        if (definition.id() == null || definition.id().isBlank()) {
            errors.add(new ValidationResult.Violation("id", "Task ID cannot be null or blank"));
        }

        // Validate name
        if (definition.name() == null || definition.name().isBlank()) {
            errors.add(new ValidationResult.Violation("name", "Task name cannot be null or blank"));
        }

        // Validate capabilities
        if (definition.requiredCapabilities().isEmpty()) {
            errors.add(new ValidationResult.Violation("requiredCapabilities", "Task must require at least one capability"));
        }

        // Validate parameters
        for (Map.Entry<String, ParameterSpec> entry : definition.parameters().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                errors.add(new ValidationResult.Violation("parameters", "Parameter name cannot be null or blank"));
            }
            if (entry.getValue() == null) {
                errors.add(new ValidationResult.Violation("parameters." + entry.getKey(), "Parameter spec cannot be null for: " + entry.getKey()));
            }
        }

        if (!errors.isEmpty()) {
            LOG.warn("Task definition validation failed: {}", errors);
            return ValidationResult.of(errors);
        }

        return ValidationResult.valid();
    }

    /**
     * Validates task input against parameter specifications.
     *
     * @param task  Task definition
     * @param input Task input
     * @return Validation result
     */
    @NotNull
    public ValidationResult validateInput(@NotNull TaskDefinition task, @NotNull Object input) {
        List<ValidationResult.Violation> errors = new ArrayList<>();

        // If input is a Map, validate against parameter specs
        if (input instanceof Map<?, ?> inputMap) {
            for (Map.Entry<String, ParameterSpec> entry : task.parameters().entrySet()) {
                String paramName = entry.getKey();
                ParameterSpec spec = entry.getValue();

                // Check required parameters
                if (spec.required() && !inputMap.containsKey(paramName)) {
                    errors.add(new ValidationResult.Violation("input." + paramName, "Required parameter missing: " + paramName));
                }

                // Type validation (basic)
                if (inputMap.containsKey(paramName)) {
                    Object value = inputMap.get(paramName);
                    if (!validateType(value, spec.type())) {
                        errors.add(new ValidationResult.Violation(
                                "input." + paramName,
                                "Parameter " + paramName + " has invalid type. Expected: " + spec.type()
                        ));
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            LOG.debug("Task input validation failed for {}: {}", task.id(), errors);
            return ValidationResult.of(errors);
        }

        return ValidationResult.valid();
    }

    /**
     * Validates value type.
     */
    private boolean validateType(Object value, String expectedType) {
        if (value == null) {
            return true; // Null is valid for optional parameters
        }

        return switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List || value.getClass().isArray();
            default -> true; // Unknown types pass validation
        };
    }
}
