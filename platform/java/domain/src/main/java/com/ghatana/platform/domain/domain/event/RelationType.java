package com.ghatana.platform.domain.domain.event;

/**
 * Enumeration of possible relation types between correlated events.
 * 
 * Defines the semantic relationships and dependencies between events in event correlation graphs.
 * Enables rich event relationship modeling and complex correlation pattern specification.
 * 
 * Causal Relations: CAUSES, CAUSED_BY (direct causality)
 * Hierarchical Relations: PARENT_OF, CHILD_OF, PARENT, CHILD (nesting/composition)
 * Sequence Relations: FOLLOWS, PRECEDES (temporal ordering)
 * Reference Relations: REFERENCES, REFERENCED_BY, DERIVED_FROM (data flow)
 * Equivalence Relations: DUPLICATE_OF (event de-duplication)
 * Response Relations: RESPONSE_TO, RETRY_OF (event handling patterns)
 * Correction Relations: CORRECTION_OF, ROLLBACK_OF (error recovery)
 * Generic Relations: RELATED_TO, CUSTOM (unspecified or custom relationships)
 * 
 * Architecture Role:
 * - Used by: Graph construction, correlation engines, pattern matching, event routing
 * - Created by: Correlation rules, pattern detectors, event processors
 * - Stored in: Event correlation graphs, relationship tables, pattern definitions
 * - Purpose: Model event dependencies for causality analysis, SLA tracking, root cause identification
 * 
 * Usage Example:
 * {@code
 * event1.addRelation(RelationType.CAUSES, event2);  // event1 caused event2
 * event3.addRelation(RelationType.PARENT_OF, event4);  // parent-child hierarchy
 * event5.addRelation(RelationType.RETRY_OF, event6);  // retry relationship
 * 
 * // Custom relationships
 * RelationType custom = RelationType.CUSTOM.withName("triggers_escalation");
 * }
 * 
 * Graph Construction:
 * Relationships form edges in event correlation DAGs, enabling:
 * 1. Causality chains: CAUSES chains trace root cause
 * 2. Parent-child hierarchies: PARENT_OF structures aggregate events
 * 3. Temporal sequences: FOLLOWS/PRECEDES track ordering
 * 4. Response patterns: RESPONSE_TO/RETRY_OF handle error recovery
 * 5. De-duplication: DUPLICATE_OF merges equivalent events
 * 
 * Thread Safety: Enum constants are immutable and thread-safe.
 * Performance: O(1) for all operations.
 * 
 * @doc.type enum
 * @doc.layer domain
 * @doc.purpose type-safe enumeration for event relationship semantics
 * @doc.pattern enum-with-methods
 * @doc.test-hints test all relation types, test withName()/getName(), test custom names
 * @see NodeId (nodes connected by relations)
 * @see EdgeId (edges representing relations)
 */
public enum RelationType {
    /** Indicates that this event was caused by another event. */
    CAUSED_BY,

    /** Indicates that this event caused another event. */
    CAUSES,

    /** Indicates that this event is related to another event. */
    RELATED_TO,

    /** Indicates that this event is a child of another event. */
    CHILD_OF,

    /** Indicates that this event is a parent of another event. */
    PARENT_OF,

    /** Indicates that this event references another event. */
    REFERENCES,

    /** Indicates that this event is referenced by another event. */
    REFERENCED_BY,

    /** Indicates that this event follows another event in a sequence. */
    FOLLOWS,

    /** Indicates that this event precedes another event in a sequence. */
    PRECEDES,

    /** Indicates a generic parent relation. */
    PARENT,

    /** Indicates a generic child relation. */
    CHILD,

    /** Indicates that this event was derived from another event. */
    DERIVED_FROM,

    /** Indicates that this event is a duplicate of another event. */
    DUPLICATE_OF,

    /** Indicates that this event is a response to another event. */
    RESPONSE_TO,

    /** Indicates that this event is a retry of another event. */
    RETRY_OF,

    /** Indicates that this event is a correction of another event. */
    CORRECTION_OF,

    /** Indicates that this event rolls back another event. */
    ROLLBACK_OF,

    /** Indicates a custom relation type. */
    CUSTOM;

    private String customName;

    /**
     * Sets a custom name for this relation type. Primarily used with {@link #CUSTOM} but
     * available for other relation types for backward compatibility with existing APIs.
     *
     * @param name custom relation name
     * @return this relation type for method chaining
     */
    public RelationType withName(String name) {
        this.customName = name;
        return this;
    }

    /**
     * Returns the name associated with this relation type.
     *
     * @return custom name if set (or "custom" for {@link #CUSTOM}), otherwise the enum name in lowercase
     */
    public String getName() {
        if (this == CUSTOM) {
            return customName != null ? customName : "custom";
        }
        return customName != null ? customName : name().toLowerCase();
    }

    @Override
    public String toString() {
        return getName();
    }
}
