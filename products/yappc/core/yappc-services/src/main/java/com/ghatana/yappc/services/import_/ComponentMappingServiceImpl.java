/**
 * Component Mapping Service Implementation
 * 
 * Production-grade implementation of component mapping service.
 * Maps source components to registry contracts and identifies residual islands.
 * 
 * @doc.type class
 * @doc.purpose Component mapping implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Production-grade implementation of component mapping service.
 */
public final class ComponentMappingServiceImpl implements ComponentMappingService {

    private static final Logger log = LoggerFactory.getLogger(ComponentMappingServiceImpl.class);

    private static final Set<String> KNOWN_COMPONENT_TYPES = Set.of(
            "Button",
            "Card",
            "TextField",
            "Checkbox",
            "Radio",
            "Select",
            "Layout",
            "Container",
            "Text",
            "Image",
            "Icon"
    );

    @Override
    public ComponentMappingResult mapToRegistryContract(SourceComponent sourceComponent) {
        log.info("Mapping source component to registry: componentId={}, type={}", 
                sourceComponent.id(), sourceComponent.type());

        List<String> unmappedProperties = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check if component type is known
        if (!KNOWN_COMPONENT_TYPES.contains(sourceComponent.type())) {
            warnings.add(String.format("Unknown component type: %s", sourceComponent.type()));
            return new ComponentMappingResult(
                    sourceComponent.id(),
                    null,
                    false,
                    unmappedProperties,
                    warnings
            );
        }

        // Map to registry component ID
        String registryComponentId = generateRegistryComponentId(sourceComponent);

        // Check for unmapped properties
        for (String propertyName : sourceComponent.properties().keySet()) {
            if (!isKnownProperty(sourceComponent.type(), propertyName)) {
                unmappedProperties.add(propertyName);
            }
        }

        boolean isMapped = unmappedProperties.isEmpty();

        if (isMapped) {
            log.info("Component mapped successfully: componentId={}, registryId={}", 
                    sourceComponent.id(), registryComponentId);
        } else {
            log.warn("Component partially mapped: componentId={}, unmappedProperties={}", 
                    sourceComponent.id(), unmappedProperties);
        }

        return new ComponentMappingResult(
                sourceComponent.id(),
                registryComponentId,
                isMapped,
                unmappedProperties,
                warnings
        );
    }

    @Override
    public List<ComponentMappingResult> batchMapToRegistryContracts(List<SourceComponent> sourceComponents) {
        log.info("Batch mapping components: count={}", sourceComponents.size());

        List<ComponentMappingResult> results = new ArrayList<>();

        for (SourceComponent component : sourceComponents) {
            results.add(mapToRegistryContract(component));
        }

        int mappedCount = (int) results.stream().filter(ComponentMappingResult::isMapped).count();
        log.info("Batch mapping complete: total={}, mapped={}", results.size(), mappedCount);

        return results;
    }

    @Override
    public List<ResidualIsland> findResidualIslands(List<SourceComponent> sourceComponents) {
        log.info("Finding residual islands: count={}", sourceComponents.size());

        List<ResidualIsland> residualIslands = new ArrayList<>();

        for (SourceComponent component : sourceComponents) {
            ComponentMappingResult result = mapToRegistryContract(component);

            if (!result.isMapped() || result.registryComponentId() == null) {
                List<String> suggestedActions = new ArrayList<>();
                suggestedActions.add("Create custom registry entry");
                suggestedActions.add("Map to similar existing component");
                suggestedActions.add("Mark as residual for manual review");

                residualIslands.add(new ResidualIsland(
                        component.id(),
                        component.type(),
                        String.format("Component type '%s' not found in registry", component.type()),
                        suggestedActions
                ));
            }
        }

        log.info("Residual islands found: count={}", residualIslands.size());
        return residualIslands;
    }

    private String generateRegistryComponentId(SourceComponent sourceComponent) {
        // Generate a canonical registry component ID
        return String.format("registry:%s:%s", 
                sourceComponent.type().toLowerCase(), 
                sourceComponent.sourceType().toLowerCase());
    }

    private boolean isKnownProperty(String componentType, String propertyName) {
        // Simplified property check - in production, this would query the registry
        Set<String> knownProperties = switch (componentType) {
            case "Button" -> Set.of("label", "variant", "size", "disabled", "onClick");
            case "Card" -> Set.of("title", "content", "footer", "variant");
            case "TextField" -> Set.of("label", "value", "placeholder", "disabled", "onChange");
            case "Layout" -> Set.of("direction", "spacing", "align", "justify");
            default -> Set.of("id", "className", "style");
        };
        return knownProperties.contains(propertyName);
    }
}
