/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

/**
 * Plugin tier classification.
 *
 * <p>Defines the security and capability level of plugins:
 * <ul>
 *   <li>T1: Configuration-only plugins - no code execution</li>
 *   <li>T2: Scripted plugins - sandboxed execution with resource limits</li>
 *   <li>T3: Network-capable plugins - extended permissions with oversight</li>
 * </ul></p>
 *
 * @doc.type enum
 * @doc.purpose Plugin tier classification - T1 config-only, T2 scripted, T3 network-capable
 * @doc.layer platform
 * @doc.pattern Enumeration
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public enum PluginTier {
    
    /**
     * T1: Configuration-only plugins.
     * 
     * <p>Capabilities:
     * <ul>
     *   <li>Read/write configuration</li>
     *   <li>No code execution</li>
     *   <li>No network access</li>
     *   <li>No file system access</li>
     * </ul></p>
     */
    T1(1, "Configuration-only"),
    
    /**
     * T2: Scripted plugins.
     * 
     * <p>Capabilities:
     * <ul>
     *   <li>All T1 capabilities</li>
     *   <li>Sandboxed script execution</li>
     *   <li>Limited memory allocation</li>
     *   <li>No network access</li>
     *   <li>No file system access</li>
     * </ul></p>
     */
    T2(2, "Scripted"),
    
    /**
     * T3: Network-capable plugins.
     * 
     * <p>Capabilities:
     * <ul>
     *   <li>All T2 capabilities</li>
     *   <li>Network access</li>
     *   <li>Limited file system access</li>
     *   <li>Process spawning</li>
     *   <li>Extended resource limits</li>
     * </ul></p>
     */
    T3(3, "Network-capable");

    private final int level;
    private final String description;

    PluginTier(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * Gets the tier level (1-3).
     *
     * @return the tier level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Gets the tier description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Creates a plugin tier from manifest data.
     *
     * @param manifest the plugin manifest
     * @return the plugin tier
     */
    public static PluginTier fromManifest(PluginManifest manifest) {
        // Default to T2 if not specified
        if (manifest == null || manifest.getTier() == null) {
            return T2;
        }
        return manifest.getTier();
    }

    /**
     * Checks if this tier can access capabilities of another tier.
     *
     * @param other the other tier
     * @return true if this tier level is >= other tier level
     */
    public boolean canAccess(PluginTier other) {
        return this.level >= other.level;
    }

    @Override
    public String toString() {
        return String.format("T%d (%s)", level, description);
    }
}
