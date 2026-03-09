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
 * Time unit enumeration.
 *
 * @doc.type enum
 * @doc.purpose Standard time units for platform temporal operations
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum GTimeUnit {
    MILLISECONDS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS
}
