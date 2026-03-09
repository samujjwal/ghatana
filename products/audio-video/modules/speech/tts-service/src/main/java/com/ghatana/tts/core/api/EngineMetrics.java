package com.ghatana.tts.core.api;

/**
 * TTS engine metrics.
 * 
 * @doc.type record
 * @doc.purpose Engine metrics container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EngineMetrics(
    float realTimeFactor,
    long memoryUsageBytes,
    int activeSessions,
    long totalSyntheses,
    float averageLatencyMs
) {
    public float getRealTimeFactor() { return realTimeFactor; }
    public long getMemoryUsageBytes() { return memoryUsageBytes; }
    public int getActiveSessions() { return activeSessions; }
    public long getTotalSyntheses() { return totalSyntheses; }
    public float getAverageLatencyMs() { return averageLatencyMs; }
}
