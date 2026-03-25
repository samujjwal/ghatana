package com.ghatana.yappc.services.lifecycle.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.activej.common.exception.MalformedDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * @doc.type class
 * @doc.purpose Configuration schema validation for YAPPC config YAML files
 * @doc.layer product
 * @doc.pattern Service
 *
 * Validates YAPPC configuration YAML files against JSON Schema definitions.
 * Supports validation of agent definitions, policies, lifecycle transitions,
 * and memory configurations.
 *
 * Usage:
 * <pre>
 *   ConfigurationValidator validator = new ConfigurationValidator(new File("config/schemas"));
 *   ValidationResult result = validator.validatePoliciesFile(new File("config/policies/lifecycle-policies.yaml"));
 *   if (!result.isValid()) {
 *     throw new MalformedDataException("Validation failed: " + result.getErrors());
 *   }
 * </pre>
 *
 * @since 2.4.0
 */
public class ConfigurationValidator {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final File schemaDir;
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    private JsonSchema policiesSchema;
    private JsonSchema agentSchema;
    private JsonSchema lifecycleSchema;
    private JsonSchema memorySchema;

    /**
     * Initialize ConfigurationValidator with schema directory.
     *
     * @param schemaDir Directory containing JSON schema files
     */
    public ConfigurationValidator(File schemaDir) {
        this.schemaDir = schemaDir;
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

        loadSchemas();
    }

    /**
     * Load all schema files from schema directory.
     */
    private void loadSchemas() {
        this.policiesSchema = loadSchema("policies-schema.json");
        this.agentSchema = loadSchema("agent-schema.json");
        this.lifecycleSchema = loadSchema("lifecycle-transitions-schema.json");
        this.memorySchema = loadSchema("memory-items-schema.json");
    }

