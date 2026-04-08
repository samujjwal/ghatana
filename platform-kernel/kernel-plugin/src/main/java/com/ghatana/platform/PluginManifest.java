/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Plugin manifest containing metadata and capabilities.
 *
 * @doc.type class
 * @doc.purpose Plugin manifest - metadata, capabilities, resource quotas, tier
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class PluginManifest {

    private final String pluginId;
    private final String version;
    private final String description;
    private final PluginTier tier;
    private final Set<String> capabilities;
    private final Set<PluginDependency> dependencies;
    private final PluginResourceQuota resourceQuotas;
    private final Map<String, Object> metadata;

    private PluginManifest(Builder builder) {
        this.pluginId = Objects.requireNonNull(builder.pluginId, "pluginId");
        this.version = Objects.requireNonNull(builder.version, "version");
        this.description = Objects.requireNonNullElse(builder.description, "");
        this.tier = Objects.requireNonNullElse(builder.tier, PluginTier.T2);
        this.capabilities = Set.copyOf(Objects.requireNonNullElse(builder.capabilities, Set.of()));
        this.dependencies = Set.copyOf(Objects.requireNonNullElse(builder.dependencies, Set.of()));
        this.resourceQuotas = Objects.requireNonNullElse(builder.resourceQuotas, PluginResourceQuota.defaults());
        this.metadata = Map.copyOf(Objects.requireNonNullElse(builder.metadata, Map.of()));
    }

    /**
     * Gets the plugin identifier.
     *
     * @return the plugin ID
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * Gets the plugin version.
     *
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the plugin description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the plugin tier.
     *
     * @return the tier
     */
    public PluginTier getTier() {
        return tier;
    }

    /**
     * Gets the plugin capabilities.
     *
     * @return immutable set of capabilities
     */
    public Set<String> getCapabilities() {
        return capabilities;
    }

    /**
     * Gets the plugin dependencies.
     *
     * @return immutable set of dependencies
     */
    public Set<PluginDependency> getDependencies() {
        return dependencies;
    }

    /**
     * Gets the resource quotas.
     *
     * @return the resource quotas
     */
    public PluginResourceQuota getResourceQuotas() {
        return resourceQuotas;
    }

    /**
     * Gets the additional metadata.
     *
     * @return immutable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Checks if the plugin has a specific capability.
     *
     * @param capability the capability to check
     * @return true if the plugin has the capability
     */
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    /**
     * Creates a new builder for PluginManifest.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PluginManifest.
     */
    public static final class Builder {
        private String pluginId;
        private String version;
        private String description;
        private PluginTier tier;
        private Set<String> capabilities;
        private Set<PluginDependency> dependencies;
        private PluginResourceQuota resourceQuotas;
        private Map<String, Object> metadata;

        private Builder() {}

        public Builder pluginId(String pluginId) {
            this.pluginId = pluginId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder tier(PluginTier tier) {
            this.tier = tier;
            return this;
        }

        public Builder capabilities(Set<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder dependencies(Set<PluginDependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder resourceQuotas(PluginResourceQuota resourceQuotas) {
            this.resourceQuotas = resourceQuotas;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public PluginManifest build() {
            return new PluginManifest(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginManifest that = (PluginManifest) o;
        return Objects.equals(pluginId, that.pluginId) &&
               Objects.equals(version, that.version) &&
               Objects.equals(description, that.description) &&
               tier == that.tier &&
               Objects.equals(capabilities, that.capabilities) &&
               Objects.equals(resourceQuotas, that.resourceQuotas) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginId, version, description, tier, capabilities, resourceQuotas, metadata);
    }

    @Override
    public String toString() {
        return String.format("PluginManifest{id='%s', version='%s', tier=%s}", pluginId, version, tier);
    }
}
