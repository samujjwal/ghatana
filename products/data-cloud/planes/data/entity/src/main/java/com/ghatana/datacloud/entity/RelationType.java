package com.ghatana.datacloud.entity;

/**
 * Enum representing types of relationships between entities.
 *
 * <p><b>Purpose</b><br>
 * Classifies entity relationships discovered through AI-powered similarity
 * search, explicit references, or usage patterns.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RelationType type = RelationType.SIMILAR;
 * if (type.isContentBased()) {
 *     // Handle similarity-based relation
 * }
 * }</pre>
 *
 * @see EntityRelation
 * @doc.type enum
 * @doc.purpose Entity relationship type classifier
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum RelationType {
    /**
     * Content-based similarity (discovered via embeddings).
     * Entities have similar semantic content.
     */
    SIMILAR,

    /**
     * Explicit reference relationship.
     * One entity references another via a reference field.
     */
    REFERENCED,

    /**
     * Related by usage patterns.
     * Entities are frequently accessed together.
     */
    RELATED,

    /**
     * Parent-child hierarchy.
     * One entity is a parent/child of another.
     */
    HIERARCHICAL;

    /**
     * Checks if this is a content-based relationship.
     *
     * @return true if SIMILAR
     */
    public boolean isContentBased() {
        return this == SIMILAR;
    }

    /**
     * Checks if this is an explicit relationship.
     *
     * @return true if REFERENCED or HIERARCHICAL
     */
    public boolean isExplicit() {
        return this == REFERENCED || this == HIERARCHICAL;
    }

    /**
     * Checks if this is a usage-based relationship.
     *
     * @return true if RELATED
     */
    public boolean isUsageBased() {
        return this == RELATED;
    }
}
