package com.ghatana.kernel.contract;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;

import java.util.*;

/**
 * Validates kernel contracts and capability implementations.
 *
 * <p>Per DEVELOPER_PLATFORM_CONTRACT_MODEL.md, this validator ensures that
 * implementations comply with the canonical kernel contracts defined in the
 * contract families (experience, service/API, schema/data, analytics, autonomous,
 * packaging).</p>
 *
 * @doc.type interface
 * @doc.purpose Contract validation infrastructure for kernel compliance
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface ContractValidator {

    /**
     * Validates a capability implementation against its contract.
     *
     * @param capability the capability being implemented
     * @param implementation the implementation details
     * @return validation result
     */
    ValidationResult validateCapability(KernelCapability capability, ImplementationDetails implementation);

    /**
     * Validates dependency contracts between capabilities.
     *
     * @param capability the capability with dependencies
     * @param dependencies the declared dependencies
     * @return validation result
     */
    ValidationResult validateDependencies(KernelCapability capability, Set<KernelDependency> dependencies);

    /**
     * Validates that a module complies with kernel contract requirements.
     *
     * @param module the module to validate
     * @return validation result
     */
    ValidationResult validateModule(ModuleContract module);

    /**
     * Validates API contracts against the service/API contract family.
     *
     * @param apiContract the API contract to validate
     * @return validation result
     */
    ValidationResult validateApiContract(ApiContract apiContract);

    /**
     * Validates data schema contracts against the schema/data contract family.
     *
     * @param schemaContract the schema contract to validate
     * @return validation result
     */
    ValidationResult validateSchemaContract(SchemaContract schemaContract);

    // ==================== Result Types ====================

    /**
     * Result of contract validation.
     */
    record ValidationResult(
            boolean valid,
            Set<ValidationError> errors,
            Set<ValidationWarning> warnings,
            Map<String, Object> metadata
    ) {
        /**
         * Creates a successful validation result.
         */
        public static ValidationResult success() {
            return new ValidationResult(true, Set.of(), Set.of(), Map.of());
        }

        /**
         * Creates a successful validation result with warnings.
         */
        public static ValidationResult successWithWarnings(Set<ValidationWarning> warnings) {
            return new ValidationResult(true, Set.of(), warnings, Map.of());
        }

        /**
         * Creates a failed validation result.
         */
        public static ValidationResult failure(Set<ValidationError> errors) {
            return new ValidationResult(false, errors, Set.of(), Map.of());
        }

        /**
         * Creates a failed validation result with warnings.
         */
        public static ValidationResult failure(Set<ValidationError> errors, Set<ValidationWarning> warnings) {
            return new ValidationResult(false, errors, warnings, Map.of());
        }
    }

    /**
     * Validation error indicating contract violation.
     */
    record ValidationError(
            String code,
            String message,
            String severity,
            String location,
            Map<String, Object> context
    ) {
        public ValidationError(String code, String message, String location) {
            this(code, message, "ERROR", location, Map.of());
        }
    }

    /**
     * Validation warning indicating potential contract issues.
     */
    record ValidationWarning(
            String code,
            String message,
            String location,
            Map<String, Object> context
    ) {
        public ValidationWarning(String code, String message, String location) {
            this(code, message, location, Map.of());
        }
    }

    // ==================== Contract Types ====================

    /**
     * Implementation details for capability validation.
     */
    record ImplementationDetails(
            String implementationClass,
            Set<String> providedServices,
            Set<String> requiredServices,
            Map<String, Object> implementationMetadata
    ) {}

    /**
     * Module contract for validation.
     */
    record ModuleContract(
            String moduleId,
            String version,
            Set<KernelCapability> capabilities,
            Set<KernelDependency> dependencies,
            Map<String, Object> moduleMetadata
    ) {}

    /**
     * API contract for service/API validation.
     */
    record ApiContract(
            String apiId,
            String version,
            Set<String> endpoints,
            Map<String, Object> apiMetadata
    ) {}

    /**
     * Schema contract for schema/data validation.
     */
    record SchemaContract(
            String schemaId,
            String version,
            String schemaType,
            Map<String, Object> schemaDefinition,
            Map<String, Object> schemaMetadata
    ) {}
}
