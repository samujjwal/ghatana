/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.catalog.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for agent definition YAML files.
 * Validates agent definitions against schema requirements and business rules.
 *
 * @doc.type class
 * @doc.purpose Validates agent definition YAML files for correctness and completeness
 * @doc.layer catalog
 * @doc.pattern Validation
 */
public final class AgentDefinitionValidator {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    
    // Valid enum values - must match platform enum definitions
    private static final Set<String> VALID_AGENT_TYPES = Set.of(
        "deterministic", "probabilistic", "stream_processor", "planning",
        "hybrid", "adaptive", "composite", "reactive", "custom"
    );
    private static final Set<String> VALID_SUBTYPES = Set.of(
        "rule-based", "ml-based", "hybrid", "orchestrated",
        "bandit", "rule+llm", "parameter-optimization", "threshold",
        "workflow", "ingestion"
    );
    private static final Set<String> VALID_DETERMINISM_GUARANTEES = Set.of(
        "full", "config_scoped", "config-scoped", "none", "eventual"
    );
    private static final Set<String> VALID_STATE_MUTABILITY = Set.of(
        "stateless", "local_state", "local-state", "external_state", "external-state", "hybrid_state"
    );
    private static final Set<String> VALID_FAILURE_MODES = Set.of(
        "fail_fast", "fail-fast", "retry", "fallback", "skip", "dead_letter", "dead-letter", "circuit_breaker", "circuit-breaker"
    );
    private static final Set<String> VALID_CRITICALITY_LEVELS = Set.of(
        "low", "medium", "high", "critical", "mission-critical"
    );
    private static final Set<String> VALID_AUTONOMY_LEVELS = Set.of(
        "manual", "semi-autonomous", "autonomous", "fully-autonomous", "assisted"
    );
    private static final Set<String> VALID_STATUS = Set.of("active", "deprecated", "experimental", "draft");
    private static final Set<String> VALID_MEMORY_TYPES = Set.of(
        "NONE", "WORKING", "EPISODIC", "LONG_TERM", "SEMANTIC"
    );
    private static final Set<String> VALID_MEMORY_RETENTION = Set.of("none", "short", "medium", "long", "permanent");
    private static final Set<String> VALID_TOOL_TYPES = Set.of("SERVICE", "DATABASE", "FILE", "EXTERNAL_API", "PLUGIN");

    private AgentDefinitionValidator() {
    }

    /**
     * Validates an agent definition YAML file.
     *
     * @param definitionPath path to the agent definition YAML file
     * @return validation result with errors and warnings
     */
    public static ValidationResult validate(Path definitionPath) {
        return validate(definitionPath, null);
    }

    /**
     * Validates an agent definition YAML file with optional capability taxonomy validation.
     *
     * @param definitionPath path to the agent definition YAML file
     * @param capabilitiesPath path to capabilities taxonomy file for cross-reference validation (optional)
     * @return validation result with errors and warnings
     */
    public static ValidationResult validate(Path definitionPath, Path capabilitiesPath) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            if (!Files.exists(definitionPath)) {
                errors.add("Definition file does not exist: " + definitionPath);
                return new ValidationResult(errors, warnings, false);
            }

            String content = Files.readString(definitionPath);
            JsonNode root = YAML_MAPPER.readTree(content);

            // Validate required top-level fields
            validateRequiredFields(root, errors);

            // Validate field formats and values
            validateFieldValues(root, errors, warnings);

            // Validate structure consistency
            validateStructure(root, errors, warnings);

            // Cross-reference validation with capabilities taxonomy if provided
            if (capabilitiesPath != null && Files.exists(capabilitiesPath)) {
                validateCapabilities(root, capabilitiesPath, errors, warnings);
            }

