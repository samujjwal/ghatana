/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.Metric;
import com.ghatana.yappc.api.domain.Metric.*;
import com.ghatana.yappc.api.repository.MetricRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing metrics.
 *
 * @doc.type class
 * @doc.purpose Business logic for metric operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class MetricService {

    private static final Logger logger = LoggerFactory.getLogger(MetricService.class);

    private final MetricRepository repository;
    private final AuditService auditService;

    @Inject
    public MetricService(MetricRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Creates a new metric.
     */
    public Promise<Metric> createMetric(String tenantId, CreateMetricInput input) {
        logger.info("Creating metric: {} for tenant: {}", input.name(), tenantId);

        Metric metric = new Metric();
        metric.setTenantId(tenantId);
        metric.setProjectId(input.projectId());
        metric.setName(input.name());
        metric.setDescription(input.description());
        metric.setType(input.type());
        metric.setUnit(input.unit());
        if (input.tags() != null) {
            metric.setTags(input.tags());
        }

        return repository.save(metric);
    }

    /**
     * Gets a metric by ID.
     */
    public Promise<Optional<Metric>> getMetric(String tenantId, UUID metricId) {
        return repository.findById(tenantId, metricId)
            .map(m -> Optional.ofNullable(m));
    }

    /**
     * Lists metrics for a project.
     */
    public Promise<List<Metric>> listProjectMetrics(String tenantId, String projectId) {
        return repository.findByProject(tenantId, projectId);
    }

    /**
     * Search metrics by name pattern.
     */
    public Promise<List<Metric>> searchMetrics(String tenantId, String namePattern) {
        return repository.findByNamePattern(tenantId, namePattern);
    }

    /**
     * Lists metrics by type.
     */
    public Promise<List<Metric>> listMetricsByType(String tenantId, MetricType type) {
        return repository.findByType(tenantId, type);
    }

    /**
     * Records data points for a metric.
     */
    public Promise<Metric> recordDataPoints(String tenantId, UUID metricId, List<DataPointInput> inputs) {
        List<DataPoint> dataPoints = inputs.stream()
            .map(input -> {
                DataPoint dp = new DataPoint();
                dp.setTimestamp(input.timestamp() != null ? input.timestamp() : Instant.now());
                dp.setValue(input.value());
                if (input.tags() != null) {
                    dp.setTags(input.tags());
                }
                return dp;
            })
            .toList();

        return repository.addDataPoints(tenantId, metricId, dataPoints);
    }

    /**
     * Queries data points within a time range.
     */
    public Promise<List<DataPoint>> queryDataPoints(
            String tenantId, 
            UUID metricId, 
            Instant start, 
            Instant end) {
        return repository.queryDataPoints(tenantId, metricId, start, end);
    }

    /**
     * Gets metric summary/aggregations.
     */
    public Promise<MetricSummary> getMetricSummary(String tenantId, UUID metricId) {
        return repository.findById(tenantId, metricId)
            .map(m -> m != null ? m.getSummary() : null);
    }

    /**
     * Gets aggregated statistics for a project.
     */
    public Promise<ProjectMetricsStats> getProjectStatistics(String tenantId, String projectId) {
        return repository.findByProject(tenantId, projectId)
            .map(metrics -> {
                ProjectMetricsStats stats = new ProjectMetricsStats();
                stats.setTotalMetrics(metrics.size());
                
                long counters = metrics.stream().filter(m -> m.getType() == MetricType.COUNTER).count();
                long gauges = metrics.stream().filter(m -> m.getType() == MetricType.GAUGE).count();
                long histograms = metrics.stream().filter(m -> m.getType() == MetricType.HISTOGRAM).count();
                
                stats.setCounterCount(counters);
                stats.setGaugeCount(gauges);
                stats.setHistogramCount(histograms);
                
                return stats;
            });
    }

    /**
     * Deletes a metric.
     */
    public Promise<Void> deleteMetric(String tenantId, UUID metricId) {
        logger.info("Deleting metric: {} for tenant: {}", metricId, tenantId);
        return repository.delete(tenantId, metricId);
    }

    /**
     * Cleans up old data points.
     */
    public Promise<Integer> cleanupOldDataPoints(String tenantId, Instant before) {
        logger.info("Cleaning up data points before: {} for tenant: {}", before, tenantId);
        return repository.deleteDataPointsBefore(tenantId, before);
    }

    // ========== Input Records ==========

    public record CreateMetricInput(
        String projectId,
        String name,
        String description,
        MetricType type,
        String unit,
        Map<String, String> tags
    ) {}

    public record DataPointInput(
        Instant timestamp,
        double value,
        Map<String, String> tags
    ) {}

    // ========== Stats Classes ==========

    public static class ProjectMetricsStats {
        private int totalMetrics;
        private long counterCount;
        private long gaugeCount;
        private long histogramCount;

        public int getTotalMetrics() { return totalMetrics; }
        public void setTotalMetrics(int totalMetrics) { this.totalMetrics = totalMetrics; }

        public long getCounterCount() { return counterCount; }
        public void setCounterCount(long counterCount) { this.counterCount = counterCount; }

        public long getGaugeCount() { return gaugeCount; }
        public void setGaugeCount(long gaugeCount) { this.gaugeCount = gaugeCount; }

        public long getHistogramCount() { return histogramCount; }
        public void setHistogramCount(long histogramCount) { this.histogramCount = histogramCount; }
    }
}
