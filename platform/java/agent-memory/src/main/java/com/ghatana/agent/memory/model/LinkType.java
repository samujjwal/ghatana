package com.ghatana.agent.memory.model;

/**
 * Link type enumeration for memory item relationships.
 *
 * @doc.type enum
 * @doc.purpose Memory relationship classification
 * @doc.layer agent-memory
 * @doc.pattern Enum
 */
public enum LinkType {

    /** Source item provides evidence for target item. */
    SUPPORTS,

    /** Source item contradicts or invalidates target item. */
    CONTRADICTS,

    /** Source item was derived from target item (e.g., fact from episode). */
    DERIVED_FROM,

    /** Source item replaces/supersedes target item. */
    SUPERSEDES,

    /** General semantic relationship. */
    RELATED
}
