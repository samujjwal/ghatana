package com.ghatana.kernel.descriptor;

import java.time.Duration;
import java.util.Objects;

/**
 * Defines resource requirements for kernel components.
 *
 * <p>Resource requirements specify CPU, memory, storage, and network requirements
 * for kernel modules and plugins. These are used for scheduling and resource management.</p>
 *
 * @doc.type class
 * @doc.purpose Resource requirement specification for kernel component scheduling
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ResourceRequirements {
    
    private final int minCpuCores;
    private final int maxCpuCores;
    private final long minMemoryMb;
    private final long maxMemoryMb;
    private final long storageMb;
    private final int networkBandwidthMbps;
    private final boolean gpuRequired;
    private final int gpuMemoryMb;
    private final Duration maxExecutionTime;

    private ResourceRequirements(Builder builder) {
        this.minCpuCores = builder.minCpuCores;
        this.maxCpuCores = builder.maxCpuCores > 0 ? builder.maxCpuCores : builder.minCpuCores;
        this.minMemoryMb = builder.minMemoryMb;
        this.maxMemoryMb = builder.maxMemoryMb > 0 ? builder.maxMemoryMb : builder.minMemoryMb;
        this.storageMb = builder.storageMb;
        this.networkBandwidthMbps = builder.networkBandwidthMbps;
        this.gpuRequired = builder.gpuRequired;
        this.gpuMemoryMb = builder.gpuMemoryMb;
        this.maxExecutionTime = builder.maxExecutionTime;
    }

    public static ResourceRequirements defaultRequirements() {
        return new Builder().build();
    }

    // Getters
    public int getMinCpuCores() { return minCpuCores; }
    public int getMaxCpuCores() { return maxCpuCores; }
    public long getMinMemoryMb() { return minMemoryMb; }
    public long getMaxMemoryMb() { return maxMemoryMb; }
    public long getStorageMb() { return storageMb; }
    public int getNetworkBandwidthMbps() { return networkBandwidthMbps; }
    public boolean isGpuRequired() { return gpuRequired; }
    public int getGpuMemoryMb() { return gpuMemoryMb; }
    public Duration getMaxExecutionTime() { return maxExecutionTime; }

    // Builder
    public static class Builder {
        private int minCpuCores = 1;
        private int maxCpuCores = 0;
        private long minMemoryMb = 512;
        private long maxMemoryMb = 0;
        private long storageMb = 1024;
        private int networkBandwidthMbps = 100;
        private boolean gpuRequired = false;
        private int gpuMemoryMb = 0;
        private Duration maxExecutionTime;

        public Builder withMinCpuCores(int cores) {
            this.minCpuCores = cores;
            return this;
        }

        public Builder withMaxCpuCores(int cores) {
            this.maxCpuCores = cores;
            return this;
        }

        public Builder withMinMemoryMb(long memory) {
            this.minMemoryMb = memory;
            return this;
        }

        public Builder withMaxMemoryMb(long memory) {
            this.maxMemoryMb = memory;
            return this;
        }

        public Builder withStorageMb(long storage) {
            this.storageMb = storage;
            return this;
        }

        public Builder withNetworkBandwidthMbps(int bandwidth) {
            this.networkBandwidthMbps = bandwidth;
            return this;
        }

        public Builder withGpuRequired(boolean required) {
            this.gpuRequired = required;
            return this;
        }

        public Builder withGpuMemoryMb(int memory) {
            this.gpuMemoryMb = memory;
            return this;
        }

        public Builder withMaxExecutionTime(Duration time) {
            this.maxExecutionTime = time;
            return this;
        }

        public ResourceRequirements build() {
            return new ResourceRequirements(this);
        }
    }
}
