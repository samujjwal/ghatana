/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.memory;

/**
 * Defines the supported memory scopes for agent memory namespaces.
 *
 * <ul>
 *   <li>{@link #EPISODIC} — Recent experiences and events (what happened)</li>
 *   <li>{@link #SEMANTIC} — Factual knowledge and beliefs (what I know)</li>
 *   <li>{@link #PROCEDURAL} — Skills, policies, and how-to knowledge (how to do)</li>
 *   <li>{@link #PREFERENCE} — Configured biases, styles, and preferences (what I prefer)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Memory scope taxonomy for namespace isolation
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public enum MemoryScope {

    /** Recent experiences, events, and turn history. */
    EPISODIC,

    /** Factual knowledge and external data retrieved by the agent. */
    SEMANTIC,

    /** Promoted skills, workflow patterns, and learned procedures. */
    PROCEDURAL,

    /** User preferences, interaction styles, and configured biases. */
    PREFERENCE
}
