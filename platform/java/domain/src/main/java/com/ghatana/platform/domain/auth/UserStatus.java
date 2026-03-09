/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

/**
 * Lifecycle status of a user account.
 *
 * <p>Tracks the state of a user account for access control and lifecycle management.
 * Used to enforce authentication rules (e.g., LOCKED users cannot authenticate).
 *
 * <p><b>State Transitions</b>
 * <ul>
 *   <li>PENDING → ACTIVE (email verification or admin approval)</li>
 *   <li>ACTIVE → INACTIVE (user deactivation or soft delete)</li>
 *   <li>ACTIVE → LOCKED (security policy, failed login attempts)</li>
 *   <li>LOCKED → ACTIVE (admin unlock or automatic unlock after timeout)</li>
 *   <li>INACTIVE → ACTIVE (user reactivation)</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose User account lifecycle status
 * @doc.layer core
 * @doc.pattern Value Object
 */
public enum UserStatus {

    ACTIVE,
    INACTIVE,
    LOCKED,
    PENDING;

    /**
     * Whether a user in this status can authenticate.
     *
     * @return true for ACTIVE and PENDING
     */
    public boolean canAuthenticate() {
        return this == ACTIVE || this == PENDING;
    }

    /**
     * Whether the account is fully active (not pending/locked).
     *
     * @return true only for ACTIVE
     */
    public boolean isFullyActive() {
        return this == ACTIVE;
    }

    /**
     * Whether the status requires user or admin action.
     *
     * @return true for LOCKED and PENDING
     */
    public boolean requiresAction() {
        return this == LOCKED || this == PENDING;
    }
}
