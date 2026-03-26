package com.ghatana.tts.core.api;

/**
 * Runtime performance metrics for the TTS engine.
 *
 * @doc.type record
 * @doc.purpose Carries TTS engine telemetry for observability
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EngineMetrics(
        float realTimeFactor,
        long memoryUsageBytes,
        int activeSessions,
        long totalSyntheses,
        float averageLatencyMs
) {}