            // Business rule validation
            validateBusinessRules(root, errors, warnings);

        } catch (Exception e) {
            errors.add("Failed to parse definition file: " + e.getMessage());
        }

        return new ValidationResult(errors, warnings, errors.isEmpty());
    }

    /**
     * Validates agent capabilities against the capabilities taxonomy.
     */
    private static void validateCapabilities(JsonNode root, Path capabilitiesPath, 
                                               List<String> errors, List<String> warnings) {
        try {
            JsonNode capabilitiesTaxonomy = YAML_MAPPER.readTree(capabilitiesPath.toFile());
            Set<String> validCapabilities = new HashSet<>();

            // Extract valid capabilities from taxonomy
            if (capabilitiesTaxonomy.has("capabilities") && capabilitiesTaxonomy.get("capabilities").isObject()) {
                capabilitiesTaxonomy.get("capabilities").fieldNames().forEachRemaining(validCapabilities::add);
            }

            // Validate agent capabilities
            if (root.has("capabilities")) {
                JsonNode agentCapabilities = root.get("capabilities");
                if (agentCapabilities.isArray()) {
                    for (JsonNode capability : agentCapabilities) {
                        String capabilityName = capability.asText();
                        if (!validCapabilities.contains(capabilityName)) {
                            warnings.add("Capability '" + capabilityName + "' is not defined in the capabilities taxonomy");
                        }
                    }
                }
            }

            // Validate tool endpoints
            if (root.has("tools")) {
                JsonNode tools = root.get("tools");
                if (tools.isArray()) {
                    for (JsonNode tool : tools) {
                        if (tool.has("endpoint")) {
                            String endpoint = tool.get("endpoint").asText();
                            if (!endpoint.startsWith("/")) {
                                errors.add("Tool endpoint must start with '/': " + endpoint);
                            }
                            if (endpoint.contains("//")) {
                                errors.add("Tool endpoint must not contain '//' (double slash): " + endpoint);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            warnings.add("Failed to validate capabilities against taxonomy: " + e.getMessage());
        }
    }

    /**
     * Validates business rules specific to agent definitions.
     */
    private static void validateBusinessRules(JsonNode root, List<String> errors, List<String> warnings) {
        // Rule: High criticality agents must have contact information
        if (root.has("identity") && root.get("identity").has("criticality")) {
            String criticality = root.get("identity").get("criticality").asText();
            if ("high".equals(criticality) || "critical".equals(criticality)) {
                if (!root.has("owners") || root.get("owners").isEmpty()) {
                    errors.add("High/critical criticality agents must have owners defined");
                }
            }
        }

        // Rule: Autonomous agents must have memory defined
        if (root.has("identity") && root.get("identity").has("autonomyLevel")) {
            String autonomyLevel = root.get("identity").get("autonomyLevel").asText();
            if ("autonomous".equals(autonomyLevel) || "fully-autonomous".equals(autonomyLevel)) {
                if (!root.has("memory")) {
                    warnings.add("Autonomous agents should have memory configuration defined");
                }
            }
        }

        // Rule: External-state agents must have tools defined
        if (root.has("identity") && root.get("identity").has("stateMutability")) {
            String stateMutability = root.get("identity").get("stateMutability").asText();
            if ("external-state".equals(stateMutability)) {
                if (!root.has("tools") || root.get("tools").isEmpty()) {
                    errors.add("External-state agents must have tools defined");
                }
            }
        }

        // Rule: Deprecated agents should have a deprecation notice in description
        if (root.has("status") && "deprecated".equals(root.get("status").asText())) {
            if (!root.has("description") || !root.get("description").asText().toLowerCase().contains("deprecated")) {
                warnings.add("Deprecated agents should mention deprecation in description");
            }
        }

        // Rule: Experimental agents should have experimental in metadata tags
        if (root.has("status") && "experimental".equals(root.get("status").asText())) {
            if (root.has("metadata") && root.get("metadata").has("tags")) {
                JsonNode tags = root.get("metadata").get("tags");
                boolean hasExperimentalTag = false;
                if (tags.isArray()) {
                    for (JsonNode tag : tags) {
                        if ("experimental".equalsIgnoreCase(tag.asText())) {
                            hasExperimentalTag = true;
                            break;
                        }
                    }
                }
                if (!hasExperimentalTag) {
                    warnings.add("Experimental agents should have 'experimental' tag in metadata");
                }
            }
        }
    }

    private static void validateRequiredFields(JsonNode root, List<String> errors) {
        String[] requiredFields = {"id", "namespace", "name", "version", "status"};
        for (String field : requiredFields) {
            if (!root.has(field) || root.get(field).isNull() || root.get(field).asText().isBlank()) {
                errors.add("Required field '" + field + "' is missing or empty");
            }
        }
    }

    private static void validateFieldValues(JsonNode root, List<String> errors, List<String> warnings) {
        // Validate id format
        if (root.has("id")) {
            String id = root.get("id").asText();
            if (!id.matches("^[a-z0-9-]+$")) {
                errors.add("Field 'id' must be lowercase alphanumeric with hyphens: " + id);
            }
        }

        // Validate version format (semantic versioning)
        if (root.has("version")) {
            String version = root.get("version").asText();
            if (!version.matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")) {
                errors.add("Field 'version' must follow semantic versioning (e.g., 1.0.0): " + version);
            }
        }

        // Validate status
        if (root.has("status")) {
            String status = root.get("status").asText();
            if (!VALID_STATUS.contains(status)) {
                errors.add("Field 'status' has invalid value: " + status + ". Valid values: " + VALID_STATUS);
            }
        }

        // Validate identity section
        if (root.has("identity")) {
            JsonNode identity = root.get("identity");
            validateEnumField(identity, "agentType", VALID_AGENT_TYPES, errors);
            validateEnumField(identity, "subtype", VALID_SUBTYPES, errors);
            validateEnumField(identity, "determinismGuarantee", VALID_DETERMINISM_GUARANTEES, errors);
            validateEnumField(identity, "stateMutability", VALID_STATE_MUTABILITY, errors);
            validateEnumField(identity, "failureMode", VALID_FAILURE_MODES, errors);
            validateEnumField(identity, "criticality", VALID_CRITICALITY_LEVELS, errors);
            validateEnumField(identity, "autonomyLevel", VALID_AUTONOMY_LEVELS, errors);
        }

        // Validate memory section
        if (root.has("memory")) {
            JsonNode memory = root.get("memory");
            validateEnumField(memory, "type", VALID_MEMORY_TYPES, errors);
            validateEnumField(memory, "retention", VALID_MEMORY_RETENTION, errors);
        }

        // Validate tools
        if (root.has("tools")) {
            JsonNode tools = root.get("tools");
            if (tools.isArray()) {
                for (JsonNode tool : tools) {
                    validateEnumField(tool, "type", VALID_TOOL_TYPES, errors);
                    if (!tool.has("name") || tool.get("name").asText().isBlank()) {
                        errors.add("Tool must have a non-empty 'name' field");
                    }
                }
            }
        }

        // Validate resources
        if (root.has("resources")) {
            JsonNode resources = root.get("resources");
            validateResourceField(resources, "memory", errors);
            validateResourceField(resources, "cpu", errors);
            validateResourceField(resources, "timeout", errors);
        }
    }

    private static void validateEnumField(JsonNode node, String fieldName, Set<String> validValues, List<String> errors) {
        if (!node.has(fieldName)) {
            return; // Field is optional
        }
        String value = node.get(fieldName).asText();
        if (!validValues.contains(value)) {
            errors.add("Field '" + fieldName + "' has invalid value: " + value + ". Valid values: " + validValues);
        }
    }

    private static void validateResourceField(JsonNode node, String fieldName, List<String> errors) {
        if (!node.has(fieldName)) {
            return;
        }
        String value = node.get(fieldName).asText();
        if (fieldName.equals("memory") && !value.matches("^\\d+(GB|MB|KB)?$")) {
            errors.add("Field 'resources." + fieldName + "' must match format (e.g., 2GB, 512MB): " + value);
        }
        if (fieldName.equals("cpu") && !value.matches("^\\d+(\\.\\d+)?$")) {
            errors.add("Field 'resources." + fieldName + "' must be a number: " + value);
        }
        if (fieldName.equals("timeout") && !value.matches("^\\d+(ms|s|m|h)?$")) {
            errors.add("Field 'resources." + fieldName + "' must match format (e.g., 30s, 500ms): " + value);
        }
    }

    private static void validateStructure(JsonNode root, List<String> errors, List<String> warnings) {
        // Validate that capabilities is a list
        if (root.has("capabilities")) {
            JsonNode capabilities = root.get("capabilities");
            if (!capabilities.isArray()) {
                errors.add("Field 'capabilities' must be an array");
            } else {
                Set<String> capabilityNames = new HashSet<>();
                for (JsonNode capability : capabilities) {
                    String name = capability.asText();
                    if (capabilityNames.contains(name)) {
                        warnings.add("Duplicate capability: " + name);
                    }
                    capabilityNames.add(name);
                }
            }
        }

        // Validate that interfaces has inputs and outputs
        if (root.has("interfaces")) {
            JsonNode interfaces = root.get("interfaces");
            if (interfaces.has("inputs") && !interfaces.get("inputs").isArray()) {
                errors.add("Field 'interfaces.inputs' must be an array");
            }
            if (interfaces.has("outputs") && !interfaces.get("outputs").isArray()) {
                errors.add("Field 'interfaces.outputs' must be an array");
            }
        }

        // Validate owners section
        if (root.has("owners")) {
            JsonNode owners = root.get("owners");
            if (!owners.isArray()) {
                errors.add("Field 'owners' must be an array");
            } else {
                for (JsonNode owner : owners) {
                    if (!owner.has("team") || owner.get("team").asText().isBlank()) {
                        errors.add("Owner must have a non-empty 'team' field");
                    }
                    if (!owner.has("contact") || owner.get("contact").asText().isBlank()) {
                        errors.add("Owner must have a non-empty 'contact' field");
                    }
                    if (owner.has("contact") && !owner.get("contact").asText().contains("@")) {
                        errors.add("Owner 'contact' must be a valid email address");
                    }
                }
            }
        }

        // Validate metadata
        if (root.has("metadata")) {
            JsonNode metadata = root.get("metadata");
            if (metadata.has("level")) {
                int level = metadata.get("level").asInt();
                if (level < 1 || level > 5) {
                    errors.add("Field 'metadata.level' must be between 1 and 5: " + level);
                }
            }
        }
    }

    /**
     * Validation result containing errors, warnings, and overall validity status.
     */
    public record ValidationResult(List<String> errors, List<String> warnings, boolean isValid) {
        /**
         * Returns true if there are no errors.
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Returns true if there are warnings.
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        /**
         * Returns a formatted error message.
         */
        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return "No errors";
            }
            return String.join("\n", errors);
        }

        /**
         * Returns a formatted warning message.
         */
        public String getWarningMessage() {
            if (warnings.isEmpty()) {
                return "No warnings";
            }
            return String.join("\n", warnings);
        }
    }
}
