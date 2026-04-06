package com.ghatana.platform.plugin;

/**
 * Represents the lifecycle state of a plugin.
 * <p>
 * Lifecycle progression:
 * <pre>
 * UNLOADED → DISCOVERED → INITIALIZED → STARTING → STARTED → RUNNING
 *                              ↓            ↓          ↓         ↓
 *                            ERROR       ERROR      ERROR     STOPPING → STOPPED
 *                              ↓            ↓          ↓                     ↓
 *                            FAILED      FAILED     FAILED              UNLOADED
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Track plugin lifecycle
 * @doc.layer core
 */
public enum PluginState {
    /**
     * Plugin code is loaded but not yet initialized.
     */
    UNLOADED,

    /**
     * Plugin has been discovered by the registry.
     */
    DISCOVERED,

    /**
     * Plugin has been initialized with context.
     */
    INITIALIZED,

    /**
     * Plugin is starting up (connecting to resources).
     */
    STARTING,

    /**
     * Plugin has completed its start sequence but is not yet fully operational.
     */
    STARTED,

    /**
     * Plugin is fully operational.
     */
    RUNNING,

    /**
     * Plugin is stopping.
     */
    STOPPING,

    /**
     * Plugin has stopped.
     */
    STOPPED,

    /**
     * Plugin encountered a recoverable error.
     */
    ERROR,

    /**
     * Plugin encountered a critical error and cannot recover.
     */
    FAILED;

    /**
     * Returns true if this state represents an active state
     * (INITIALIZED, STARTING, STARTED, or RUNNING).
     */
    public boolean isActive() {
        return this == INITIALIZED || this == STARTING || this == STARTED || this == RUNNING;
    }

    /**
     * Returns true if this state represents a terminal state
     * (STOPPED, ERROR, or FAILED).
     */
    public boolean isTerminal() {
        return this == STOPPED || this == ERROR || this == FAILED;
    }

    /**
     * Returns true if this state indicates an error condition (ERROR or FAILED).
     */
    public boolean isError() {
        return this == ERROR || this == FAILED;
    }
}
