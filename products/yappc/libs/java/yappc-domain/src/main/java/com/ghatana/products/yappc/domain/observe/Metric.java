/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — Canonical Metric Value Object
 */
package com.ghatana.products.yappc.domain.observe;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical domain metric value object.
 *
 * <p>Use this class for all runtime, business, and observability metrics within YAPPC.
 * Includes project and tenant dimensions for multi-tenant isolation.
 *
 * @doc.type record
 * @doc.purpose Canonical multi-tenant runtime metric value object
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record Metric(
        String metricId,
        String name,
        double value,
        String unit,
        Instant timestamp,
        String projectId,
        String tenantId,
        Map<String, String> tags
) {

    /**
     * Creates a {@code Metric} with an auto-generated ID and current timestamp.
     *
     * @param name      metric name (e.g. {@code "api.latency.p99"})
     * @param value     numeric measurement
     * @param unit      unit of measure (e.g. {@code "ms"}, {@code "count"})
     * @param projectId project this metric belongs to
     * @param tenantId  tenant context
     * @return a new {@code Metric} instance
     */
    public static Metric of(String name, double value, String unit,
                            String projectId, String tenantId) {
        return new Metric(UUID.randomUUID().toString(), name, value, unit,
                Instant.now(), projectId, tenantId, Map.of());
    }

    /**
     * Creates a {@code Metric} with tags and an auto-generated ID and current timestamp.
     *
     * @param name      metric name
     * @param value     numeric measurement
     * @param unit      unit of measure
     * @param projectId project this metric belongs to
     * @param tenantId  tenant context
     * @param tags      additional label dimensions
     * @return a new {@code Metric} instance
     */
    public static Metric of(String name, double value, String unit,
                            String projectId, String tenantId, Map<String, String> tags) {
        return new Metric(UUID.randomUUID().toString(), name, value, unit,
                Instant.now(), projectId, tenantId, tags);
    }

    /**
     * Returns a new {@code Metric} instance with the given ID.
     * Useful for constructing metrics with externally assigned identifiers.
     *
     * @param id the metric identifier
     * @return a copy with the new ID
     */
    public Metric withId(String id) {
        return new Metric(id, name, value, unit, timestamp, projectId, tenantId, tags);
    }

    /**
     * Returns a new {@code Metric} with additional tags merged in.
     *
     * @param additionalTags tags to merge (duplicate keys are overwritten)
     * @return a new {@code Metric} with merged tags
     */
    public Metric withTags(Map<String, String> additionalTags) {
        var merged = new java.util.HashMap<>(tags);
        merged.putAll(additionalTags);
        return new Metric(metricId, name, value, unit, timestamp, projectId, tenantId,
                Map.copyOf(merged));
    }
}