    /**
     * Load a single schema file.
     *
     * @param schemaFileName Schema file name
     * @return JsonSchema or null if not found
     */
    private JsonSchema loadSchema(String schemaFileName) {
        try {
            File schemaFile = new File(schemaDir, schemaFileName);
            if (!schemaFile.exists()) {
                logger.warn("Schema file not found: {}", schemaFile.getAbsolutePath());
                return null;
            }
            return schemaFactory.getSchema(schemaFile.toURI());
        } catch (Exception e) {
            logger.error("Error loading schema {}: {}", schemaFileName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validate a policies YAML file.
     *
     * @param file YAML file to validate
     * @return ValidationResult with errors if any
     */
    public ValidationResult validatePoliciesFile(File file) {
        if (policiesSchema == null) {
            return ValidationResult.warning("Policies schema not loaded");
        }
        return validateFile(file, policiesSchema, "policies");
    }

    /**
     * Validate an agent definition YAML file.
     *
     * @param file YAML file to validate
     * @return ValidationResult with errors if any
     */
    public ValidationResult validateAgentFile(File file) {
        if (agentSchema == null) {
            return ValidationResult.warning("Agent schema not loaded");
        }
        return validateFile(file, agentSchema, "agent");
    }

    /**
     * Validate a lifecycle transitions YAML file.
     *
     * @param file YAML file to validate
     * @return ValidationResult with errors if any
     */
    public ValidationResult validateLifecycleFile(File file) {
        if (lifecycleSchema == null) {
            return ValidationResult.warning("Lifecycle schema not loaded");
        }
        return validateFile(file, lifecycleSchema, "lifecycle");
    }

    /**
     * Validate a memory configuration YAML file.
     *
     * @param file YAML file to validate
     * @return ValidationResult with errors if any
     */
    public ValidationResult validateMemoryFile(File file) {
        if (memorySchema == null) {
            return ValidationResult.warning("Memory schema not loaded");
        }
        return validateFile(file, memorySchema, "memory");
    }

    /**
     * Generic file validation against schema.
     *
     * @param file YAML file to validate
     * @param schema Target schema
     * @param schemaType Schema type (for logging)
     * @return ValidationResult with errors if any
     */
    private ValidationResult validateFile(File file, JsonSchema schema, String schemaType) {
        try {
            if (!file.exists()) {
                return ValidationResult.error("File not found: " + file.getAbsolutePath());
            }

            JsonNode yamlNode = objectMapper.readTree(file);
            Set<com.networknt.schema.ValidationMessage> errors = schema.validate(yamlNode);

            if (errors.isEmpty()) {
                logger.debug("✓ Valid: {} (against {} schema)", file.getName(), schemaType);
                return ValidationResult.success();
            } else {
                List<String> errorMessages = new ArrayList<>();
                for (com.networknt.schema.ValidationMessage error : errors) {
                    String message = String.format("%s: %s", error.getInstanceLocation(), error.getMessage());
                    errorMessages.add(message);
                    logger.debug("  - {}", message);
                }
                return ValidationResult.invalid(errorMessages);
            }
        } catch (IOException e) {
            String message = String.format("Error parsing YAML: %s", e.getMessage());
            logger.error(message, e);
            return ValidationResult.error(message);
        } catch (Exception e) {
            String message = String.format("Validation error: %s", e.getMessage());
            logger.error(message, e);
            return ValidationResult.error(message);
        }
    }

    /**
     * Validate all YAML files in a directory against corresponding schemas.
     *
     * @param configDir Directory containing config YAML files
     * @return ValidationResult aggregating all errors
     */
    public ValidationResult validateDirectory(File configDir) {
        if (!configDir.exists() || !configDir.isDirectory()) {
            return ValidationResult.error("Directory not found: " + configDir.getAbsolutePath());
        }

        List<String> allErrors = new ArrayList<>();
        File policiesDir = new File(configDir, "policies");
        File agentsDir = new File(configDir, "agents");
        File lifecycleDir = new File(configDir, "lifecycle");
        File memoryDir = new File(configDir, "memory");

        // Validate policies
        if (policiesDir.exists()) {
            ValidationResult result = validateDirRecursive(policiesDir, this::validatePoliciesFile);
            allErrors.addAll(result.getErrors());
        }

        // Validate agents
        if (agentsDir.exists()) {
            ValidationResult result = validateDirRecursive(agentsDir, this::validateAgentFile);
            allErrors.addAll(result.getErrors());
        }

        // Validate lifecycle
        if (lifecycleDir.exists()) {
            ValidationResult result = validateDirRecursive(lifecycleDir, this::validateLifecycleFile);
            allErrors.addAll(result.getErrors());
        }

        // Validate memory
        if (memoryDir.exists()) {
            ValidationResult result = validateDirRecursive(memoryDir, this::validateMemoryFile);
            allErrors.addAll(result.getErrors());
        }

        return allErrors.isEmpty() ? ValidationResult.success() : ValidationResult.invalid(allErrors);
    }

    /**
     * Recursively validate all YAML files in a directory.
     */
    private ValidationResult validateDirRecursive(File dir, ValidatorFunction validator) {
        List<String> errors = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    errors.addAll(validateDirRecursive(file, validator).getErrors());
                } else if (file.isFile() && file.getName().endsWith(".yaml")) {
                    ValidationResult result = validator.validate(file);
                    errors.addAll(result.getErrors());
                }
            }
        }
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.invalid(errors);
    }

    /**
     * Functional interface for validator function.
     */
    @FunctionalInterface
    private interface ValidatorFunction {
        ValidationResult validate(File file);
    }

    /**
     * Result of configuration validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final boolean warning;

        private ValidationResult(boolean valid, List<String> errors, boolean warning) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warning = warning;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList(), false);
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors, false);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, List.of(message), false);
        }

        public static ValidationResult warning(String message) {
            return new ValidationResult(true, List.of(message), true);
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isWarning() {
            return warning;
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public String getErrorsSummary() {
            if (errors.isEmpty()) {
                return "No errors";
            }
            return String.join("\n", errors);
        }
    }
}
