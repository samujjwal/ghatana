package com.ghatana.datacloud.config.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Compiled lifecycle configuration including retention and archival.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled lifecycle configuration for data retention
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledLifecycleConfig(
        CompiledRetentionConfig retention,
        CompiledArchivalConfig archival,
        CompiledCompactionConfig compaction
        ) {

    /**
     * Creates a CompiledLifecycleConfig with defaults.
     */
    public CompiledLifecycleConfig   {
        retention = retention != null ? retention : CompiledRetentionConfig.infinite();
        archival = archival != null ? archival : CompiledArchivalConfig.disabled();
        compaction = compaction != null ? compaction : CompiledCompactionConfig.disabled();
    }

    /**
     * Retention policy configuration.
     */
    public record CompiledRetentionConfig(
            Duration duration,
            RetentionAction action
    ) {
        

    public CompiledRetentionConfig   {
        action = action != null ? action : RetentionAction.DELETE;
    }

    /**
     * Create an infinite retention policy.
     */
    public static CompiledRetentionConfig infinite() {
        return new CompiledRetentionConfig(null, RetentionAction.ARCHIVE);
    }

    /**
     * Check if retention is infinite.
     */
    public boolean isInfinite() {
        return duration == null;
    }
}

/**
 * Archival configuration.
 */
public record CompiledArchivalConfig(
        boolean enabled,
        Duration afterDuration,
        String targetProfile
        ) {

    /**
     * Create a disabled archival config.
     */
    public static CompiledArchivalConfig disabled() {
        return new CompiledArchivalConfig(false, null, null);
    }
}

/**
 * Compaction configuration (for entity collections, not events).
 */
public record CompiledCompactionConfig(
        boolean enabled
        ) {

    /**
     * Create a disabled compaction config.
     */
    public static CompiledCompactionConfig disabled() {
        return new CompiledCompactionConfig(false);
    }
}

/**
 * Actions to take when retention period expires.
 */
public enum RetentionAction {
    /**
     * Delete data permanently
     */
    DELETE,
    /**
     * Archive to cold storage
     */
    ARCHIVE,
    /**
     * Anonymize PII fields
     */
    ANONYMIZE
}
}
