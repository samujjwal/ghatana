package com.ghatana.yappc.ai.router;

/**
 * Cache statistics for monitoring.
 * 
 * @doc.type record
 * @doc.purpose Cache performance metrics
 
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public record CacheStatistics(
    int size,
    long hits,
    long misses,
    double hitRate
) {
    @Override
    public String toString() {
        return String.format("CacheStatistics[size=%d, hits=%d, misses=%d, hitRate=%.2f%%]",
            size, hits, misses, hitRate * 100);
    }
}
