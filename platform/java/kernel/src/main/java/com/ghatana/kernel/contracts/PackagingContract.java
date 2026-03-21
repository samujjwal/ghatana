/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract for packaging surfaces: pack manifests, deployment units, lifecycle hooks.
 *
 * <p>A packaging contract declares the deployment artifacts a module produces,
 * its lifecycle hooks (install, upgrade, rollback, uninstall), and dependency
 * relationships with other packs. Aligns with the AppPlatform's
 * {@code PluginManifest} and {@code ReleaseManifestService} patterns.</p>
 *
 * @doc.type class
 * @doc.purpose Packaging contract for manifest and deployment declarations
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class PackagingContract extends KernelContract {

    /**
     * Pack tiers aligned with AppPlatform plugin tiering.
     */
    public enum PackTier {
        /** Core platform pack (kernel-level). */
        T1_CORE,
        /** Certified partner pack. */
        T2_CERTIFIED,
        /** Community/third-party pack. */
        T3_COMMUNITY
    }

    /**
     * Lifecycle phases that support hooks.
     */
    public enum LifecyclePhase {
        INSTALL,
        UPGRADE,
        ROLLBACK,
        UNINSTALL,
        HEALTH_CHECK
    }

    /**
     * Declares a lifecycle hook.
     */
    public record LifecycleHook(LifecyclePhase phase, String handlerClass, int timeoutMs) {
        public LifecycleHook {
            Objects.requireNonNull(phase, "phase required");
            Objects.requireNonNull(handlerClass, "handlerClass required");
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("timeoutMs must be positive: " + timeoutMs);
            }
        }
    }

    /**
     * Declares a pack dependency.
     */
    public record PackDependency(String packId, String versionRange, boolean optional) {
        public PackDependency {
            Objects.requireNonNull(packId, "packId required");
            Objects.requireNonNull(versionRange, "versionRange required");
        }
    }

    private final PackTier tier;
    private final List<LifecycleHook> lifecycleHooks;
    private final List<PackDependency> dependencies;
    private final String artifactChecksum;

    private PackagingContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              ContractFamily.PACKAGING, builder.metadata);
        this.tier = builder.tier;
        this.lifecycleHooks = builder.lifecycleHooks != null
            ? List.copyOf(builder.lifecycleHooks) : List.of();
        this.dependencies = builder.dependencies != null
            ? List.copyOf(builder.dependencies) : List.of();
        this.artifactChecksum = builder.artifactChecksum;
        validate();
    }

    public PackTier getTier() { return tier; }
    public List<LifecycleHook> getLifecycleHooks() { return lifecycleHooks; }
    public List<PackDependency> getDependencies() { return dependencies; }
    public String getArtifactChecksum() { return artifactChecksum; }

    @Override
    protected void validate() {
        super.validate();
        if (tier == null) {
            throw new IllegalArgumentException("Pack tier is required");
        }
    }

    /**
     * Creates a new builder for {@link PackagingContract}.
     */
    public static Builder builder(String contractId, String name, String version) {
        return new Builder(contractId, name, version);
    }

    /**
     * Fluent builder for {@link PackagingContract}.
     */
    public static final class Builder {
        private final String contractId;
        private final String name;
        private final String version;
        private Map<String, String> metadata = Map.of();
        private PackTier tier;
        private List<LifecycleHook> lifecycleHooks = List.of();
        private List<PackDependency> dependencies = List.of();
        private String artifactChecksum;

        private Builder(String contractId, String name, String version) {
            this.contractId = contractId;
            this.name = name;
            this.version = version;
        }

        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder tier(PackTier tier) { this.tier = tier; return this; }
        public Builder lifecycleHooks(List<LifecycleHook> hooks) { this.lifecycleHooks = hooks; return this; }
        public Builder dependencies(List<PackDependency> deps) { this.dependencies = deps; return this; }
        public Builder artifactChecksum(String checksum) { this.artifactChecksum = checksum; return this; }

        public PackagingContract build() { return new PackagingContract(this); }
    }
}
