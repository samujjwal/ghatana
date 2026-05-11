/**
 * Component Registry Validation Service
 * 
 * Validates component registry entries before persistence.
 * Ensures component definitions meet required standards.
 * 
 * @doc.type interface
 * @doc.purpose Component registry validation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.registry;

import com.ghatana.yappc.api.ComponentRegistryContract;

/**
 * Service interface for validating component registry entries.
 */
public interface ComponentRegistryValidationService {

    /**
     * Validates a component registry entry.
     * 
     * @param entry The registry entry to validate
     * @return ComponentValidationResult containing validation status and any errors
     */
    ComponentRegistryContract.ComponentValidationResult validateEntry(
            ComponentRegistryContract.RegistryEntry entry
    );

    /**
     * Validates a component definition.
     * 
     * @param definition The component definition to validate
     * @return ComponentValidationResult containing validation status and any errors
     */
    ComponentRegistryContract.ComponentValidationResult validateDefinition(
            ComponentRegistryContract.ComponentDefinition definition
    );

    /**
     * Validates component properties against their definitions.
     * 
     * @param properties The properties to validate
     * @param propertyDefinitions The property definitions to validate against
     * @return ComponentValidationResult containing validation status and any errors
     */
    ComponentRegistryContract.ComponentValidationResult validateProperties(
            java.util.Map<String, Object> properties,
            java.util.Map<String, ComponentRegistryContract.PropertyDefinition> propertyDefinitions
    );
}
