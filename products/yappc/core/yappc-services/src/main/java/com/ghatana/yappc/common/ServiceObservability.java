package com.ghatana.yappc.common;

import com.ghatana.platform.observability.MetricsCollector;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared observability helpers to keep service telemetry and audit payloads consistent.
 */
public final class ServiceObservability {

    private ServiceObservability() {
    }

    public static Map<String, String> tenantTag(String tenantId) {
        return Map.of("tenant", isBlank(tenantId) ? "unknown" : tenantId);
    }

    public static Map<String, String> errorTag(Throwable throwable) {
        return Map.of("error", throwable == null ? "unknown" : throwable.getClass().getSimpleName());
    }

    public static Map<String, Object> auditEvent(String action, Object input, Object output) {
        return Map.of(
            "action", action,
            "timestamp", Instant.now().toEpochMilli(),
            "input", String.valueOf(input),
            "output", String.valueOf(output)
        );
    }

    public static void incrementSuccess(MetricsCollector metrics, String metricPrefix, Map<String, String> tags) {
        metrics.incrementCounter(metricPrefix + ".success", tags == null ? Map.of() : tags);
    }

    public static void incrementFailure(
            MetricsCollector metrics,
            String metricPrefix,
            Throwable throwable,
            Map<String, String> tags) {
        Map<String, String> mergedTags = new HashMap<>();
        if (tags != null) {
            mergedTags.putAll(tags);
        }
        mergedTags.putAll(errorTag(throwable));
        metrics.incrementCounter(metricPrefix + ".error", mergedTags);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
