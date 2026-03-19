package com.ghatana.kernel.plugin;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Plugin manifest containing metadata about a kernel plugin.
 *
 * <p>The manifest provides static information about a plugin that can be
 * inspected before the plugin is loaded or instantiated.</p>
 *
 * @doc.type class
 * @doc.purpose Plugin metadata container for discovery and dependency analysis
 * @doc.layer core
 * @doc.pattern ValueObject, Builder
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class PluginManifest {

    private final String pluginId;
    private final String version;
    private final String description;
    private final String author;
    private final String license;
    private final Set<KernelCapability> capabilities;
    private final Set<KernelDependency> dependencies;
    private final Set<String> requiredKernelVersions;
    private final Set<String> tags;

    private PluginManifest(Builder builder) {
        this.pluginId = Objects.requireNonNull(builder.pluginId, "pluginId cannot be null");
        this.version = Objects.requireNonNull(builder.version, "version cannot be null");
        this.description = builder.description != null ? builder.description : "";
        this.author = builder.author != null ? builder.author : "Unknown";
        this.license = builder.license != null ? builder.license : "Unknown";
        this.capabilities = Set.copyOf(builder.capabilities);
        this.dependencies = Set.copyOf(builder.dependencies);
        this.requiredKernelVersions = Set.copyOf(builder.requiredKernelVersions);
        this.tags = Set.copyOf(builder.tags);
    }

    // Getters
    public String getPluginId() { return pluginId; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getLicense() { return license; }
    public Set<KernelCapability> getCapabilities() { return capabilities; }
    public Set<KernelDependency> getDependencies() { return dependencies; }
    public Set<String> getRequiredKernelVersions() { return requiredKernelVersions; }
    public Set<String> getTags() { return tags; }

    /**
     * Checks if this plugin is compatible with a kernel version.
     *
     * @param kernelVersion the kernel version to check
     * @return true if compatible
     */
    public boolean isCompatibleWithKernel(String kernelVersion) {
        if (requiredKernelVersions.isEmpty()) {
            return true;
        }
        return requiredKernelVersions.stream()
            .anyMatch(v -> isVersionCompatible(kernelVersion, v));
    }

    /**
     * Simple version compatibility check.
     */
    private boolean isVersionCompatible(String actual, String required) {
        // Simple prefix match for now
        // In production, use proper semver comparison
        return actual.startsWith(required.split("\\.")[0]);
    }

    @Override
    public String toString() {
        return String.format("PluginManifest{id='%s', version='%s', capabilities=%d}",
            pluginId, version, capabilities.size());
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String pluginId;
        private String version;
        private String description;
        private String author;
        private String license;
        private Set<KernelCapability> capabilities = new HashSet<>();
        private Set<KernelDependency> dependencies = new HashSet<>();
        private Set<String> requiredKernelVersions = new HashSet<>();
        private Set<String> tags = new HashSet<>();

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

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder license(String license) {
            this.license = license;
            return this;
        }

        public Builder capability(KernelCapability capability) {
            this.capabilities.add(capability);
            return this;
        }

        public Builder dependency(KernelDependency dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder requiredKernelVersion(String version) {
            this.requiredKernelVersions.add(version);
            return this;
        }

        public Builder tag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public PluginManifest build() {
            return new PluginManifest(this);
        }
    }
}
