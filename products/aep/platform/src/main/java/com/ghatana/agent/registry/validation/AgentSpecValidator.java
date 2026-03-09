package com.ghatana.agent.registry.validation;

import com.ghatana.contracts.agent.v1.AgentSpecProto;
import com.ghatana.contracts.agent.v1.InputProto;
import com.ghatana.contracts.agent.v1.OutputProto;
import com.ghatana.contracts.agent.v1.RuntimeProto;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for agent specifications.
 * Ensures that agent specs meet the required criteria for registration and updates.
 *
 * @doc.type class
 * @doc.purpose Validates agent specification fields, schemas, and configuration before registration or update
 * @doc.layer product
 * @doc.pattern Service
 */
@Slf4j
public class AgentSpecValidator {

    /**
     * Validates an agent specification.
     *
     * @param agentSpec The agent specification to validate
     * @return List of validation errors, empty if valid
     */
    public List<String> validate(AgentSpecProto agentSpec) {
        List<String> errors = new ArrayList<>();

        // Validate required fields
        validateRequiredFields(agentSpec, errors);
        
        // Validate input schema
        validateInputSchema(agentSpec, errors);
        
        // Validate output schema
        validateOutputSchema(agentSpec, errors);
        
        // Validate config schema
        validateConfigSchema(agentSpec, errors);
        

        return errors;
    }

    private void validateRequiredFields(AgentSpecProto agentSpec, List<String> errors) {
        if (agentSpec == null) {
            errors.add("AgentSpec must not be null");
            return;
        }
        if (!agentSpec.hasRuntime()) {
            errors.add("Runtime configuration is required");
        } else {
            RuntimeProto rt = agentSpec.getRuntime();
            if (rt.getType() == null || rt.getType().isEmpty()) {
                errors.add("Runtime type is required");
            }
        }
        if (agentSpec.getInputsCount() == 0) {
            errors.add("At least one input is required");
        }
        if (agentSpec.getOutputsCount() == 0) {
            errors.add("At least one output is required");
        }
    }

    private void validateInputSchema(AgentSpecProto agentSpec, List<String> errors) {
        if (agentSpec.getInputsCount() == 0) {
            return; // Already checked in validateRequiredFields
        }

        // Valid input types
        Set<String> validTypes = Set.of("topic", "http", "grpc");
        
        // Check for duplicate input IDs
        Set<String> inputIds = new HashSet<>();
        int inputIndex = 1;
        for (InputProto input : agentSpec.getInputsList()) {
            if (input.getId() == null) {
                errors.add(String.format("Input #%d: ID is required", inputIndex));
                inputIndex++;
                continue;
            } else if (input.getId().isEmpty() || input.getId().trim().isEmpty()) {
                errors.add(String.format("Input #%d: ID cannot be empty", inputIndex));
                inputIndex++;
                continue;
            }
            
            if (!inputIds.add(input.getId())) {
                errors.add(String.format("Duplicate input ID: %s", input.getId()));
            }
            
            if (input.getType() == null || input.getType().trim().isEmpty()) {
                errors.add(String.format("Input '%s': Type is required", input.getId()));
            } else if (!validTypes.contains(input.getType().toLowerCase())) {
                errors.add(String.format("Input '%s': Invalid type '%s'. Must be one of: [topic, http, grpc]", input.getId(), input.getType()));
            }
            
            // Validate event types if specified
            if (input.getEventTypesCount() == 0) {
                errors.add(String.format("Input '%s': At least one event type is required", input.getId()));
            } else {
                // Validate event type format
                for (String eventType : input.getEventTypesList()) {
                    if (!isValidEventTypeFormat(eventType)) {
                        errors.add(String.format("Input '%s': Invalid event type format: %s. Expected format: 'domain.v1.Type'", input.getId(), eventType));
                    }
                }
            }
            
            inputIndex++;
        }
    }

    private void validateOutputSchema(AgentSpecProto agentSpec, List<String> errors) {
        if (agentSpec.getOutputsCount() == 0) {
            return; // Already checked in validateRequiredFields
        }

        // Valid output types
        Set<String> validTypes = Set.of("topic", "http", "grpc");
        Set<String> outputNames = new HashSet<>();
        int outputIndex = 1;
        
        for (OutputProto output : agentSpec.getOutputsList()) {
            if (output.getName() == null) {
                errors.add(String.format("Output #%d: Name is required", outputIndex));
            } else if (output.getName().isEmpty() || output.getName().trim().isEmpty()) {
                errors.add(String.format("Output #%d: Name cannot be empty", outputIndex));
            } else {
                if (!outputNames.add(output.getName())) {
                    errors.add(String.format("Duplicate output name: %s", output.getName()));
                }
            }
            
            if (output.getType() == null || output.getType().trim().isEmpty()) {
                errors.add(String.format("Output '%s': Type is required", output.getName()));
            } else if (!validTypes.contains(output.getType().toLowerCase())) {
                errors.add(String.format("Output '%s': Invalid type '%s'. Must be one of: [topic, http, grpc]", output.getName(), output.getType()));
            }
            
            outputIndex++;
        }
    }

    private void validateConfigSchema(AgentSpecProto agentSpec, List<String> errors) {
        // Config schema is optional, but if present, validate it
        // Runtime validation already handled in validateRequiredFields
        if (agentSpec.hasRuntime()) {
            RuntimeProto runtime = agentSpec.getRuntime();
            // Only validate non-empty runtime types as empty ones are handled in validateRequiredFields
            if (runtime.getType() != null && !runtime.getType().isEmpty()) {
                validateRuntimeType(runtime.getType(), errors);
            }
        }
    }

    private void validateRuntimeType(String runtimeType, List<String> errors) {
        Set<String> supportedTypes = Set.of("java", "python", "nodejs");
        if (runtimeType == null || runtimeType.trim().isEmpty() || !supportedTypes.contains(runtimeType)) {
            errors.add(String.format("Unsupported runtime type: %s. Supported types: [java, python, nodejs]", 
                runtimeType == null ? "null" : runtimeType));
        }
    }

    private boolean isValidEventTypeFormat(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            return false;
        }
        // Expected format: domain.v1.Type (at least 3 parts separated by dots)
        String[] parts = eventType.split("\\.");
        return parts.length >= 3 && parts[1].startsWith("v") && Character.isDigit(parts[1].charAt(1));
    }

    // validateVersion removed: version belongs to manifest.metadata in new schema

}
