/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

/**
 * Degradation modes for the graceful-degradation manager.
 *
 * <p>When an incident is detected, the platform reduces agent capability
 * progressively rather than abruptly halting all tenants. Each mode represents
 * a step down the capability ladder.
 *
 * @doc.type enum
 * @doc.purpose Enumerate graceful-degradation capability levels
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum DegradationMode {

    /** Full capability — all agent features available. */
    FULL,

    /**
     * Read-only mode — agents may query data but not execute any write or
     * external-call tool actions.
     */
    READ_ONLY,

    /**
     * Notifications-only mode — agents may only emit notifications; all other
     * tool calls are blocked.
     */
    NOTIFICATIONS_ONLY,

    /**
     * Offline mode — agents are completely suspended. Equivalent to a soft
     * kill switch; the agent process remains running but declines all requests.
     */
    OFFLINE
}
