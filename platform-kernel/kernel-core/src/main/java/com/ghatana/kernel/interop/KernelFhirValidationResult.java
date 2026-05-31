package com.ghatana.kernel.interop;

/**
 * Immutable result for Kernel-managed FHIR resource validation.
 *
 * @doc.type record
 * @doc.purpose Carries safe FHIR validation outcome details for product interop plugins
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record KernelFhirValidationResult(
    boolean valid,
    String message,
    String resourceType,
    String safeReasonCode
) {
    public static KernelFhirValidationResult valid(String resourceType) {
        return new KernelFhirValidationResult(
            true,
            "Valid FHIR R4 resource",
            resourceType,
            "FHIR_RESOURCE_VALID"
        );
    }

    public static KernelFhirValidationResult invalid(String resourceType, String safeReasonCode, String message) {
        return new KernelFhirValidationResult(false, message, resourceType, safeReasonCode);
    }
}
