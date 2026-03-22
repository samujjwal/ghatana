package com.ghatana.agent.framework.memory;

/**
 * Memory statistics.
 * 
 * @doc.type class
 * @doc.purpose Memory storage statistics
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public final class MemoryStats {
    
    private final long episodeCount;
    private final long factCount;
    private final long policyCount;
    private final long preferenceCount;
    private final long totalSizeBytes;
    
    public MemoryStats(
            long episodeCount, 
            long factCount, 
            long policyCount, 
            long preferenceCount,
            long totalSizeBytes) {
        this.episodeCount = episodeCount;
        this.factCount = factCount;
        this.policyCount = policyCount;
        this.preferenceCount = preferenceCount;
        this.totalSizeBytes = totalSizeBytes;
    }
    
    public long getEpisodeCount() {
        return episodeCount;
    }
    
    public long getFactCount() {
        return factCount;
    }
    
    public long getPolicyCount() {
        return policyCount;
    }
    
    public long getPreferenceCount() {
        return preferenceCount;
    }
    
    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }
    
    public long getTotalCount() {
        return episodeCount + factCount + policyCount + preferenceCount;
    }
}
