package com.ghatana.kernel.plugin;

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
 * @doc.layer core
 * @doc.pattern Enumeration
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public enum PluginTier {

    /**
     * T1: Configuration-only plugins.
     *
     * <p>Capabilities:
     * <ul>
     *   <li>No code execution</li>
     *   <li>Static configuration only</li>
     *   <li>No network access</li>
     *   <li>No file system access</li>
     * </ul></p>
     */
    T1,

    /**
     * T2: Scripted plugins.
     *
     * <p>Capabilities:
     * <ul>
     *   <li>Sandboxed code execution</li>
     *   <li>Limited resource quotas</li>
     *   <li>No network access by default</li>
     *   <li>Read-only file system access within plugin directory</li>
     * </ul></p>
     */
    T2,

    /**
     * T3: Network-capable plugins.
     *
     * <p>Capabilities:
     * <ul>
     *   <li>Extended code execution permissions</li>
     *   <li>Network access with oversight</li>
     *   <li>Higher resource quotas</li>
     *   <li>Write access within plugin directory</li>
     * </ul></p>
     */
    T3;

    private final int level;

    PluginTier() {
        this.level = ordinal() + 1;
    }

    /**
     * Gets the tier level (1 for T1, 2 for T2, 3 for T3).
     *
     * @return the tier level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Determines the tier from a plugin manifest.
     *
     * @param manifest the plugin manifest
     * @return the plugin tier, or T2 as default
     */
    public static PluginTier fromManifest(PluginManifest manifest) {
        if (manifest == null || manifest.getTier() == null) {
            return T2; // Default tier
        }
        return manifest.getTier();
    }
}
