/**
 * Canonical Builder Document Schema
 * 
 * Single source of truth for builder document structure.
 * Aligns with @ghatana/ui-builder TypeScript BuilderDocument schema (v1.0.0).
 * This provides the backend Java reference for the canonical document structure.
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
 * Canonical builder document schema aligned with TypeScript BuilderDocument v1.0.0.
 * This is the backend reference for the builder document structure used in page artifacts.
 * The actual implementation lives in @ghatana/ui-builder package.
 */
public final class BuilderDocumentSchema {

    /** Current schema version - must match TypeScript version */
    public static final String CURRENT_SCHEMA_VERSION = "1.0.0";

    /**
     * Builder document metadata - aligns with TypeScript DocumentMetadata.
     */
    public record DocumentMetadata(
            String createdAt,
            String updatedAt,
            String author,
            String description,
            List<String> tags,
            Integer changeCount,
            Integer collaborationVersion,
            String checkpointId,
            Map<String, Object> dataClassification,
            ReviewStatus reviewStatus,
            Map<String, Object> syncStatus,
            Map<String, Object> visibilityContract,
            Map<String, Object> trustLevel
    ) {
        public enum ReviewStatus {
            NONE,
            PENDING,
            APPROVED,
            REJECTED,
            REQUIRES_MANUAL
        }
    }

    /**
     * Position coordinates - aligns with TypeScript Position.
     */
    public record Position(
            double x,
            double y
    ) {}

    /**
     * Size dimensions - aligns with TypeScript Size.
     */
    public record Size(
            double width,
            double height
    ) {}

    /**
     * Layout constraints - aligns with TypeScript LayoutConstraints.
     */
    public record LayoutConstraints(
            Object minWidth, // Number or String
            Object maxWidth, // Number or String
            Object minHeight, // Number or String
            Object maxHeight, // Number or String
            Double aspectRatio,
            boolean resizable,
            boolean positionable,
            String overflow // visible, hidden, scroll, auto
    ) {}

    /**
     * Action definition - aligns with TypeScript ActionDefinition.
     */
    public record ActionDefinition(
            String id,
            String label,
            String triggerEvent,
            String targetKind, // navigate, toggle-state, emit-event, call-api, update-binding, custom
            Map<String, Object> payload,
            String condition
    ) {}

    /**
     * Responsive variant - aligns with TypeScript ResponsiveVariant.
     */
    public record ResponsiveVariant(
            String breakpoint,
            Double minWidth,
            Double maxWidth,
            Map<String, Object> props,
            Boolean hidden,
            Position position,
            Size size
    ) {}

    /**
     * State variant - aligns with TypeScript StateVariant.
     */
    public record StateVariant(
            String state, // hover, focus, active, disabled, error, loading, selected
            Map<String, Object> props
    ) {}

    /**
     * Privacy metadata - aligns with TypeScript PrivacyMetadata.
     */
    public record PrivacyMetadata(
            String classification, // public, internal, confidential, restricted
            List<String> piiFields,
            boolean requiresConsent
    ) {}

    /**
     * AI change record - aligns with TypeScript AIChangeRecord.
     */
    public record AIChangeRecord(
            String changeId,
            String timestamp,
            Map<String, Object> descriptor,
            DocumentMetadata.ReviewStatus reviewStatus
    ) {}

    /**
     * Provenance record - aligns with TypeScript ProvenanceRecord.
     */
    public record ProvenanceRecord(
            String createdBy,
            String createdAt,
            String modifiedBy,
            String modifiedAt,
            String version
    ) {}

    /**
     * Instance metadata - aligns with TypeScript InstanceMetadata.
     */
    public record InstanceMetadata(
            String name,
            Position position,
            Size size,
            Boolean locked,
            Boolean hidden,
            Map<String, Object> ownership,
            LayoutConstraints layout,
            List<ResponsiveVariant> responsiveVariants,
            List<StateVariant> stateVariants,
            List<ActionDefinition> actions,
            DocumentMetadata.ReviewStatus reviewStatus,
            Map<String, Object> pendingProps,
            PrivacyMetadata privacyMetadata,
            Map<String, Object> dataClassification,
            List<AIChangeRecord> aiLineage,
            String collaborationId,
            ProvenanceRecord provenance
    ) {}

    /**
     * Binding definition - aligns with TypeScript Binding.
     */
    public record Binding(
            String id,
            String type, // data, event, slot, theme, computed
            String source,
            String target,
            String transform,
            Boolean bidirectional
    ) {}

    /**
     * Component instance - aligns with TypeScript ComponentInstance.
     */
    public record ComponentInstance(
            String id,
            String contractName,
            Map<String, Object> props,
            Map<String, List<String>> slots,
            List<Binding> bindings,
            InstanceMetadata metadata
    ) {}

    /**
     * Design system model - aligns with TypeScript DesignSystemModel.
     */
    public record DesignSystemModel(
            String id,
            String name,
            String version,
            List<String> tokenSetIds,
            List<Map<String, Object>> componentContracts,
            String themeId
    ) {}

    /**
     * Layout node - aligns with TypeScript LayoutNode.
     */
    public record LayoutNode(
            String id,
            String type, // root, container, leaf
            List<String> children,
            String layout, // flex, grid, absolute, stack
            Map<String, Object> layoutProps
    ) {}

    /**
     * Layout definition - aligns with TypeScript Layout.
     */
    public record Layout(
            String type, // flex, grid, absolute, stack, flow
            Map<String, LayoutNode> nodes,
            String rootId
    ) {}

    /**
     * i18n configuration - aligns with TypeScript I18n.
     */
    public record I18n(
            String defaultLocale,
            List<String> locales,
            Map<String, Map<String, String>> translations
    ) {}

    /**
     * Accessibility configuration - aligns with TypeScript A11y.
     */
    public record A11y(
            String title,
            String description,
            List<Landmark> landmarks,
            List<SkipLink> skipLinks
    ) {
        public record Landmark(
                String type, // main, navigation, complementary, contentinfo, search, banner
                String nodeId
        ) {}

        public record SkipLink(
                String targetId,
                String label
        ) {}
    }

    /**
     * Privacy configuration - aligns with TypeScript Privacy.
     */
    public record Privacy(
            String classification, // public, internal, confidential, restricted
            List<String> piiNodes,
            DataRetention dataRetention,
            boolean consentRequired
    ) {
        public record DataRetention(
                int days,
                boolean autoDelete
        ) {}
    }

    /**
     * Validation rule - aligns with TypeScript ValidationRule.
     */
    public record ValidationRule(
            String id,
            String type, // required, format, range, custom
            String target,
            String message,
            Map<String, Object> params
    ) {}

    /**
     * Validation configuration - aligns with TypeScript Validation.
     */
    public record Validation(
            List<ValidationRule> rules,
            String validateOn // change, blur, submit
    ) {}

    /**
     * Complete BuilderDocument - aligns with TypeScript BuilderDocument.
     */
    public record BuilderDocument(
            String schemaVersion,
            String documentId,
            String owner,
            String root,
            Map<String, ComponentInstance> nodes,
            List<Binding> bindings,
            Layout layout,
            DocumentMetadata metadata,
            I18n i18n,
            A11y a11y,
            Privacy privacy,
            Validation validation
    ) {}
}
