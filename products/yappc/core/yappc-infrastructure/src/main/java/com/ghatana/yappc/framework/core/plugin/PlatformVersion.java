/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin;

/**
 * Single source of truth for the current YAPPC platform version.
 *
 * <p>This constant is referenced by {@link PluginManager} during sandbox compatibility
 * checks. Update this value when cutting a new platform release.
 *
 * @doc.type class
 * @doc.purpose Platform version constant for plugin compatibility checks
 * @doc.layer product
 * @doc.pattern Constants
 */
public final class PlatformVersion {

    /** Current platform version in SemVer format. */
    public static final String CURRENT = "2.0.0";

    private PlatformVersion() {}
}
