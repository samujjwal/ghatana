package com.ghatana.platform.kernel.dependency;

/**
 * @doc.type enum
 * @doc.purpose Dependency handling mode for critical adapters like DataCloud and AEP
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public enum DependencyMode {
    /**
     * STRICT mode: Fail fast if critical dependencies are missing.
     * Suitable for production environments where missing dependencies are unacceptable.
     * Write operations will throw exceptions if dependencies are unavailable.
     */
    STRICT("Fail fast on missing dependencies"),

    /**
     * DEGRADED mode: Allow graceful degradation with fallback behavior.
     * Suitable for development or environments where temporary degradation is acceptable.
     * Read operations may work with cached/local data; write operations log warnings but may succeed.
     */
    DEGRADED("Allow graceful degradation with fallbacks");

    private final String description;

    DependencyMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isStrict() {
        return this == STRICT;
    }

    public boolean isDegraded() {
        return this == DEGRADED;
    }
}
