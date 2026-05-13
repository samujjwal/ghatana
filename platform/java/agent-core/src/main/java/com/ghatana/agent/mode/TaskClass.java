/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

/**
 * Classification of a task based on mastery, version context, and risk.
 *
 * @doc.type enum
 * @doc.purpose Task classification for mode selection
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum TaskClass {
    /**
     * Task is known and mastered for the current version.
     */
    KNOWN_TASK,

    /**
     * Task is known but with variations from the mastered pattern.
     */
    KNOWN_VARIATION,

    /**
     * Task is completely unknown to the agent.
     */
    UNKNOWN_TASK,

    /**
     * Task is high-risk (irreversible side effects, security implications, etc.).
     */
    HIGH_RISK_TASK,

    /**
     * Task is critical risk (security-critical, data-destructive, production-critical).
     */
    CRITICAL_RISK_TASK,

    /**
     * Task is for maintaining legacy code (old versions, deprecated patterns).
     */
    MAINTENANCE_TASK,

    /**
     * Task is maintenance-only (read-only, read-write on deprecated components).
     */
    MAINTENANCE_ONLY_TASK,

    /**
     * Task is exploratory (research, prototyping, experimentation).
     */
    EXPLORATION_TASK,

    /**
     * Task is a migration (upgrading versions, changing frameworks).
     */
    MIGRATION_TASK
}
