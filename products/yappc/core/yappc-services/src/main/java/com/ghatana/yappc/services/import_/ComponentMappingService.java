/**
 * Component Mapping Service
 * 
 * Maps source components to registry contracts.
 * Ensures imported components are mapped to canonical registry entries.
 * 
 * @doc.type interface
 * @doc.purpose Component mapping
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import com.ghatana.yappc.api.ComponentRegistryContract;

import java.util.Map;

/**
 * Service interface for mapping source components to registry contracts.
 */
public interface ComponentMappingService {

    /**
     * Maps a source component to a registry contract.
     * 
     * @param sourceComponent The source component to map
     * @return ComponentMappingResult containing the mapped registry entry and any unmapped properties
     */
    ComponentMappingResult mapToRegistryContract(SourceComponent sourceComponent);

    /**
     * Batch maps multiple source components to registry contracts.
     * 
     * @param sourceComponents The source components to map
     * @return List of component mapping results
     */
    java.util.List<ComponentMappingResult> batchMapToRegistryContracts(java.util.List<SourceComponent> sourceComponents);

    /**
     * Finds residual islands (unmapped components).
     * 
     * @param sourceComponents The source components to check
     * @return List of residual islands
     */
    java.util.List<ResidualIsland> findResidualIslands(java.util.List<SourceComponent> sourceComponents);
}

/**
 * Source component representation.
 */
record SourceComponent(
    String id,
    String type,
    String name,
    Map<String, Object> properties,
    String sourceType
) {}

/**
 * Component mapping result.
 */
record ComponentMappingResult(
    String sourceComponentId,
    String registryComponentId,
    boolean isMapped,
    java.util.List<String> unmappedProperties,
    java.util.List<String> warnings
) {
    public ComponentMappingResult {
        if (unmappedProperties == null) {
            unmappedProperties = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}

/**
 * Residual island (unmapped component).
 */
record ResidualIsland(
    String componentId,
    String componentType,
    String reason,
    java.util.List<String> suggestedActions
) {
    public ResidualIsland {
        if (suggestedActions == null) {
            suggestedActions = java.util.List.of();
        }
    }
}
