package com.ghatana.kernel.interop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Product-agnostic FHIR/HL7 interop plugin primitives.
 *
 * <p>The Kernel owns reusable FHIR R4 structural checks and safe validation
 * results. Products own domain-specific resource providers, storage, profiles,
 * and exchange adapters.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel-owned reusable FHIR R4 validation primitives for product interop plugins
 * @doc.layer core
 * @doc.pattern Plugin
 */
public final class KernelFhirHl7Plugin {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Set<String> SUPPORTED_R4_RESOURCE_TYPES = Set.of(
        "AllergyIntolerance",
        "CarePlan",
        "Condition",
        "Coverage",
        "DiagnosticReport",
        "DocumentReference",
        "Encounter",
        "Immunization",
        "Medication",
        "MedicationRequest",
        "Observation",
        "Patient",
        "Procedure"
    );
    private static final Map<String, Set<String>> REQUIRED_R4_FIELDS = Map.of(
        "Condition", Set.of("code", "subject"),
        "DiagnosticReport", Set.of("status", "code", "subject"),
        "DocumentReference", Set.of("status", "type", "subject"),
        "Encounter", Set.of("status", "class", "subject"),
        "Observation", Set.of("status", "code", "subject"),
        "Procedure", Set.of("status", "code", "subject")
    );

    public KernelFhirValidationResult validateResource(String resourceType, String resourceJson) {
        if (resourceType == null || resourceType.isBlank()) {
            return KernelFhirValidationResult.invalid(
                resourceType,
                "FHIR_RESOURCE_TYPE_REQUIRED",
                "FHIR resource type is required"
            );
        }
        if (resourceJson == null || resourceJson.isBlank()) {
            return KernelFhirValidationResult.invalid(
                resourceType,
                "FHIR_RESOURCE_JSON_REQUIRED",
                "FHIR resource JSON is required"
            );
        }
        if (!SUPPORTED_R4_RESOURCE_TYPES.contains(resourceType)) {
            return KernelFhirValidationResult.invalid(
                resourceType,
                "FHIR_RESOURCE_TYPE_UNSUPPORTED",
                "FHIR R4 resource type is not supported"
            );
        }

        try {
            JsonNode root = JSON_MAPPER.readTree(resourceJson);
            JsonNode declaredType = root.get("resourceType");
            if (declaredType == null || !resourceType.equals(declaredType.asText())) {
                return KernelFhirValidationResult.invalid(
                    resourceType,
                    "FHIR_RESOURCE_TYPE_MISMATCH",
                    "FHIR resourceType must match the requested resource type"
                );
            }

            if ("Patient".equals(resourceType) && !hasAny(root, Set.of("id", "identifier", "name"))) {
                return KernelFhirValidationResult.invalid(
                    resourceType,
                    "FHIR_REQUIRED_FIELD_MISSING",
                    "FHIR Patient must include id, identifier, or name"
                );
            }

            for (String requiredField : REQUIRED_R4_FIELDS.getOrDefault(resourceType, Set.of())) {
                if (!root.has(requiredField)) {
                    return KernelFhirValidationResult.invalid(
                        resourceType,
                        "FHIR_REQUIRED_FIELD_MISSING",
                        "FHIR resource is missing required field: " + requiredField
                    );
                }
            }

            return KernelFhirValidationResult.valid(resourceType);
        } catch (Exception exception) {
            return KernelFhirValidationResult.invalid(
                resourceType,
                "FHIR_JSON_INVALID",
                "FHIR resource JSON is invalid"
            );
        }
    }

    public Set<String> supportedR4ResourceTypes() {
        return SUPPORTED_R4_RESOURCE_TYPES;
    }

    private boolean hasAny(JsonNode root, Set<String> fieldNames) {
        Objects.requireNonNull(root, "root cannot be null");
        for (String fieldName : fieldNames) {
            if (root.has(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
