package com.ghatana.appplatform.plugin.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime record of a registered plugin stored in the plugin registry.
 *
 * <p>Combines the immutable {@link PluginManifest} with mutable lifecycle state
 * ({@link PluginStatus}) and audit metadata.
 *
 * @doc.type  record
 * @doc.purpose Full plugin registration entity persisted to the plugin store
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public record PluginRegistration(
        UUID id,
        PluginManifest manifest,
        PluginStatus status,
        List<PluginCapability> approvedCapabilities,
        Instant registeredAt,
        Instant statusChangedAt,
        String registeredBy,
        String tenantId
) {

    public PluginRegistration {
        Objects.requireNonNull(id,       "id");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(status,   "status");
        approvedCapabilities = approvedCapabilities == null
                ? List.of() : List.copyOf(approvedCapabilities);
    }

    /** Returns a copy of this registration with the given status. */
    public PluginRegistration withStatus(PluginStatus newStatus) {
        return new PluginRegistration(id, manifest, newStatus, approvedCapabilities,
                registeredAt, Instant.now(), registeredBy, tenantId);
    }

    /** Returns a copy of this registration with the given approved capabilities. */
    public PluginRegistration withApprovedCapabilities(List<PluginCapability> caps) {
        return new PluginRegistration(id, manifest, status, caps,
                registeredAt, Instant.now(), registeredBy, tenantId);
    }

    /** Shortcut: plugin name. */
    public String pluginName()    { return manifest.name(); }

    /** Shortcut: plugin tier. */
    public PluginTier tier()      { return manifest.tier(); }

    /** Shortcut: plugin version. */
    public PluginVersion version() { return manifest.version(); }
}
