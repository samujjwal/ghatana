/**
 * Canonical Component Registry Contract
 * 
 * Single source of truth for component registry structure.
 * Defines the canonical schema for component registry that manages UI components
 * used in page artifacts.
 * 
 * @doc.type class
 * @doc.purpose Canonical component registry contract
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical component registry contract.
 * This is the authoritative schema for component registry operations.
 */
public final class ComponentRegistryContract {

    /**
     * Component registry entry.
     */
    public record RegistryEntry(
            String componentId,
            String componentName,
            String componentVersion,
            String registryName,
            ComponentMetadata metadata,
            ComponentDefinition definition,
            ComponentConstraints constraints,
            List<ComponentAlias> aliases,
            Instant createdAt,
            Instant updatedAt,
            boolean isActive
    ) {}

    /**
     * Component metadata.
     */
    public record ComponentMetadata(
            String displayName,
            String description,
            String category,
            Set<String> tags,
            String author,
            String documentationUrl,
            Map<String, String> customProperties
    ) {}

    /**
     * Component definition.
     */
    public record ComponentDefinition(
            String componentType,
            Map<String, PropertyDefinition> properties,
            List<SlotDefinition> slots,
            List<EventDefinition> events,
            Map<String, String> defaultStyles,
            Map<String, Object> themeConfig
    ) {}

    /**
     * Property definition.
     */
    public record PropertyDefinition(
            String name,
            PropertyType type,
            boolean isRequired,
            Object defaultValue,
            List<PropertyConstraint> constraints,
            String description
    ) {
        public enum PropertyType {
            STRING,
            NUMBER,
            BOOLEAN,
            ARRAY,
            OBJECT,
            ENUM,
            CUSTOM
        }
    }

    /**
     * Property constraint.
     */
    public record PropertyConstraint(
            String constraintType,
            Object value,
            String errorMessage
    ) {}

    /**
     * Slot definition.
     */
    public record SlotDefinition(
            String name,
            String slotType,
            boolean isRequired,
            int minItems,
            int maxItems,
            String description
    ) {}

    /**
     * Event definition.
     */
    public record EventDefinition(
            String name,
            String eventType,
            Map<String, String> payloadSchema,
            String description
    ) {}

    /**
     * Component constraints.
     */
    public record ComponentConstraints(
            Set<String> allowedParentTypes,
            Set<String> disallowedParentTypes,
            int maxDepth,
            Map<String, String> validationRules
    ) {}

    /**
     * Component alias for migration support.
     */
    public record ComponentAlias(
            String aliasName,
            String targetComponentId,
            String targetVersion,
            Instant createdAt,
            boolean isDeprecated,
            Instant deprecatedAt,
            String migrationNotes
    ) {}

    /**
     * Registry query result.
     */
    public record RegistryQueryResult(
            List<RegistryEntry> entries,
            int totalCount,
            int pageSize,
            int pageNumber,
            boolean hasMore
    ) {}

    /**
     * Component validation result.
     */
    public record ComponentValidationResult(
            boolean isValid,
            List<String> errors,
            List<String> warnings,
            Map<String, Object> validationContext
    ) {}
}
