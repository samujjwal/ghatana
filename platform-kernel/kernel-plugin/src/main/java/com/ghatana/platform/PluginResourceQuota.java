/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import java.util.Objects;

/**
 * Resource quotas for a plugin - delegates to kernel-core PluginResourceQuota.
 *
 * @doc.type class
 * @doc.purpose Plugin resource quotas - delegates to kernel-core
 * @doc.layer platform
 * @doc.pattern Adapter
 * @author Ghatana Platform Team
 * @since 1.0.0
 * @deprecated Use {@link com.ghatana.kernel.plugin.PluginResourceQuota} from kernel-core instead
 */
@Deprecated(forRemoval = true, since = "1.0.0")
public final class PluginResourceQuota {

    private final com.ghatana.kernel.plugin.PluginResourceQuota kernelQuota;

    private PluginResourceQuota(com.ghatana.kernel.plugin.PluginResourceQuota kernelQuota) {
        this.kernelQuota = kernelQuota;
    }

    /**
     * Gets the maximum memory in MB.
     *
     * @return max memory in MB
     */
    public int maxMemoryMB() {
        return (int) kernelQuota.getMaxMemoryMb();
    }

    /**
     * Gets the maximum CPU percentage.
     *
     * @return max CPU percentage
     */
    public int maxCpuPercent() {
        return (int) kernelQuota.getMaxCpuPercent();
    }

    /**
     * Gets the maximum file descriptors.
     *
     * @return max file descriptors
     */
    public int maxFileDescriptors() {
        return kernelQuota.getMaxFileDescriptors();
    }

    /**
     * Gets the plugin tier.
     *
     * @return the tier
     */
    public PluginTier tier() {
        return fromKernelTier(kernelQuota.getTier());
    }

    private static PluginTier fromKernelTier(com.ghatana.kernel.plugin.PluginTier kernelTier) {
        switch (kernelTier) {
            case T1: return PluginTier.T1;
            case T2: return PluginTier.T2;
            case T3: return PluginTier.T3;
            default: return PluginTier.T2;
        }
    }

    /**
     * Creates default quotas.
     *
     * @return default quotas
     */
    public static PluginResourceQuota defaults() {
        return new PluginResourceQuota(com.ghatana.kernel.plugin.PluginResourceQuota.defaults());
    }

    /**
     * Creates a new builder for PluginResourceQuota.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PluginResourceQuota.
     */
    public static final class Builder {
        private long maxCpuPercent = 50;
        private long maxMemoryMb = 512;
        private long maxExecutionTimeMs = 30000;
        private int maxConcurrentOperations = 10;
        private int maxFileDescriptors = 100;
        private com.ghatana.kernel.plugin.PluginTier tier = com.ghatana.kernel.plugin.PluginTier.T2;

        public Builder maxMemoryMB(int maxMemoryMB) {
            this.maxMemoryMb = maxMemoryMB;
            return this;
        }

        public Builder maxCpuPercent(int maxCpuPercent) {
            this.maxCpuPercent = maxCpuPercent;
            return this;
        }

        public Builder maxFileDescriptors(int maxFileDescriptors) {
            this.maxFileDescriptors = maxFileDescriptors;
            return this;
        }

        public Builder tier(com.ghatana.kernel.plugin.PluginTier tier) {
            this.tier = tier;
            return this;
        }

        public PluginResourceQuota build() {
            return new PluginResourceQuota(
                com.ghatana.kernel.plugin.PluginResourceQuota.builder()
                    .maxCpuPercent(maxCpuPercent)
                    .maxMemoryMb(maxMemoryMb)
                    .maxExecutionTimeMs(maxExecutionTimeMs)
                    .maxConcurrentOperations(maxConcurrentOperations)
                    .maxFileDescriptors(maxFileDescriptors)
                    .tier(tier)
                    .build()
            );
        }
    }

    /**
     * Converts to kernel-core PluginResourceQuota.
     *
     * @return kernel-core PluginResourceQuota
     */
    public com.ghatana.kernel.plugin.PluginResourceQuota toKernelQuota() {
        return kernelQuota;
    }
}
