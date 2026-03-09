/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.types.time;

/**
 * Interval type for time-based operations.
 *
 * @doc.type enum
 * @doc.purpose Describes how interval boundaries are defined
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum IntervalType {
    FIXED,
    SLIDING,
    TUMBLING,
    SESSION
}
