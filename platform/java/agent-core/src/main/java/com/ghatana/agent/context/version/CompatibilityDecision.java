/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.jetbrains.annotations.NotNull;

/**
 * Decision about compatibility between version contexts.
 *
 * @doc.type enum
 * @doc.purpose Compatibility decision for version contexts
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum CompatibilityDecision {
    /**
     * Versions are fully compatible.
     */
    COMPATIBLE,

    /**
     * Versions are compatible with warnings.
     */
    COMPATIBLE_WITH_WARNINGS,

    /**
     * Versions are incompatible.
     */
    INCOMPATIBLE,

    /**
     * Compatibility cannot be determined.
     */
    UNKNOWN
}
