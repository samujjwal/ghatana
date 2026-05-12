/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

/**
 * Applicability decision for a version context.
 *
 * @doc.type enum
 * @doc.purpose Applicability decision for version context
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum VersionApplicability {
    /**
     * Version is actively supported and recommended.
     */
    ACTIVE,

    /**
     * Version is in maintenance mode - usable but not recommended for new work.
     */
    MAINTENANCE,

    /**
     * Version is obsolete - should not be used.
     */
    OBSOLETE,

    /**
     * Version applicability is unknown.
     */
    UNKNOWN
}
