package com.ghatana.yappc.common;

import com.ghatana.platform.observability.MetricsCollector;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
}
