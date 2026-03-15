package com.ghatana.appplatform.plugin.manifest;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import com.ghatana.appplatform.plugin.domain.PluginVersion;

import java.util.Objects;

/**
 * Checks that a plugin's declared {@code platformVersionRange} is satisfied by the
 * current running platform version.
 *
 * <p>Range format mirrors npm/Maven semver ranges:
 * <ul>
 *   <li>{@code ">=2.0.0"} — minimum version</li>
 *   <li>{@code ">=2.0.0 <3.0.0"} — version window</li>
 *   <li>{@code "*"} or blank — any version accepted</li>
 * </ul>
 * Parsing delegates to {@link PluginVersion#satisfies(String)}.
 *
 * @doc.type  class
 * @doc.purpose Semver range check for plugin–platform compatibility
 * @doc.layer  product
 * @doc.pattern Service
 */
public final class PluginVersionCompatibilityChecker {

    private final PluginVersion platformVersion;

    /**
     * @param platformVersion the version of the currently running platform
     */
    public PluginVersionCompatibilityChecker(PluginVersion platformVersion) {
        this.platformVersion = Objects.requireNonNull(platformVersion, "platformVersion");
    }

    /**
     * Checks whether the current platform version satisfies the plugin's declared range.
     *
     * @param manifest the manifest being evaluated
     * @return {@code true} when compatible
     * @throws PluginVersionIncompatibleException if the platform version falls outside
     *                                            the declared range
     */
    public boolean check(PluginManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");

        String range = manifest.platformVersionRange();
        if (range == null || range.isBlank() || "*".equals(range)) {
            return true;
        }
        if (!platformVersion.satisfies(range)) {
            throw new PluginVersionIncompatibleException(
                    "Plugin '" + manifest.name() + "' requires platform version '" + range
                    + "' but running version is " + platformVersion);
        }
        return true;
    }
}
