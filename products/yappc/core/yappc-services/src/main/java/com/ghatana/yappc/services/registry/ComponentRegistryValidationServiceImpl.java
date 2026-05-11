/**
 * Component Registry Validation Service Implementation
 * 
 * Production-grade implementation of component registry validation service.
 * Validates component registry entries, definitions, and properties.
 * 
 * @doc.type class
 * @doc.purpose Component registry validation implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.registry;

import com.ghatana.yappc.api.ComponentRegistryContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Production-grade implementation of component registry validation service.
 */
public final class ComponentRegistryValidationServiceImpl implements ComponentRegistryValidationService {

    private static final Logger log = LoggerFactory.getLogger(ComponentRegistryValidationServiceImpl.class);

    @Override
    public ComponentRegistryContract.ComponentValidationResult validateEntry(
            ComponentRegistryContract.RegistryEntry entry
    ) {
        log.info("Validating registry entry: componentId={}", entry.componentId());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate required fields
        if (entry.componentId() == null || entry.componentId().isBlank()) {
            errors.add("Component ID is required");
        }
        if (entry.componentName() == null || entry.componentName().isBlank()) {
            errors.add("Component name is required");
        }
        if (entry.componentVersion() == null || entry.componentVersion().isBlank()) {
            errors.add("Component version is required");
        }
        if (entry.registryName() == null || entry.registryName().isBlank()) {
            errors.add("Registry name is required");
        }

        // Validate version format
        if (entry.componentVersion() != null && !isValidVersion(entry.componentVersion())) {
            errors.add("Component version must follow semantic versioning (e.g., 1.0.0)");
        }

        // Validate metadata
        if (entry.metadata() == null) {
            warnings.add("Component metadata is missing");
        } else {
            if (entry.metadata().displayName() == null || entry.metadata().displayName().isBlank()) {
                warnings.add("Component display name is missing");
            }
            if (entry.metadata().category() == null || entry.metadata().category().isBlank()) {
                warnings.add("Component category is missing");
            }
        }

        // Validate definition
        if (entry.definition() == null) {
            errors.add("Component definition is required");
        } else {
            ComponentRegistryContract.ComponentValidationResult defResult = validateDefinition(entry.definition());
            if (!defResult.isValid()) {
                errors.addAll(defResult.errors());
            }
            warnings.addAll(defResult.warnings());
        }

        // Validate constraints
        if (entry.constraints() == null) {
            warnings.add("Component constraints are missing");
        }

        boolean isValid = errors.isEmpty();
        if (isValid) {
            log.info("Registry entry validation passed: componentId={}", entry.componentId());
        } else {
            log.warn("Registry entry validation failed: componentId={}, errors={}", entry.componentId(), errors);
        }

        return new ComponentRegistryContract.ComponentValidationResult(isValid, errors, warnings, Map.of());
    }

    @Override
    public ComponentRegistryContract.ComponentValidationResult validateDefinition(
            ComponentRegistryContract.ComponentDefinition definition
    ) {
        log.debug("Validating component definition: componentType={}", definition.componentType());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate required fields
        if (definition.componentType() == null || definition.componentType().isBlank()) {
            errors.add("Component type is required");
        }

        // Validate properties
        if (definition.properties() == null) {
            warnings.add("Component properties are missing");
        } else {
            for (Map.Entry<String, ComponentRegistryContract.PropertyDefinition> entry : definition.properties().entrySet()) {
                if (entry.getValue() == null) {
                    errors.add(String.format("Property definition is null for: %s", entry.getKey()));
                } else {
                    if (entry.getValue().name() == null || entry.getValue().name().isBlank()) {
                        errors.add(String.format("Property name is required for: %s", entry.getKey()));
                    }
                    if (entry.getValue().type() == null) {
                        errors.add(String.format("Property type is required for: %s", entry.getKey()));
                    }
                }
            }
        }

        // Validate slots
        if (definition.slots() == null) {
            warnings.add("Component slots are missing");
        } else {
            for (ComponentRegistryContract.SlotDefinition slot : definition.slots()) {
                if (slot.name() == null || slot.name().isBlank()) {
                    errors.add("Slot name is required");
                }
                if (slot.slotType() == null || slot.slotType().isBlank()) {
                    errors.add("Slot type is required");
                }
                if (slot.minItems() < 0) {
                    errors.add("Slot minItems cannot be negative");
                }
                if (slot.maxItems() < slot.minItems()) {
                    errors.add("Slot maxItems must be >= minItems");
                }
            }
        }

        // Validate events
        if (definition.events() == null) {
            warnings.add("Component events are missing");
        } else {
            for (ComponentRegistryContract.EventDefinition event : definition.events()) {
                if (event.name() == null || event.name().isBlank()) {
                    errors.add("Event name is required");
                }
                if (event.eventType() == null || event.eventType().isBlank()) {
                    errors.add("Event type is required");
                }
            }
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Component definition validation failed: componentType={}, errors={}", 
                    definition.componentType(), errors);
        }

