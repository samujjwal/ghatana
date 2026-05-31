/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

/**
 * Plugin lifecycle state enumeration (P8).
 *
 * <p>Defines the lifecycle states that a plugin can be in.
 *
 * @doc.type enum
 * @doc.purpose Plugin lifecycle state enumeration
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum PluginLifecycleState {
    /**
     * Plugin has been installed but not yet initialized.
     */
    INSTALLED,
    
    /**
     * Plugin is being initialized.
     */
    INITIALIZING,
    
    /**
     * Plugin has been initialized but not yet enabled.
     */
    INITIALIZED,
    
    /**
     * Plugin is enabled and ready to start.
     */
    ENABLED,
    
    /**
     * Plugin is currently running and processing requests.
     */
    RUNNING,
    
    /**
     * Plugin is being stopped.
     */
    STOPPING,
    
    /**
     * Plugin has been stopped but not disabled.
     */
    STOPPED,
    
    /**
     * Plugin has been disabled.
     */
    DISABLED,
    
    /**
     * Plugin is being uninstalled.
     */
    UNINSTALLING,
    
    /**
     * Plugin has been uninstalled.
     */
    UNINSTALLED,
    
    /**
     * Plugin is in an error state.
     */
    ERROR,
    
    /**
     * Plugin is being upgraded.
     */
    UPGRADING,
    
    /**
     * Plugin is in maintenance mode.
     */
    MAINTENANCE
}
