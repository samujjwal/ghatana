package com.ghatana.agent.memory.model;

/**
 * Lifecycle status for a memory item's validity.
 *
 * @doc.type enum
 * @doc.purpose Memory validity lifecycle states
 * @doc.layer agent-memory
 */
public enum ValidityStatus {

    /** Item is current and actively used. */
    ACTIVE,

    /** Item may be outdated; queued for re-verification. */
    STALE,

    /** Item has been superseded by a newer version. */
    DEPRECATED,

    /** Item has been archived for historical reference only. */
    ARCHIVED
}
