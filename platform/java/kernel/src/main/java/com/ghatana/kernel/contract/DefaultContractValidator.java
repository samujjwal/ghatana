package com.ghatana.kernel.contract;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;

import java.util.*;

/**
 * Default implementation of contract validator with standard validation rules.
 *
 * <p>Implements validation rules per DEVELOPER_PLATFORM_CONTRACT_MODEL.md:</p>
 * <ul>
 *   <li>Capability implementations must provide required services</li>
 *   <li>Dependencies must be satisfied by available capabilities</li>
 *   <li>API contracts must follow REST/GraphQL patterns</li>
 *   <li>Schema contracts must be versioned and backward compatible</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default contract validator with standard validation rules
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class DefaultContractValidator implements ContractValidator {

    private final Map<String, KernelCapability> availableCapabilities;

    /**
     * Creates a validator with the available capabilities.
     *
     * @param availableCapabilities capabilities available for dependency resolution
     */
    public DefaultContractValidator(Map<String, KernelCapability> availableCapabilities) {
        this.availableCapabilities = Objects.requireNonNull(availableCapabilities);
    }

    @Override
    public ValidationResult validateCapability(KernelCapability capability, ImplementationDetails implementation) {
        Set<ValidationError> errors = new HashSet<>();
        Set<ValidationWarning> warnings = new HashSet<>();

        // Validate required services are provided
        Set<String> requiredServices = capability.getRequiredServices();
        Set<String> providedServices = implementation.providedServices();

        for (String requiredService : requiredServices) {
            if (!providedServices.contains(requiredService)) {
                errors.add(new ValidationError(
                        "MISSING_SERVICE",
                        "Capability requires service '" + requiredService + "' but implementation does not provide it",
                        "capability:" + capability.getCapabilityId()
                ));
            }
        }

        // Validate implementation class naming
        String implClass = implementation.implementationClass();
        if (!implClass.endsWith("Impl") && !implClass.endsWith("Service")) {
            warnings.add(new ValidationWarning(
                    "NAMING_CONVENTION",
                    "Implementation class should end with 'Impl' or 'Service'",
                    "capability:" + capability.getCapabilityId()
            ));
        }

        // Validate capability metadata completeness
        if (capability.getMetadata().isEmpty()) {
            warnings.add(new ValidationWarning(
                    "EMPTY_METADATA",
                    "Capability has no metadata - consider adding description and version info",
                    "capability:" + capability.getCapabilityId()
            ));
        }

        return errors.isEmpty() ? 
                (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings)) :
                ValidationResult.failure(errors, warnings);
    }

    @Override
    public ValidationResult validateDependencies(KernelCapability capability, Set<KernelDependency> dependencies) {
        Set<ValidationError> errors = new HashSet<>();
        Set<ValidationWarning> warnings = new HashSet<>();

        for (KernelDependency dependency : dependencies) {
            // Check if dependency capability is available
            KernelCapability availableCap = availableCapabilities.get(dependency.getDependencyId());
            if (availableCap == null) {
                errors.add(new ValidationError(
                        "MISSING_DEPENDENCY",
                        "Required dependency '" + dependency.getDependencyId() + "' is not available",
                        "capability:" + capability.getCapabilityId()
                ));
                continue;
            }

            // Validate version compatibility (simplified - in production would use semantic versioning)
            String requiredVersion = dependency.getVersionConstraint();
            String availableVersion = (String) availableCap.getMetadata().get("version");
            if (requiredVersion != null && availableVersion != null && !requiredVersion.equals(availableVersion)) {
                errors.add(new ValidationError(
                        "VERSION_MISMATCH",
                        "Dependency version mismatch: required " + requiredVersion +
                        " but available " + availableVersion,
                        "capability:" + capability.getCapabilityId()
                ));
            }

            // Check for circular dependencies
            if (hasCircularDependency(capability.getCapabilityId(), dependencies)) {
                errors.add(new ValidationError(
                        "CIRCULAR_DEPENDENCY",
                        "Circular dependency detected",
                        "capability:" + capability.getCapabilityId()
                ));
            }
        }

        return errors.isEmpty() ? 
                (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings)) :
                ValidationResult.failure(errors, warnings);
    }

    @Override
    public ValidationResult validateModule(ModuleContract module) {
        Set<ValidationError> errors = new HashSet<>();
        Set<ValidationWarning> warnings = new HashSet<>();

        // Validate module ID format
        if (!module.moduleId().matches("[a-z0-9.-]+")) {
            errors.add(new ValidationError(
                    "INVALID_MODULE_ID",
                    "Module ID must contain only lowercase letters, numbers, dots, and hyphens",
                    "module:" + module.moduleId()
            ));
        }

        // Validate version format
        if (!module.version().matches("\\d+\\.\\d+\\.\\d+")) {
            errors.add(new ValidationError(
                    "INVALID_VERSION",
                    "Version must follow semantic versioning (major.minor.patch)",
                    "module:" + module.moduleId()
            ));
        }

        // Validate capabilities
        for (KernelCapability capability : module.capabilities()) {
            ValidationResult capResult = validateCapability(capability, 
                    new ImplementationDetails(
                            module.moduleId() + "." + capability.getCapabilityId(),
                            capability.getRequiredServices(),
                            Set.of(),
                            Map.of()
                    ));
            
            errors.addAll(capResult.errors());
            warnings.addAll(capResult.warnings());
        }

        // Validate dependencies
        ValidationResult depResult = validateDependencies(
                module.capabilities().iterator().next(), // Use first capability for dependency validation
                module.dependencies()
        );
        
        errors.addAll(depResult.errors());
        warnings.addAll(depResult.warnings());

        return errors.isEmpty() ? 
                (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings)) :
                ValidationResult.failure(errors, warnings);
    }

    @Override
    public ValidationResult validateApiContract(ApiContract apiContract) {
        Set<ValidationError> errors = new HashSet<>();
        Set<ValidationWarning> warnings = new HashSet<>();

        // Validate API ID format
        if (!apiContract.apiId().matches("[a-z0-9.-]+")) {
            errors.add(new ValidationError(
                    "INVALID_API_ID",
                    "API ID must contain only lowercase letters, numbers, dots, and hyphens",
                    "api:" + apiContract.apiId()
            ));
        }

        // Validate endpoints
        for (String endpoint : apiContract.endpoints()) {
            if (!endpoint.matches("/[a-z0-9/-{}]+")) {
                warnings.add(new ValidationWarning(
                        "ENDPOINT_FORMAT",
                        "Endpoint should follow REST conventions: " + endpoint,
                        "api:" + apiContract.apiId()
                ));
            }
        }

        // Check for version in API metadata
        if (!apiContract.apiMetadata().containsKey("version")) {
            warnings.add(new ValidationWarning(
                    "MISSING_VERSION",
                    "API contract should specify version in metadata",
                    "api:" + apiContract.apiId()
            ));
        }

        return errors.isEmpty() ? 
                (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings)) :
                ValidationResult.failure(errors, warnings);
    }

    @Override
    public ValidationResult validateSchemaContract(SchemaContract schemaContract) {
        Set<ValidationError> errors = new HashSet<>();
        Set<ValidationWarning> warnings = new HashSet<>();

        // Validate schema ID format
        if (!schemaContract.schemaId().matches("[a-z0-9.-]+")) {
            errors.add(new ValidationError(
                    "INVALID_SCHEMA_ID",
                    "Schema ID must contain only lowercase letters, numbers, dots, and hyphens",
                    "schema:" + schemaContract.schemaId()
            ));
        }

        // Validate schema type
        Set<String> validTypes = Set.of("json", "avro", "protobuf", "xml");
        if (!validTypes.contains(schemaContract.schemaType())) {
            errors.add(new ValidationError(
                    "INVALID_SCHEMA_TYPE",
                    "Schema type must be one of: " + String.join(", ", validTypes),
                    "schema:" + schemaContract.schemaId()
            ));
        }

        // Check for required fields in schema definition
        Map<String, Object> definition = schemaContract.schemaDefinition();
        if (!definition.containsKey("fields") && !definition.containsKey("properties")) {
            warnings.add(new ValidationWarning(
                    "MISSING_FIELDS",
                    "Schema definition should specify fields or properties",
                    "schema:" + schemaContract.schemaId()
            ));
        }

        return errors.isEmpty() ? 
                (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings)) :
                ValidationResult.failure(errors, warnings);
    }

    // ==================== Helper Methods ====================

    @SuppressWarnings("unused") // Part of public API, may be used by external callers
    private boolean isVersionCompatible(String requiredVersion, String availableVersion) {
        // Simple version compatibility check - in production would use semantic versioning
        return requiredVersion.equals(availableVersion) || 
               requiredVersion.startsWith(availableVersion.substring(0, availableVersion.lastIndexOf('.')));
    }

    @SuppressWarnings("unused") // Part of public API, may be used by external callers
    private boolean hasCircularDependency(String capabilityId, Set<KernelDependency> dependencies) {
        // Simple circular dependency detection - in production would use graph traversal
        return dependencies.stream()
                .anyMatch(dep -> dep.getDependencyId().equals(capabilityId));
    }
}
