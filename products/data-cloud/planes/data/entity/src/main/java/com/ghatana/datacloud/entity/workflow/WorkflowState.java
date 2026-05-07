package com.ghatana.datacloud.entity.workflow;

/**
 * Enumeration of workflow lifecycle states for entity publication.
 *
 * <p><b>Purpose</b><br>
 * Defines valid states in entity publication workflow and transitions between them.
 * Supports draft → review → published → archived lifecycle.
 *
 * <p><b>State Transitions</b><br>
 * - DRAFT: Initial state, entity is being edited, not visible to consumers
 * - REVIEW: Entity submitted for review, awaiting approval
 * - PUBLISHED: Entity approved and publicly visible
 * - ARCHIVED: Entity retired and no longer active
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * if (entity.getWorkflowState() == WorkflowState.PUBLISHED) {
 *     return entity;
 * }
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Entity publication lifecycle states
 * @doc.layer domain
 * @doc.pattern Enumeration
 */
public enum WorkflowState {
    /**
     * Initial state - entity is being edited, not visible to consumers.
     */
    DRAFT("draft"),

    /**
     * Review state - entity submitted for review, awaiting approval.
     */
    REVIEW("review"),

    /**
     * Published state - entity approved and visible to consumers.
     */
    PUBLISHED("published"),

    /**
     * Archived state - entity retired and no longer active.
     */
    ARCHIVED("archived");

    private final String displayName;

    WorkflowState(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name for the state.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a string to WorkflowState.
     *
     * @param value the string value
     * @return the corresponding WorkflowState, or null if not found
     */
    public static WorkflowState fromString(String value) {
        if (value == null) {
            return null;
        }
        for (WorkflowState state : WorkflowState.values()) {
            if (state.name().equalsIgnoreCase(value) || state.displayName.equalsIgnoreCase(value)) {
                return state;
            }
        }
        return null;
    }
}
