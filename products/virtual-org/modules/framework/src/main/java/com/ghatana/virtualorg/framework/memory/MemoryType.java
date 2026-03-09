package com.ghatana.virtualorg.framework.memory;

/**
 * Types of agent memory.
 *
 * @doc.type enum
 * @doc.purpose Categorize memory entries
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum MemoryType {

    /**
     * Short-term working memory for current task. Typically cleared after task
     * completion.
     */
    WORKING,
    /**
     * Episodic memory of past experiences and events. Records what happened,
     * when, and outcomes.
     */
    EPISODIC,
    /**
     * Semantic memory of facts and knowledge. General knowledge not tied to
     * specific events.
     */
    SEMANTIC,
    /**
     * Procedural memory of how to do things. Steps, processes, and learned
     * behaviors.
     */
    PROCEDURAL,
    /**
     * Social memory of interactions with other agents. Relationships,
     * preferences, and communication patterns.
     */
    SOCIAL,
    /**
     * Feedback memory from human reviews. Corrections, preferences, and
     * guidance received.
     */
    FEEDBACK
}
