package com.ghatana.agent.registry.util;

import com.ghatana.contracts.agent.v1.AgentSpecProto;

/**
 * Utility class for common validation tasks in the Agent Registry module.
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Validates that the specified string is not blank.
     *
     * @param value The string to validate
     * @param fieldName The name of the field being validated (used in error messages)
     * @throws StatusRuntimeException with INVALID_ARGUMENT status if validation fails
     */
    public static void validateNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    /**
     * Validates that the specified object is not null.
     *
     * @param value The object to validate
     * @param fieldName The name of the field being validated (used in error messages)
     * @throws StatusRuntimeException with INVALID_ARGUMENT status if validation fails
     */
    public static void validateNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    /**
     * Validates an agent specification.
     *
     * @param agentSpec The agent specification to validate
     * @throws StatusRuntimeException with INVALID_ARGUMENT status if validation fails
     */
    public static void validateAgentSpec(AgentSpecProto agentSpec) {
        // Minimal validation aligned to current AgentSpecProto (manifest.spec) which does not carry
        // top-level name/version. Ensure runtime block exists if required in your domain.
        validateNotNull(agentSpec, "Agent specification");
        if (!agentSpec.hasRuntime()) {
            throw new IllegalArgumentException("Runtime configuration is required");
        }
    }

    /**
     * Validates that the specified ID is a valid UUID.
     *
     * @param id The ID to validate
     * @param fieldName The name of the field being validated (used in error messages)
     * @throws StatusRuntimeException with INVALID_ARGUMENT status if validation fails
     */
    public static void validateUuid(String id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        try {
            java.util.UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID");
        }
    }
}
