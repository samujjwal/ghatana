/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

/**
 * Determinism guarantee an agent provides.
 *
 * <p>Determines caching, replay, and memoization strategies.
 *
 * @doc.type enum
 * @doc.purpose Determinism classification
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum DeterminismGuarantee {

    /** Fully deterministic — same input, same output, always. */
    FULL,

    /** Deterministic within a configuration version. */
    CONFIG_SCOPED,

    /** Probabilistic — output may vary for same input. */
    NONE,

    /** Eventually consistent — output converges over time. */
    EVENTUAL
}
