/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.maintenance;

/**
 * Intent classification for agent tasks.
 *
 * <p>Distinguishes between new work (feature development, exploration)
 * and legacy work (maintenance, bug fixes, support for existing systems).
 *
 * @doc.type enum
 * @doc.purpose Intent classification for agent tasks
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum TaskIntent {
    /**
     * New feature development or exploration work.
     */
    NEW_WORK,

    /**
     * Maintenance of existing systems or bug fixes.
     */
    LEGACY_MAINTENANCE,

    /**
     * Support for existing systems without changes.
     */
    LEGACY_SUPPORT,

    /**
     * Migration from legacy to new systems.
     */
    MIGRATION,

    /**
     * Retirement or decommissioning of legacy systems.
     */
    RETIREMENT,

    /**
     * Unknown or unclassified intent.
     */
    UNKNOWN;

    /**
     * Returns true if this intent is legacy-related (maintenance, support, migration, retirement).
     *
     * @return true if legacy-related
     */
    public boolean isLegacy() {
        return this == LEGACY_MAINTENANCE
                || this == LEGACY_SUPPORT
                || this == MIGRATION
                || this == RETIREMENT;
    }

    /**
     * Returns true if this intent is new work.
     *
     * @return true if new work
     */
    public boolean isNewWork() {
        return this == NEW_WORK;
    }

    /**
     * Returns true if this intent allows maintenance-only execution.
     *
     * @return true if maintenance-only execution allowed
     */
    public boolean allowsMaintenanceOnly() {
        return isLegacy();
    }
}
