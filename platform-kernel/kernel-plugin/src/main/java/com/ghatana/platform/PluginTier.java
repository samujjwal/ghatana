/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import com.ghatana.kernel.plugin.PluginManifest;

/**
 * Plugin tier classification - delegates to kernel-core PluginTier.
 *
 * @doc.type enum
 * @doc.purpose Plugin tier classification - delegates to kernel-core
 * @doc.layer platform
 * @doc.pattern Adapter
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
@Deprecated(forRemoval = true, since = "1.0.0")
public enum PluginTier {

    T1,
    T2,
    T3;

    /**
     * Determines the tier from a plugin manifest.
     *
     * @param manifest the plugin manifest
     * @return the plugin tier
     */
    public static PluginTier fromManifest(PluginManifest manifest) {
        if (manifest == null || manifest.getTier() == null) {
            return T2; // Default tier
        }
        return fromKernelTier(manifest.getTier());
    }

    private static PluginTier fromKernelTier(com.ghatana.kernel.plugin.PluginTier kernelTier) {
        switch (kernelTier) {
            case T1: return T1;
            case T2: return T2;
            case T3: return T3;
            default: return T2;
        }
    }

    /**
     * Gets the tier level (1 for T1, 2 for T2, 3 for T3).
     *
     * @return the tier level
     */
    public int getLevel() {
        return ordinal() + 1;
    }
}
