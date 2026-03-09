package com.ghatana.platform.testing;

/**
 * Canonical classification for tests across the platform.
 * Combines fine-grained performance types with broader functional categories.
 * 
 * @doc.type enum
 * @doc.purpose Comprehensive test type classification including functional and performance categories
 * @doc.layer core
 * @doc.pattern Enumeration, Type Classification
 */
public enum TestType {
    // Functional categories
    UNIT,
    INTEGRATION,
    E2E,
    SECURITY,
    PERFORMANCE,
    OTHER,

    // Performance sub-types
    LOAD_TEST,
    STRESS_TEST,
    SPIKE_TEST,
    VOLUME_TEST,
    ENDURANCE_TEST,
    SCALABILITY_TEST
}
