/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.interaction;

/**
 * Defines how a shared context entry is visible to other agents.
 *
 * @doc.type enum
 * @doc.purpose Visibility boundary for agent shared context entries
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum ContextSharingScope {
    /** Visible only within the same conversation thread. */
    CONVERSATION,
    /** Visible to all agents within the same tenant session. */
    SESSION,
    /** Visible to all members of the same composition group. */
    COMPOSITION_GROUP,
    /** Visible globally within the tenant boundary. */
    TENANT
}
