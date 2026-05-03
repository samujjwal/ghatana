package com.ghatana.kernel.plugin;

/**
 * Plugin resource quotas.
 *
 * <p>Defines the resource limits for plugin execution:
 * <ul>
 *   <li>Maximum CPU usage</li>
 *   <li>Maximum memory usage</li>
 *   <li>Maximum execution time</li>
 *   <li>Maximum concurrent operations</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Plugin resource quotas - CPU, memory, execution time, concurrency limits
 * @doc.layer core
 * @doc.pattern ValueObject, Builder
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class PluginResourceQuota {

    private final long maxCpuPercent;
    private final long maxMemoryMb;
    private final long maxExecutionTimeMs;
    private final int maxConcurrentOperations;
    private final int maxFileDescriptors;
    private final long maxNetworkConnections;
    private final PluginTier tier;

    private PluginResourceQuota(Builder builder) {
        this.maxCpuPercent = builder.maxCpuPercent;
        this.maxMemoryMb = builder.maxMemoryMb;
        this.maxExecutionTimeMs = builder.maxExecutionTimeMs;
        this.maxConcurrentOperations = builder.maxConcurrentOperations;
        this.maxFileDescriptors = builder.maxFileDescriptors;
        this.maxNetworkConnections = builder.maxNetworkConnections;
        this.tier = builder.tier;
    }

    // Getters
    public long getMaxCpuPercent() { return maxCpuPercent; }
    public long getMaxMemoryMb() { return maxMemoryMb; }
    public long getMaxExecutionTimeMs() { return maxExecutionTimeMs; }
    public int getMaxConcurrentOperations() { return maxConcurrentOperations; }
    public int getMaxFileDescriptors() { return maxFileDescriptors; }
    public long getMaxNetworkConnections() { return maxNetworkConnections; }
    public PluginTier getTier() { return tier; }

    /**
     * Returns default resource quotas for plugins.
     *
     * @return default resource quotas
     */
    public static PluginResourceQuota defaults() {
        return new Builder()
            .maxCpuPercent(50)
            .maxMemoryMb(512)
            .maxExecutionTimeMs(30000)
            .maxConcurrentOperations(10)
            .maxFileDescriptors(100)
            .maxNetworkConnections(0)
            .tier(PluginTier.T2)
            .build();
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long maxCpuPercent = 50;
        private long maxMemoryMb = 512;
        private long maxExecutionTimeMs = 30000;
        private int maxConcurrentOperations = 10;
        private int maxFileDescriptors = 100;
        private long maxNetworkConnections = 0;
        private PluginTier tier = PluginTier.T2;

        public Builder maxCpuPercent(long maxCpuPercent) {
            this.maxCpuPercent = maxCpuPercent;
            return this;
        }

        public Builder maxMemoryMb(long maxMemoryMb) {
            this.maxMemoryMb = maxMemoryMb;
            return this;
        }

        public Builder maxExecutionTimeMs(long maxExecutionTimeMs) {
            this.maxExecutionTimeMs = maxExecutionTimeMs;
            return this;
        }

        public Builder maxConcurrentOperations(int maxConcurrentOperations) {
            this.maxConcurrentOperations = maxConcurrentOperations;
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

        public Builder tier(PluginTier tier) {
            this.tier = tier;
            return this;
        }

        public PluginResourceQuota build() {
            return new PluginResourceQuota(this);
        }
    }
}
