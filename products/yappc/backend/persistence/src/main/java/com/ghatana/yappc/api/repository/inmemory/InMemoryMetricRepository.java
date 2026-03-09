/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.yappc.api.domain.Metric;
import com.ghatana.yappc.api.domain.Metric.*;
import com.ghatana.yappc.api.repository.MetricRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of MetricRepository.
 *
 * @doc.type class
 * @doc.purpose In-memory storage for metrics
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryMetricRepository implements MetricRepository {

    private final Map<String, Map<UUID, Metric>> tenantMetrics = new ConcurrentHashMap<>();

    private Map<UUID, Metric> getMetricMap(String tenantId) {
        return tenantMetrics.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Promise<Metric> save(Metric metric) {
        if (metric.getId() == null) {
            metric.setId(UUID.randomUUID());
        }
        getMetricMap(metric.getTenantId()).put(metric.getId(), metric);
        return Promise.of(metric);
    }

    @Override
    public Promise<Metric> findById(String tenantId, UUID id) {
        return Promise.of(getMetricMap(tenantId).get(id));
    }

    @Override
    public Promise<List<Metric>> findByProject(String tenantId, String projectId) {
        return Promise.of(
            getMetricMap(tenantId).values().stream()
                .filter(m -> projectId.equals(m.getProjectId()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Metric>> findByName(String tenantId, String name) {
        return Promise.of(
            getMetricMap(tenantId).values().stream()
                .filter(m -> name.equals(m.getName()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Metric>> findByNamePattern(String tenantId, String namePattern) {
        return Promise.of(
            getMetricMap(tenantId).values().stream()
                .filter(m -> m.getName() != null && m.getName().contains(namePattern))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Metric>> findByType(String tenantId, MetricType type) {
        return Promise.of(
            getMetricMap(tenantId).values().stream()
                .filter(m -> type == m.getType())
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Metric>> findByTags(String tenantId, Map<String, String> tags) {
        return Promise.of(
            getMetricMap(tenantId).values().stream()
                .filter(m -> {
                    if (m.getTags() == null || tags == null) return false;
                    return tags.entrySet().stream()
                        .allMatch(e -> e.getValue().equals(m.getTags().get(e.getKey())));
                })
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Metric> addDataPoints(String tenantId, UUID metricId, List<DataPoint> dataPoints) {
        Metric metric = getMetricMap(tenantId).get(metricId);
        if (metric != null && dataPoints != null) {
            for (DataPoint dp : dataPoints) {
                metric.addDataPoint(dp);
            }
        }
        return Promise.of(metric);
    }

    @Override
    public Promise<List<DataPoint>> queryDataPoints(
            String tenantId, UUID metricId, Instant start, Instant end) {
        Metric metric = getMetricMap(tenantId).get(metricId);
        if (metric == null || metric.getDataPoints() == null) {
            return Promise.of(Collections.emptyList());
        }
        return Promise.of(
            metric.getDataPoints().stream()
                .filter(dp -> !dp.getTimestamp().isBefore(start) && !dp.getTimestamp().isAfter(end))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Void> delete(String tenantId, UUID id) {
        getMetricMap(tenantId).remove(id);
        return Promise.complete();
    }

    @Override
    public Promise<Integer> deleteDataPointsBefore(String tenantId, Instant before) {
        int totalDeleted = 0;
        for (Metric metric : getMetricMap(tenantId).values()) {
            if (metric.getDataPoints() != null) {
                int sizeBefore = metric.getDataPoints().size();
                metric.getDataPoints().removeIf(dp -> dp.getTimestamp().isBefore(before));
                totalDeleted += (sizeBefore - metric.getDataPoints().size());
            }
        }
        return Promise.of(totalDeleted);
    }

    @Override
    public Promise<Boolean> exists(String tenantId, UUID id) {
        return Promise.of(getMetricMap(tenantId).containsKey(id));
    }
}
