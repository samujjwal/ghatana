package com.ghatana.agent.memory.model;

/**
 * Types of relationships between memory items.
 *
 * @doc.type enum
 * @doc.purpose Memory link classification
 * @doc.layer agent-memory
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
