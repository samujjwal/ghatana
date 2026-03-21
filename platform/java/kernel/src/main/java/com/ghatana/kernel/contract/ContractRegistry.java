package com.ghatana.kernel.contract;

import com.ghatana.kernel.descriptor.KernelCapability;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing kernel contracts and their validation status.
 *
 * <p>Per DEVELOPER_PLATFORM_CONTRACT_MODEL.md, this registry maintains the
 * canonical contract definitions and provides lookup services for contract
 * validation and compliance checking.</p>
 *
 * @doc.type class
 * @doc.purpose Registry for managing kernel contracts and validation
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class ContractRegistry {

    private final Map<String, ContractValidator.ModuleContract> moduleContracts = new ConcurrentHashMap<>();
    private final Map<String, ContractValidator.ApiContract> apiContracts = new ConcurrentHashMap<>();
    private final Map<String, ContractValidator.SchemaContract> schemaContracts = new ConcurrentHashMap<>();
    private final Map<String, KernelCapability> registeredCapabilities = new ConcurrentHashMap<>();
    private final ContractValidator validator;

    /**
     * Creates a new contract registry.
     *
     * @param validator the contract validator to use
     */
    public ContractRegistry(ContractValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    /**
     * Registers a module contract.
     *
     * @param contract the module contract to register
     * @return true if registration was successful
     */
    public boolean registerModuleContract(ContractValidator.ModuleContract contract) {
        Objects.requireNonNull(contract);
        
        // Validate the contract before registration
        ContractValidator.ValidationResult result = validator.validateModule(contract);
        if (!result.valid()) {
            throw new IllegalArgumentException("Module contract validation failed: " + result.errors());
        }

        moduleContracts.put(contract.moduleId(), contract);
        return true;
    }

    /**
     * Registers an API contract.
     *
     * @param contract the API contract to register
     * @return true if registration was successful
     */
    public boolean registerApiContract(ContractValidator.ApiContract contract) {
        Objects.requireNonNull(contract);
        
        // Validate the contract before registration
        ContractValidator.ValidationResult result = validator.validateApiContract(contract);
        if (!result.valid()) {
            throw new IllegalArgumentException("API contract validation failed: " + result.errors());
        }

        apiContracts.put(contract.apiId(), contract);
        return true;
    }

    /**
     * Registers a schema contract.
     *
     * @param contract the schema contract to register
     * @return true if registration was successful
     */
    public boolean registerSchemaContract(ContractValidator.SchemaContract contract) {
        Objects.requireNonNull(contract);
        
        // Validate the contract before registration
        ContractValidator.ValidationResult result = validator.validateSchemaContract(contract);
        if (!result.valid()) {
            throw new IllegalArgumentException("Schema contract validation failed: " + result.errors());
        }

        schemaContracts.put(contract.schemaId(), contract);
        return true;
    }

    /**
     * Registers a kernel capability.
     *
     * @param capability the capability to register
     * @return true if registration was successful
     */
    public boolean registerCapability(KernelCapability capability) {
        Objects.requireNonNull(capability);
        registeredCapabilities.put(capability.getCapabilityId(), capability);
        return true;
    }

    /**
     * Gets a module contract by ID.
     *
     * @param moduleId the module ID
     * @return the module contract, or null if not found
     */
    public ContractValidator.ModuleContract getModuleContract(String moduleId) {
        return moduleContracts.get(moduleId);
    }

    /**
     * Gets an API contract by ID.
     *
     * @param apiId the API ID
     * @return the API contract, or null if not found
     */
    public ContractValidator.ApiContract getApiContract(String apiId) {
        return apiContracts.get(apiId);
    }

    /**
     * Gets a schema contract by ID.
     *
     * @param schemaId the schema ID
     * @return the schema contract, or null if not found
     */
    public ContractValidator.SchemaContract getSchemaContract(String schemaId) {
        return schemaContracts.get(schemaId);
    }

    /**
     * Gets a registered capability by ID.
     *
     * @param capabilityId the capability ID
     * @return the capability, or null if not found
     */
    public KernelCapability getCapability(String capabilityId) {
        return registeredCapabilities.get(capabilityId);
    }

    /**
     * Lists all registered module contracts.
     *
     * @return immutable set of module contracts
     */
    public Set<ContractValidator.ModuleContract> listModuleContracts() {
        return Set.copyOf(moduleContracts.values());
    }

    /**
     * Lists all registered API contracts.
     *
     * @return immutable set of API contracts
     */
    public Set<ContractValidator.ApiContract> listApiContracts() {
        return Set.copyOf(apiContracts.values());
    }

    /**
     * Lists all registered schema contracts.
     *
     * @return immutable set of schema contracts
     */
    public Set<ContractValidator.SchemaContract> listSchemaContracts() {
        return Set.copyOf(schemaContracts.values());
    }

    /**
     * Lists all registered capabilities.
     *
     * @return immutable set of capabilities
     */
    public Set<KernelCapability> listCapabilities() {
        return Set.copyOf(registeredCapabilities.values());
    }

    /**
     * Validates a module contract against the registry.
     *
     * @param contract the contract to validate
     * @return validation result
     */
    public ContractValidator.ValidationResult validateModule(ContractValidator.ModuleContract contract) {
        return validator.validateModule(contract);
    }

    /**
     * Validates an API contract against the registry.
     *
     * @param contract the contract to validate
     * @return validation result
     */
    public ContractValidator.ValidationResult validateApi(ContractValidator.ApiContract contract) {
        return validator.validateApiContract(contract);
    }

    /**
     * Validates a schema contract against the registry.
     *
     * @param contract the contract to validate
     * @return validation result
     */
    public ContractValidator.ValidationResult validateSchema(ContractValidator.SchemaContract contract) {
        return validator.validateSchemaContract(contract);
    }

    /**
     * Checks if a module contract exists in the registry.
     *
     * @param moduleId the module ID to check
     * @return true if the contract exists
     */
    public boolean hasModuleContract(String moduleId) {
        return moduleContracts.containsKey(moduleId);
    }

    /**
     * Checks if an API contract exists in the registry.
     *
     * @param apiId the API ID to check
     * @return true if the contract exists
     */
    public boolean hasApiContract(String apiId) {
        return apiContracts.containsKey(apiId);
    }

    /**
     * Checks if a schema contract exists in the registry.
     *
     * @param schemaId the schema ID to check
     * @return true if the contract exists
     */
    public boolean hasSchemaContract(String schemaId) {
        return schemaContracts.containsKey(schemaId);
    }

    /**
     * Gets registry statistics.
     *
     * @return statistics about registered contracts and capabilities
     */
    public RegistryStatistics getStatistics() {
        return new RegistryStatistics(
                moduleContracts.size(),
                apiContracts.size(),
                schemaContracts.size(),
                registeredCapabilities.size()
        );
    }

    /**
     * Registry statistics.
     */
    public record RegistryStatistics(
            int moduleContracts,
            int apiContracts,
            int schemaContracts,
            int capabilities
    ) {}
}
