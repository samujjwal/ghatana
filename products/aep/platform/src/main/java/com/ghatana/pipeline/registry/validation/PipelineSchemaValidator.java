package com.ghatana.pipeline.registry.validation;

import com.ghatana.pipeline.registry.model.Pipeline;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates pipeline configuration schema and structure.
 *
 * @doc.type class
 * @doc.purpose Validate pipeline config schema and structure
 * @doc.layer product
 * @doc.pattern Validator
 */
@Slf4j
@Singleton
public class PipelineSchemaValidator {

    /**
     * Validate pipeline schema.
     *
     * @param pipeline the pipeline to validate
     * @return validation result
     */
    public ValidationResult validate(Pipeline pipeline) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String config = pipeline.getConfig();
        if (config == null || config.isBlank()) {
            errors.add("Pipeline configuration is empty");
            return ValidationResult.builder()
                    .valid(false)
                    .errors(errors)
                    .build();
        }

        // Validate JSON/YAML structure
        if (!isValidJson(config) && !isValidYaml(config)) {
            errors.add("Pipeline configuration is not valid JSON or YAML");
        }

        // Check for required fields in config
        if (!config.contains("stages") && !config.contains("operators")) {
            warnings.add("Pipeline configuration does not contain 'stages' or 'operators' field");
        }

        // Validate version if present
        if (pipeline.getVersion() < 0) {
            errors.add("Pipeline version must be non-negative");
        }

        // Name length validation
        if (pipeline.getName() != null && pipeline.getName().length() > 255) {
            errors.add("Pipeline name exceeds maximum length of 255 characters");
        }

        boolean isValid = errors.isEmpty();

        return ValidationResult.builder()
                .valid(isValid)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    private boolean isValidJson(String config) {
        try {
            // Basic JSON validation - starts with { and ends with }
            String trimmed = config.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                // More thorough validation could use Jackson ObjectMapper
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidYaml(String config) {
        try {
            // Basic YAML validation - check for key: value patterns
            String trimmed = config.trim();
            return trimmed.contains(":") && !trimmed.startsWith("{");
        } catch (Exception e) {
            return false;
        }
    }
}

