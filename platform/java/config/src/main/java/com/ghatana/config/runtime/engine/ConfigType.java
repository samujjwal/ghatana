/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.config.runtime.engine;

/**
 * Types of configuration recognized by the {@link ConfigurationEngine}.
 *
 * @doc.type enum
 * @doc.purpose Categorize configuration files by domain
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ConfigType {

    /** System-wide settings (logging, defaults, etc.). */
    SYSTEM,

    /** Agent blueprints. */
    AGENT,

    /** Pipeline definitions. */
    PIPELINE;

    /**
     * Maps a directory name to a config type.
     *
     * @param dirName the directory name (e.g. "agents", "pipelines")
     * @return the matching {@link ConfigType}, defaulting to {@link #SYSTEM}
     */
    public static ConfigType fromDirectoryName(String dirName) {
        if (dirName == null || dirName.isBlank()) {
            return SYSTEM;
        }
        return switch (dirName.toLowerCase()) {
            case "agents", "agent" -> AGENT;
            case "pipelines", "pipeline" -> PIPELINE;
            default -> SYSTEM;
        };
    }
}
