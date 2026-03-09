/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.3 — Schema compatibility modes for evolution checking.
 */
package com.ghatana.datacloud.schema;

/**
 * Schema compatibility modes controlling how schemas can evolve.
 *
 * <ul>
 *   <li>{@link #BACKWARD} — new schema can read data produced by old schema</li>
 *   <li>{@link #FORWARD} — old schema can read data produced by new schema</li>
 *   <li>{@link #FULL} — both backward and forward compatible</li>
 *   <li>{@link #NONE} — no compatibility checking</li>
 * </ul>
 */
public enum CompatibilityMode {
    /** New schema can read old data. Allows: adding optional fields, removing required fields. */
    BACKWARD,
    /** Old schema can read new data. Allows: removing optional fields, adding required fields with defaults. */
    FORWARD,
    /** Both backward and forward compatible. Strictest mode. */
    FULL,
    /** No compatibility enforcement. Any schema change allowed. */
    NONE
}
