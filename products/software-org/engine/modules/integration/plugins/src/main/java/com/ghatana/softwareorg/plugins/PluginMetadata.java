package com.ghatana.softwareorg.plugins;

import java.util.Objects;
import java.util.Set;

/**
 * Metadata for a registered plugin.
 *
 * <p>
 * <b>Purpose</b><br>
 * Describes capabilities, version, and configuration of a plugin integrated
 * into software-org.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PluginMetadata metadata = new PluginMetadata(
 *   "github-integration",
 *   "1.0.0",
 *   Set.of("push", "pull_request", "check_run"),
 *   "com.ghatana.softwareorg.integration.github.GithubIntegrationPlugin"
 * );
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Plugin metadata and capabilities
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class PluginMetadata {

    private final String pluginId;
    private final String version;
    private final Set<String> capabilities;
    private final String implementationClass;
    private final boolean enabled;

    public PluginMetadata(String pluginId, String version, Set<String> capabilities, String implementationClass) {
        this.pluginId = Objects.requireNonNull(pluginId);
        this.version = Objects.requireNonNull(version);
        this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities));
        this.implementationClass = Objects.requireNonNull(implementationClass);
        this.enabled = true;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getVersion() {
        return version;
    }

    public Set<String> getCapabilities() {
        return capabilities;
    }

    public String getImplementationClass() {
        return implementationClass;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PluginMetadata m && pluginId.equals(m.pluginId);
    }

    @Override
    public int hashCode() {
        return pluginId.hashCode();
    }

    @Override
    public String toString() {
        return "PluginMetadata{" + pluginId + " v" + version + "}";
    }
}
