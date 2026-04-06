/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import java.util.Objects;

/**
 * Resource quotas for a plugin.
 *
 * @doc.type class
 * @doc.purpose Plugin resource quotas - memory, CPU, file descriptors with tier defaults
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class PluginResourceQuota {

    private final PluginTier tier;
    private final int maxMemoryMB;
    private final int maxCpuPercent;
    private final int maxFileDescriptors;
    private final long maxNetworkConnections;

    private PluginResourceQuota(Builder builder) {
        this.tier = Objects.requireNonNull(builder.tier, "tier");
        this.maxMemoryMB = builder.maxMemoryMB;
        this.maxCpuPercent = builder.maxCpuPercent;
        this.maxFileDescriptors = builder.maxFileDescriptors;
        this.maxNetworkConnections = builder.maxNetworkConnections;
    }

    /**
     * Gets the plugin tier.
     *
     * @return the tier
     */
    public PluginTier tier() {
        return tier;
    }

    /**
     * Gets the maximum memory in MB.
     *
     * @return max memory in MB
     */
    public int maxMemoryMB() {
        return maxMemoryMB;
    }

    /**
     * Gets the maximum CPU percentage.
     *
     * @return max CPU percentage
     */
    public int maxCpuPercent() {
        return maxCpuPercent;
    }

    /**
     * Gets the maximum file descriptors.
     *
     * @return max file descriptors
     */
    public int maxFileDescriptors() {
        return maxFileDescriptors;
    }

    /**
     * Gets the maximum network connections.
     *
     * @return max network connections
     */
    public long maxNetworkConnections() {
        return maxNetworkConnections;
    }

    /**
     * Creates default quotas for a tier.
     *
     * @return default quotas
     */
    public static PluginResourceQuota defaults() {
        return defaults(PluginTier.T2);
    }

    /**
     * Creates default quotas for a specific tier.
     *
     * @param tier the plugin tier
     * @return default quotas for the tier
     */
    public static PluginResourceQuota defaults(PluginTier tier) {
        return switch (tier) {
            case T1 -> new PluginResourceQuota(
                new Builder().tier(PluginTier.T1)
                    .maxMemoryMB(64)
                    .maxCpuPercent(5)
                    .maxFileDescriptors(10)
                    .maxNetworkConnections(0)
            );
            case T2 -> new PluginResourceQuota(
                new Builder().tier(PluginTier.T2)
                    .maxMemoryMB(512)
                    .maxCpuPercent(25)
                    .maxFileDescriptors(100)
                    .maxNetworkConnections(0)
            );
            case T3 -> new PluginResourceQuota(
                new Builder().tier(PluginTier.T3)
                    .maxMemoryMB(2048)
                    .maxCpuPercent(75)
                    .maxFileDescriptors(1000)
                    .maxNetworkConnections(100)
            );
        };
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
        private PluginTier tier = PluginTier.T2;
        private int maxMemoryMB = 512;
        private int maxCpuPercent = 25;
        private int maxFileDescriptors = 100;
        private long maxNetworkConnections = 0;

        private Builder() {}

        public Builder tier(PluginTier tier) {
            this.tier = tier;
            return this;
        }

        public Builder maxMemoryMB(int maxMemoryMB) {
            this.maxMemoryMB = maxMemoryMB;
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

        public Builder maxNetworkConnections(long maxNetworkConnections) {
            this.maxNetworkConnections = maxNetworkConnections;
            return this;
        }

        public PluginResourceQuota build() {
            return new PluginResourceQuota(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginResourceQuota that = (PluginResourceQuota) o;
        return tier == that.tier &&
               maxMemoryMB == that.maxMemoryMB &&
               maxCpuPercent == that.maxCpuPercent &&
               maxFileDescriptors == that.maxFileDescriptors &&
               maxNetworkConnections == that.maxNetworkConnections;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tier, maxMemoryMB, maxCpuPercent, maxFileDescriptors, maxNetworkConnections);
    }

    @Override
    public String toString() {
        return String.format("PluginResourceQuota{tier=%s, memory=%dMB, cpu=%d%%, fds=%d, connections=%d}",
            tier, maxMemoryMB, maxCpuPercent, maxFileDescriptors, maxNetworkConnections);
    }
}
