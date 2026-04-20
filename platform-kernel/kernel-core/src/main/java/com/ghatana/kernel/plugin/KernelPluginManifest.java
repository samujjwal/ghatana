/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.plugin;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable metadata descriptor for a kernel plugin.
 *
 * <p><b>Purpose</b><br>
 * Describes a plugin's identity, version, capabilities, and requirements.
 * Used by {@link PluginRegistry} to track and manage plugins without
 * instantiating them.
 *
 * <p><b>Manifest Information</b><br>
 * - Unique plugin ID (e.g., "com.ghatana.products.billing.plugin")
 * - Semantic version (e.g., "1.2.3")
 * - Functional capabilities provided by the plugin
 * - Plugin dependencies (on other plugins or kernel services)
 * - Lifecycle requirements (auto-start, optional, etc.)
 *
 * <p><b>Example Usage</b><br>
 * <pre>{@code
 *   KernelPluginManifest manifest = new KernelPluginManifest(
 *       "com.ghatana.products.billing.plugin",
 *       "1.0.0",
 *       Set.of("billing.transactions", "billing.reports"),
 *       Set.of("data-storage", "http-server"),
 *       true,  // autoStart
 *       "BillingPluginImpl"  // implementationClass
 *   );
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable plugin metadata and requirements
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @author Ghatana Platform Team
 * @since 1.1.0
 */
public final class KernelPluginManifest {

    private final String pluginId;
    private final String version;
    private final Set<String> capabilities;
    private final Set<String> dependencies;
    private final boolean autoStart;
    private final String implementationClass;

    /**
     * Creates a new plugin manifest with the given metadata.
     *
     * @param pluginId             unique plugin identifier; must not be blank
     * @param version              semantic version; must not be blank
     * @param capabilities         functional capabilities provided; may be empty
     * @param dependencies         required plugins/services; may be empty
     * @param autoStart            whether the plugin should be auto-started
     * @param implementationClass  fully qualified class name of plugin implementation
     * @throws NullPointerException if any non-optional parameter is null
     * @throws IllegalArgumentException if pluginId or version is blank
     */
    public KernelPluginManifest(
            String pluginId,
            String version,
            Set<String> capabilities,
            Set<String> dependencies,
            boolean autoStart,
            String implementationClass) {

        this.pluginId = Objects.requireNonNull(pluginId, "pluginId cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");

        if (pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId cannot be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version cannot be blank");
        }

        this.capabilities = capabilities != null ? Set.copyOf(capabilities) : Set.of();
        this.dependencies = dependencies != null ? Set.copyOf(dependencies) : Set.of();
        this.autoStart = autoStart;
        this.implementationClass = Objects.requireNonNull(implementationClass, "implementationClass cannot be null");
    }

    /**
     * Returns the unique identifier for this plugin.
     * Convention: reverse domain notation, e.g., "com.ghatana.products.billing.plugin"
     */
    public String pluginId() {
        return pluginId;
    }

    /**
     * Returns the semantic version of this plugin.
     */
    public String version() {
        return version;
    }

    /**
     * Returns the set of functional capabilities this plugin provides.
     */
    public Set<String> capabilities() {
        return capabilities;
    }

    /**
     * Returns the set of plugins or kernel services this plugin depends on.
     */
    public Set<String> dependencies() {
        return dependencies;
    }

    /**
     * Returns whether this plugin should be automatically started on registration.
     */
    public boolean isAutoStart() {
        return autoStart;
    }

    /**
     * Returns the fully qualified class name of the plugin implementation.
     * Must implement {@link KernelPlugin} interface.
     */
    public String implementationClass() {
        return implementationClass;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof KernelPluginManifest)) return false;
        KernelPluginManifest other = (KernelPluginManifest) obj;
        return pluginId.equals(other.pluginId) && version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginId, version);
    }

    @Override
    public String toString() {
        return "KernelPluginManifest{" +
                "pluginId='" + pluginId + '\'' +
                ", version='" + version + '\'' +
                ", capabilities=" + capabilities +
                ", dependencies=" + dependencies +
                ", autoStart=" + autoStart +
                '}';
    }
}
