/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.config.runtime.engine;

import java.time.Instant;

/**
 * Describes a change to a named configuration (e.g. loaded, modified, rolled back).
 *
 * @doc.type record
 * @doc.purpose Configuration change event
 * @doc.layer platform
 * @doc.pattern ValueObject
 *
 * @param configName the name of the affected configuration
 * @param changeType the kind of change
 * @param version    the version after the change
 * @param timestamp  when the change occurred
 */
public record ConfigChange(
        String configName,
        ChangeType changeType,
        String version,
        Instant timestamp
) {

    /**
     * Convenience constructor that auto-stamps the current time.
     */
    public ConfigChange(String configName, ChangeType changeType, String version) {
        this(configName, changeType, version, Instant.now());
    }

    /**
     * Kinds of configuration change.
     */
    public enum ChangeType {
        LOADED,
        MODIFIED,
        ROLLED_BACK
    }
}
