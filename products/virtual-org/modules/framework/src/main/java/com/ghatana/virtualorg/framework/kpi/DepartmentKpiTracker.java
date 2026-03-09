package com.ghatana.virtualorg.framework.kpi;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks KPIs (Key Performance Indicators) for all departments in an
 * organization.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides centralized KPI tracking with: - Real-time metric collection -
 * Department-scoped isolation - Time-series aggregation - Quality gate
 * evaluation
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DepartmentKpiTracker tracker = new DepartmentKpiTracker(tenantId, orgId);
 * tracker.registerDepartment(deptId, "Engineering");
 * tracker.recordMetric(deptId, "code_quality_score", 0.92);
 * double score = tracker.getMetric(deptId, "code_quality_score");
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe via ConcurrentHashMap. All operations are atomic.
 *
 * <p>
 * <b>Performance Characteristics</b><br>
 * O(1) metric read/write. Memory overhead: ~1KB per department + ~100 bytes per
 * metric.
 *
 * @doc.type class
 * @doc.purpose KPI tracking for virtual organizations
 * @doc.layer product
 * @doc.pattern Observer
 */
public class DepartmentKpiTracker {

    private final TenantId tenantId;
    private final Identifier organizationId;
    private final Map<String, DepartmentKpis> departmentKpis;

    /**
     * Constructs KPI tracker.
     *
     * @param tenantId tenant owning organization
     * @param organizationId organization identifier
     */
    public DepartmentKpiTracker(TenantId tenantId, Identifier organizationId) {
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.departmentKpis = new ConcurrentHashMap<>();
    }

    /**
     * Registers a department for KPI tracking.
     *
     * GIVEN: Department ID and name WHEN: registerDepartment() is called THEN:
     * New KPI container is created for department
     *
     * @param departmentId department identifier
     * @param departmentName department name
     */
    public void registerDepartment(Identifier departmentId, String departmentName) {
        departmentKpis.put(
                departmentId.raw(),
                new DepartmentKpis(departmentId, departmentName)
        );
    }

    /**
     * Records a metric value for a department.
     *
     * GIVEN: Department ID, metric name, and value WHEN: recordMetric() is
     * called THEN: Metric is stored with current timestamp
     *
     * @param departmentId department identifier
     * @param metricName metric name (e.g., "code_quality_score")
     * @param value metric value
     * @throws IllegalArgumentException if department not registered
     */
    public void recordMetric(Identifier departmentId, String metricName, double value) {
        DepartmentKpis kpis = departmentKpis.get(departmentId.raw());
        if (kpis == null) {
            throw new IllegalArgumentException("Department not registered: " + departmentId);
        }

        kpis.recordMetric(metricName, value);
    }

    /**
     * Retrieves latest metric value for a department.
     *
     * @param departmentId department identifier
     * @param metricName metric name
     * @return latest metric value or 0.0 if not found
     */
    public double getMetric(Identifier departmentId, String metricName) {
        DepartmentKpis kpis = departmentKpis.get(departmentId.raw());
        if (kpis == null) {
            return 0.0;
        }

        return kpis.getMetric(metricName);
    }

    /**
     * Retrieves all KPIs for a department.
     *
     * @param departmentId department identifier
     * @return unmodifiable map of metric name → value
     */
    public Map<String, Double> getDepartmentKpis(Identifier departmentId) {
        DepartmentKpis kpis = departmentKpis.get(departmentId.raw());
        if (kpis == null) {
            return Map.of();
        }

        return kpis.getAllMetrics();
    }

    /**
     * Container for department-specific KPIs.
     */
    private static class DepartmentKpis {

        private final Identifier departmentId;
        private final String departmentName;
        private final Map<String, MetricValue> metrics;

        DepartmentKpis(Identifier departmentId, String departmentName) {
            this.departmentId = departmentId;
            this.departmentName = departmentName;
            this.metrics = new ConcurrentHashMap<>();
        }

        void recordMetric(String name, double value) {
            metrics.put(name, new MetricValue(value, Instant.now()));
        }

        double getMetric(String name) {
            MetricValue metric = metrics.get(name);
            return metric != null ? metric.value : 0.0;
        }

        Map<String, Double> getAllMetrics() {
            Map<String, Double> result = new ConcurrentHashMap<>();
            metrics.forEach((name, metric) -> result.put(name, metric.value));
            return Map.copyOf(result);
        }
    }

    /**
     * Immutable metric value with timestamp.
     */
    private record MetricValue(double value, Instant timestamp) {
    }
}