        return new ComponentRegistryContract.ComponentValidationResult(isValid, errors, warnings, Map.of());
    }

    @Override
    public ComponentRegistryContract.ComponentValidationResult validateProperties(
            Map<String, Object> properties,
            Map<String, ComponentRegistryContract.PropertyDefinition> propertyDefinitions
    ) {
        log.debug("Validating properties against definitions: propertyCount={}", properties.size());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate all provided properties against their definitions
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            Object propertyValue = entry.getValue();
            ComponentRegistryContract.PropertyDefinition definition = propertyDefinitions.get(propertyName);

            if (definition == null) {
                warnings.add(String.format("Property '%s' has no definition", propertyName));
                continue;
            }

            // Validate required properties
            if (definition.isRequired() && propertyValue == null) {
                errors.add(String.format("Required property '%s' is missing", propertyName));
            }

            // Validate type
            if (propertyValue != null) {
                if (!isValidType(propertyValue, definition.type())) {
                    errors.add(String.format("Property '%s' has invalid type: expected %s, got %s",
                            propertyName, definition.type(), propertyValue.getClass().getSimpleName()));
                }
            }

            // Validate constraints
            if (definition.constraints() != null) {
                for (ComponentRegistryContract.PropertyConstraint constraint : definition.constraints()) {
                    String constraintError = validateConstraint(propertyValue, constraint);
                    if (constraintError != null) {
                        errors.add(String.format("Property '%s' constraint failed: %s", propertyName, constraintError));
                    }
                }
            }
        }

        // Check for missing required properties
        for (Map.Entry<String, ComponentRegistryContract.PropertyDefinition> entry : propertyDefinitions.entrySet()) {
            if (entry.getValue().isRequired() && !properties.containsKey(entry.getKey())) {
                errors.add(String.format("Required property '%s' is missing from provided properties", entry.getKey()));
            }
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Property validation failed: errors={}", errors);
        }

        return new ComponentRegistryContract.ComponentValidationResult(isValid, errors, warnings, Map.of());
    }

    private boolean isValidVersion(String version) {
        return version.matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$");
    }

    private boolean isValidType(Object value, ComponentRegistryContract.PropertyDefinition.PropertyType expectedType) {
        return switch (expectedType) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof java.util.Collection;
            case OBJECT -> value instanceof Map;
            case ENUM -> value instanceof String; // Simplified enum validation
            case CUSTOM -> true; // Custom types are not validated at this level
        };
    }

    private String validateConstraint(Object value, ComponentRegistryContract.PropertyConstraint constraint) {
        return switch (constraint.constraintType()) {
            case "min" -> {
                if (value instanceof Number num && num.doubleValue() < ((Number) constraint.value()).doubleValue()) {
                    yield constraint.errorMessage() != null ? constraint.errorMessage() : "Value is below minimum";
                }
                yield null;
            }
            case "max" -> {
                if (value instanceof Number num && num.doubleValue() > ((Number) constraint.value()).doubleValue()) {
                    yield constraint.errorMessage() != null ? constraint.errorMessage() : "Value is above maximum";
                }
                yield null;
            }
            case "minLength" -> {
                if (value instanceof String str && str.length() < ((Number) constraint.value()).intValue()) {
                    yield constraint.errorMessage() != null ? constraint.errorMessage() : "String is too short";
                }
                yield null;
            }
            case "maxLength" -> {
                if (value instanceof String str && str.length() > ((Number) constraint.value()).intValue()) {
                    yield constraint.errorMessage() != null ? constraint.errorMessage() : "String is too long";
                }
                yield null;
            }
            case "pattern" -> {
                if (value instanceof String str && !str.matches((String) constraint.value())) {
                    yield constraint.errorMessage() != null ? constraint.errorMessage() : "String does not match pattern";
                }
                yield null;
            }
            default -> null;
        };
    }
}
