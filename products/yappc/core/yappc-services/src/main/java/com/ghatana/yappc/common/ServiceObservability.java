package com.ghatana.yappc.common;

import com.ghatana.platform.observability.MetricsCollector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared observability helpers to keep service telemetry and audit payloads consistent.
 *
 * @doc.type class
 * @doc.purpose Shared telemetry helpers for YAPPC service observability
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class ServiceObservability {

    public static final String REDACTED_VALUE = "[REDACTED]";

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

    /**
     * Builds the canonical nine-tag set for a YAPPC critical flow metric.
     *
     * <p>Tag names are fixed across all critical flows to enable cross-flow dashboards
     * and alerting in Grafana. Unknown / absent values are normalised to {@code "unknown"}
     * or {@code "none"} to avoid high-cardinality gaps in Prometheus.
     *
     * @param tenantId      owning tenant identifier
     * @param workspaceId   workspace identifier within the tenant
     * @param projectId     project identifier within the workspace
     * @param phase         lifecycle phase (e.g. {@code "DESIGN"}, {@code "BUILD"})
     * @param operation     specific operation (e.g. {@code "create"}, {@code "validate"})
     * @param outcome       result of the operation (e.g. {@code "SUCCESS"}, {@code "BLOCKED"})
     * @param degraded      {@code true} when the response was served in a degraded state
     * @param errorClass    simple class name of the exception, or {@code null} when none
     * @param correlationId request correlation / trace identifier
     * @return unmodifiable map with exactly nine entries, one per canonical tag
     */
    public static Map<String, String> flowTags(
            String tenantId,
            String workspaceId,
            String projectId,
            String phase,
            String operation,
            String outcome,
            boolean degraded,
            String errorClass,
            String correlationId) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("tenantId",      isBlank(tenantId)     ? "unknown" : tenantId);
        tags.put("workspaceId",   isBlank(workspaceId)  ? "unknown" : workspaceId);
        tags.put("projectId",     isBlank(projectId)    ? "unknown" : projectId);
        tags.put("phase",         isBlank(phase)        ? "unknown" : phase);
        tags.put("operation",     isBlank(operation)    ? "unknown" : operation);
        tags.put("outcome",       isBlank(outcome)      ? "unknown" : outcome);
        tags.put("degraded",      String.valueOf(degraded));
        tags.put("errorClass",    isBlank(errorClass)   ? "none"    : errorClass);
        tags.put("correlationId", isBlank(correlationId)? "none"    : correlationId);
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Redacts sensitive prompt/input/output/generated-content values before they are
     * written to telemetry, logs, or evidence metadata.
     *
     * @param data source metadata
     * @return immutable redacted metadata map
     */
    public static Map<String, Object> redactSensitiveFields(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> redacted = new LinkedHashMap<>();
        data.forEach((key, value) -> redacted.put(key, redactValue(key, value)));
        return Collections.unmodifiableMap(redacted);
    }

    @SuppressWarnings("unchecked")
    private static Object redactValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return REDACTED_VALUE;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) ->
                    redacted.put(String.valueOf(nestedKey), redactValue(String.valueOf(nestedKey), nestedValue)));
            return Collections.unmodifiableMap(redacted);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> redacted = new ArrayList<>(collection.size());
            for (Object item : collection) {
                redacted.add(item instanceof Map<?, ?> map
                        ? redactValue(key, (Map<String, Object>) map)
                        : item);
            }
            return List.copyOf(redacted);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (isBlank(key)) {
            return false;
        }
        String normalized = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.equals("prompt")
                || normalized.equals("rawprompt")
                || normalized.equals("input")
                || normalized.equals("output")
                || normalized.equals("payload")
                || normalized.equals("content")
                || normalized.equals("rawcontent")
                || normalized.equals("sourcecontent")
                || normalized.equals("generatedcontent")
                || normalized.equals("generatedsource")
                || normalized.equals("generatedcode")
                || normalized.equals("secret")
                || normalized.equals("password")
                || normalized.equals("apikey")
                || normalized.equals("credential")
                || normalized.equals("token");
    }
}
