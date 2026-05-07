package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Product-level business KPI metrics for Data Cloud runtime operations.
 *
 * @doc.type class
 * @doc.purpose Emits tenant-scoped entity, event, and governance KPI metrics
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DataCloudBusinessMetrics {

    private static final Logger log = LoggerFactory.getLogger(DataCloudBusinessMetrics.class);

    public static final String METRIC_ENTITY_TOTAL = "datacloud_entity_operations_total";
    public static final String METRIC_ENTITY_DURATION = "datacloud_entity_operation_duration_seconds";
    public static final String METRIC_EVENT_APPEND_TOTAL = "datacloud_event_append_total";
    public static final String METRIC_EVENT_APPEND_DURATION = "datacloud_event_append_duration_seconds";
    public static final String METRIC_GOVERNANCE_TOTAL = "datacloud_governance_operations_total";
    public static final String METRIC_GOVERNANCE_DURATION = "datacloud_governance_operation_duration_seconds";

    private static final DataCloudBusinessMetrics NOOP = new DataCloudBusinessMetrics(null);

    private final MetricsCollector metricsCollector;

    public DataCloudBusinessMetrics(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    public static DataCloudBusinessMetrics noop() {
        return NOOP;
    }

    public void recordEntityOperation(String operation, String collection, String tenantId, String status, long durationMs) {
        MeterRegistry registry = registry();
        if (registry == null) {
            return;
        }
        List<Tag> tags = List.of(
                Tag.of("operation", normalize(operation)),
                Tag.of("collection", normalize(collection)),
                Tag.of("tenant", normalizeTenant(tenantId)),
                Tag.of("status", normalize(status)));
        try {
            registry.counter(METRIC_ENTITY_TOTAL, tags).increment();
            registry.timer(METRIC_ENTITY_DURATION, tags).record(Math.max(durationMs, 0L), TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            log.debug("entity KPI metrics emission failed: {}", exception.getMessage());
        }
    }

    public void recordEventAppend(String tenantId, String status, long durationMs, String eventType) {
        MeterRegistry registry = registry();
        if (registry == null) {
            return;
        }
        List<Tag> tags = List.of(
                Tag.of("tenant", normalizeTenant(tenantId)),
                Tag.of("status", normalize(status)),
                Tag.of("type", normalize(eventType)));
        try {
            registry.counter(METRIC_EVENT_APPEND_TOTAL, tags).increment();
            registry.timer(METRIC_EVENT_APPEND_DURATION, tags).record(Math.max(durationMs, 0L), TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            log.debug("event KPI metrics emission failed: {}", exception.getMessage());
        }
    }

    public void recordGovernanceOperation(String type, String tenantId, String status, long durationMs) {
        MeterRegistry registry = registry();
        if (registry == null) {
            return;
        }
        List<Tag> tags = List.of(
                Tag.of("type", normalize(type)),
                Tag.of("tenant", normalizeTenant(tenantId)),
                Tag.of("status", normalize(status)));
        try {
            registry.counter(METRIC_GOVERNANCE_TOTAL, tags).increment();
            registry.timer(METRIC_GOVERNANCE_DURATION, tags).record(Math.max(durationMs, 0L), TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            log.debug("governance KPI metrics emission failed: {}", exception.getMessage());
        }
    }

    private MeterRegistry registry() {
        if (metricsCollector == null) {
            return null;
        }
        try {
            return metricsCollector.getMeterRegistry();
        } catch (RuntimeException exception) {
            log.debug("metrics collector registry unavailable: {}", exception.getMessage());
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String normalizeTenant(String tenantId) {
        String normalized = normalize(tenantId);
        if (normalized.length() <= 64) {
            return normalized;
        }
        return normalized.substring(0, 64);
    }
}