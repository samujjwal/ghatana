package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata describing a Plugin.
 * <p>
 * This is the canonical plugin metadata record for the entire Ghatana platform.
 * It is a superset of all plugin metadata needs across platform, data-cloud, and
 * event-cloud plugin SPIs.
 * <p>
 * Use the {@link #builder()} method for convenient construction with defaults.
 *
 * @param id Unique identifier of the plugin (e.g., "com.ghatana.postgres")
 * @param name Human-readable name
 * @param version Semantic version
 * @param description Brief description
 * @param type Plugin type category
 * @param author Author or organization (also known as "vendor")
 * @param license License identifier (e.g., "Apache-2.0")
 * @param tags Searchable tags
 * @param links Documentation and support links
 * @param dependencies Required other plugins
 * @param compatibility Version compatibility matrix (nullable)
 * @param capabilities String-based capability descriptors for discovery
 * @param properties Arbitrary plugin-specific properties
 *
 * @doc.type record
 * @doc.purpose Plugin metadata
 * @doc.layer core
 */
public record PluginMetadata(
    @NotNull String id,
    @NotNull String name,
    @NotNull String version,
    @NotNull String description,
    @NotNull PluginType type,
    @NotNull String author,
    @NotNull String license,
    @NotNull Set<String> tags,
    @NotNull Map<String, String> links,
    @NotNull Set<PluginDependency> dependencies,
    @Nullable PluginCompatibility compatibility,
    @NotNull Set<String> capabilities,
    @NotNull Map<String, Object> properties
) {
    /**
     * Compact constructor with validation (backwards-compatible: capabilities and properties default).
     */
    public PluginMetadata {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(version, "version must not be null");
        if (capabilities == null) capabilities = Set.of();
        if (properties == null) properties = Map.of();
        if (tags == null) tags = Set.of();
        if (links == null) links = Map.of();
        if (dependencies == null) dependencies = Set.of();
    }

    /**
     * Backwards-compatible constructor without capabilities and properties.
     */
    public PluginMetadata(
            @NotNull String id,
            @NotNull String name,
            @NotNull String version,
            @NotNull String description,
            @NotNull PluginType type,
            @NotNull String author,
            @NotNull String license,
            @NotNull Set<String> tags,
            @NotNull Map<String, String> links,
            @NotNull Set<PluginDependency> dependencies,
            @Nullable PluginCompatibility compatibility
    ) {
        this(id, name, version, description, type, author, license,
             tags, links, dependencies, compatibility, Set.of(), Map.of());
    }

    /**
     * Returns the author (also known as "vendor") of this plugin.
     */
    public String vendor() {
        return author;
    }

    /**
     * Returns true if this plugin declares the given string capability.
     */
    public boolean hasCapability(@NotNull String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Creates a new builder for constructing {@link PluginMetadata} instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for convenient construction of {@link PluginMetadata} instances.
     */
    public static final class Builder {
        private String id;
        private String name;
        private String version = "1.0.0";
        private String description = "";
        private PluginType type = PluginType.CUSTOM;
        private String author = "Ghatana";
        private String license = "Proprietary";
        private Set<String> tags = Set.of();
        private Map<String, String> links = Map.of();
        private Set<PluginDependency> dependencies = Set.of();
        private PluginCompatibility compatibility;
        private Set<String> capabilities = Set.of();
        private Map<String, Object> properties = Map.of();

        private Builder() {}

        public Builder id(@NotNull String id) { this.id = id; return this; }
        public Builder name(@NotNull String name) { this.name = name; return this; }
        public Builder version(@NotNull String version) { this.version = version; return this; }
        public Builder description(@NotNull String description) { this.description = description; return this; }
        public Builder type(@NotNull PluginType type) { this.type = type; return this; }
        public Builder author(@NotNull String author) { this.author = author; return this; }
        /** Alias for {@link #author(String)}. */
        public Builder vendor(@NotNull String vendor) { this.author = vendor; return this; }
        public Builder license(@NotNull String license) { this.license = license; return this; }
        public Builder tags(@NotNull Set<String> tags) { this.tags = tags; return this; }
        public Builder links(@NotNull Map<String, String> links) { this.links = links; return this; }
        public Builder dependencies(@NotNull Set<PluginDependency> dependencies) { this.dependencies = dependencies; return this; }
        public Builder compatibility(@Nullable PluginCompatibility compatibility) { this.compatibility = compatibility; return this; }
        public Builder capabilities(@NotNull Set<String> capabilities) { this.capabilities = capabilities; return this; }
        public Builder capability(@NotNull String... capabilities) { this.capabilities = Set.of(capabilities); return this; }
        public Builder properties(@NotNull Map<String, Object> properties) { this.properties = properties; return this; }

        public PluginMetadata build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Plugin id is required");
            if (name == null || name.isBlank()) name = id;
            return new PluginMetadata(id, name, version, description, type, author, license,
                tags, links, dependencies, compatibility, capabilities, properties);
        }
    }
}
