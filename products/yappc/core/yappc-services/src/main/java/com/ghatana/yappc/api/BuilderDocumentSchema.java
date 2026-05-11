/**
 * Canonical Builder Document Schema
 * 
 * Single source of truth for builder document structure.
 * Defines the canonical schema for builder documents that are used in page artifacts.
 * This schema aligns with the @ghatana/ui-builder package but provides a backend reference.
 * 
 * @doc.type class
 * @doc.purpose Canonical builder document schema reference
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical builder document schema.
 * This is the backend reference for the builder document structure used in page artifacts.
 * The actual implementation lives in @ghatana/ui-builder package.
 */
public final class BuilderDocumentSchema {

    /**
     * Builder document metadata.
     */
    public record BuilderMetadata(
            String documentId,
            String version,
            String createdAt,
            String updatedAt,
            String createdBy,
            DataClassification dataClassification,
            TrustLevel trustLevel,
            Map<String, String> customProperties
    ) {
        public enum DataClassification {
            PUBLIC,
            INTERNAL,
            CONFIDENTIAL,
            RESTRICTED
        }

        public enum TrustLevel {
            TRUSTED,
            UNTRUSTED,
            DEGRADED,
            UNKNOWN
        }
    }

    /**
     * Builder document root node.
     */
    public record BuilderRoot(
            String id,
            String type,
            Map<String, Object> properties,
            List<BuilderNode> children
    ) {}

    /**
     * Builder node representing a UI component.
     */
    public record BuilderNode(
            String id,
            String type,
            Map<String, Object> properties,
            List<BuilderNode> children,
            NodeConstraints constraints,
            NodeStyle style,
            NodeBehavior behavior
    ) {}

    /**
     * Node constraints defining validation rules.
     */
    public record NodeConstraints(
            Set<String> allowedTypes,
            int minChildren,
            int maxChildren,
            Map<String, Object> propertyConstraints
    ) {}

    /**
     * Node style properties.
     */
    public record NodeStyle(
            Map<String, String> styles,
            Map<String, String> classes,
            Map<String, Object> theme
    ) {}

    /**
     * Node behavior defining interaction patterns.
     */
    public record NodeBehavior(
            boolean isInteractive,
            boolean isDraggable,
            boolean isResizable,
            Map<String, Object> eventHandlers
    ) {}

    /**
     * Component reference for registry integration.
     */
    public record ComponentReference(
            String componentId,
            String componentName,
            String componentVersion,
            String registryName,
            Map<String, Object> defaultProperties
    ) {}

    /**
     * AI action lineage for tracking AI-originated changes.
     */
    public record AIActionLineage(
            String actionId,
            String hookKind,
            String reviewState,
            String actorId,
            String timestamp,
            Map<String, String> metadata
    ) {
        public enum HookKind {
            GENERATE,
            REFACTOR,
            OPTIMIZE,
            TRANSLATE
        }

        public enum ReviewState {
            PENDING,
            APPROVED,
            REJECTED,
            AUTO_APPLIED
        }
    }
}
