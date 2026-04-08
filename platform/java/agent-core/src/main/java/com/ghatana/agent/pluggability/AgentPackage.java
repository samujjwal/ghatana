/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable descriptor of a deployable agent package.
 *
 * <p>An {@code AgentPackage} aggregates an agent's identity, its
 * {@link AgentCapabilityManifest}, the class name of its implementation,
 * the loading source, and an optional checksum for integrity verification.
 *
 * @param packageId          unique package identifier
 * @param manifest           capability manifest for this agent
 * @param implementationClass fully-qualified implementation class name
 * @param source             origin of this package
 * @param releaseState       lifecycle state of this package
 * @param checksum           optional SHA-256 hex checksum; null if not verified
 * @param registeredAt       UTC timestamp of package registration
 * @param metadata           additional annotations (immutable)
 *
 * @doc.type class
 * @doc.purpose Immutable agent package descriptor for runtime loading and hot-swap
 * @doc.layer platform
 * @doc.pattern Record
 */
public record AgentPackage(
        @NotNull String packageId,
        @NotNull AgentCapabilityManifest manifest,
        @NotNull String implementationClass,
        @NotNull AgentPackageSource source,
        @NotNull ReleaseState releaseState,
        @Nullable String checksum,
        @NotNull Instant registeredAt,
        @NotNull Map<String, String> metadata
) {
    /** Compact constructor — validates required fields and makes collections immutable. */
    public AgentPackage {
        if (packageId == null || packageId.isBlank()) {
            throw new IllegalArgumentException("packageId must not be blank");
        }
        Objects.requireNonNull(manifest, "manifest");
        if (implementationClass == null || implementationClass.isBlank()) {
            throw new IllegalArgumentException("implementationClass must not be blank");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(releaseState, "releaseState");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(metadata, "metadata");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Returns the agent ID from the embedded manifest.
     */
    public String agentId() {
        return manifest.agentId();
    }

    /**
     * Returns the agent version from the embedded manifest.
     */
    public String agentVersion() {
        return manifest.agentVersion();
    }

    /** Returns {@code true} when a checksum is present for integrity verification. */
    public boolean hasChecksum() {
        return checksum != null && !checksum.isBlank();
    }

    /**
     * Lifecycle state of a deployable agent package.
     */
    public enum ReleaseState {
        /** Under development; not suitable for production dispatch. */
        DRAFT,
        /** Validated and released to a staging slot. */
        STAGING,
        /** Fully validated; may be loaded into production dispatch slots. */
        STABLE,
        /** Deprecated; still loadable but should be replaced. */
        DEPRECATED,
        /** Removed; the package must not be loaded. */
        RETIRED
    }

    // ─── Builder ─────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link AgentPackage}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder — all required fields must be set before calling {@link #build()}.
     */
    public static final class Builder {

        private String packageId;
        private AgentCapabilityManifest manifest;
        private String implementationClass;
        private AgentPackageSource source = AgentPackageSource.DYNAMIC;
        private ReleaseState releaseState = ReleaseState.STABLE;
        private String checksum;
        private Instant registeredAt = Instant.now();
        private Map<String, String> metadata = Map.of();

        private Builder() {}

        public Builder packageId(String packageId) {
            this.packageId = packageId;
            return this;
        }

        public Builder manifest(AgentCapabilityManifest manifest) {
            this.manifest = manifest;
            return this;
        }

        public Builder implementationClass(String implementationClass) {
            this.implementationClass = implementationClass;
            return this;
        }

        public Builder source(AgentPackageSource source) {
            this.source = source;
            return this;
        }

        public Builder releaseState(ReleaseState releaseState) {
            this.releaseState = releaseState;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder registeredAt(Instant registeredAt) {
            this.registeredAt = registeredAt;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds the {@link AgentPackage}.
         *
         * @throws IllegalArgumentException if required fields are missing
         */
        public AgentPackage build() {
            return new AgentPackage(packageId, manifest, implementationClass,
                    source, releaseState, checksum, registeredAt, metadata);
        }
    }
}
