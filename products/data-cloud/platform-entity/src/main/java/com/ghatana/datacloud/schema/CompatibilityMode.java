/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.3 — Schema compatibility modes for evolution checking.
 */
package com.ghatana.datacloud.schema;

/**
 * Component for CompatibilityMode
 *
 * @doc.type enum
 * @doc.purpose Component for CompatibilityMode
 * @doc.layer product
 * @doc.pattern Service
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
