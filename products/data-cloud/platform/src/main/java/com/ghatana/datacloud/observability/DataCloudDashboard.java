package com.ghatana.datacloud.observability;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dashboard data provider for Data-Cloud metrics visualization.
 *
 * <p><b>Purpose</b><br>
 * Aggregates and formats metrics data for dashboard consumption:
 * <ul>
 *   <li>Real-time plugin status overview</li>
 *   <li>Per-tenant usage breakdown</li>
 *   <li>Operation latency distributions</li>
 *   <li>Error rate tracking</li>
 *   <li>Capacity planning metrics</li>
 * </ul>
 *
 * <p><b>Dashboard Views</b><br>
 * <pre>
 * 1. System Overview   - Overall health, throughput, latency
 * 2. Plugin Dashboard  - Per-plugin status and metrics
 * 3. Tenant Dashboard  - Per-tenant usage and quotas
 * 4. Operations View   - Detailed operation breakdown
 * 5. Alerts View       - Active alerts and thresholds
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DataCloudDashboard dashboard = DataCloudDashboard.create(metrics);
 * 
 * // Get system overview
 * DashboardData overview = dashboard.getSystemOverview();
 * 
 * // Get plugin details
 * DashboardData plugins = dashboard.getPluginDashboard();
 * 
 * // Get tenant breakdown
 * DashboardData tenant = dashboard.getTenantDashboard("acme-corp");
 * }</pre>
 *
 * @see DataCloudMetrics
 * @doc.type class
 * @doc.purpose Dashboard data provider for metrics
 * @doc.layer observability
 * @doc.pattern Aggregator
 */
public class DataCloudDashboard {
    
    private final DataCloudMetrics metrics;
    private final Map<String, AlertThreshold> thresholds;
    private final List<Alert> activeAlerts;
    
    private DataCloudDashboard(DataCloudMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics is required");
        this.thresholds = new ConcurrentHashMap<>();
        this.activeAlerts = Collections.synchronizedList(new ArrayList<>());
        
        initializeDefaultThresholds();
    }
    
    /**
     * Create dashboard with metrics.
     *
     * @param metrics the metrics collector
     * @return dashboard instance
     */
    public static DataCloudDashboard create(DataCloudMetrics metrics) {
        return new DataCloudDashboard(metrics);
    }
    
    // ==================== Dashboard Views ====================
    
    /**
     * Get system overview dashboard.
     *
     * @return system overview data
     */
    public DashboardData getSystemOverview() {
        DashboardData data = new DashboardData("system_overview");
        
        // Get all metrics data
        Map<String, Object> allMetrics = metrics.getDashboardData();
        
        // System health
        data.addSection("health", createHealthSection());
        
        // Throughput summary
        data.addSection("throughput", createThroughputSection(allMetrics));
        
        // Error summary
        data.addSection("errors", createErrorSection(allMetrics));
        
        // Active alerts
        data.addSection("alerts", createAlertsSection());
        
        // Plugin summary
        data.addSection("plugins", createPluginSummarySection(allMetrics));
        
        return data;
    }
    
    /**
     * Get plugin dashboard with detailed metrics per plugin.
     *
     * @return plugin dashboard data
     */
    public DashboardData getPluginDashboard() {
        DashboardData data = new DashboardData("plugin_dashboard");
        
        Map<String, DataCloudMetrics.PluginHealthStatus> healthStatuses = metrics.getPluginHealthStatuses();
        
        // Group by plugin type
        Map<String, List<Map<String, Object>>> byType = healthStatuses.values().stream()
            .collect(Collectors.groupingBy(
                h -> h.getType().getValue(),
                Collectors.mapping(DataCloudMetrics.PluginHealthStatus::toMap, Collectors.toList())
            ));
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : byType.entrySet()) {
            data.addSection(entry.getKey() + "_plugins", Map.of(
                "type", entry.getKey(),
                "plugins", entry.getValue(),
                "total_count", entry.getValue().size(),
                "healthy_count", entry.getValue().stream()
                    .filter(p -> Boolean.TRUE.equals(p.get("healthy")))
                    .count()
            ));
        }
        
        return data;
    }
    
    /**
     * Get tenant dashboard with usage breakdown.
     *
     * @param tenantId the tenant ID
     * @return tenant dashboard data
     */
    public DashboardData getTenantDashboard(String tenantId) {
        DashboardData data = new DashboardData("tenant_dashboard");
        data.addMetadata("tenant_id", tenantId);
        
        Map<String, Object> tenantMetrics = metrics.getTenantMetrics(tenantId);
        
        // Usage summary
        data.addSection("usage", createTenantUsageSection(tenantId, tenantMetrics));
        
        // Operations breakdown
        data.addSection("operations", createTenantOperationsSection(tenantId, tenantMetrics));
        
        // Errors
        data.addSection("errors", createTenantErrorsSection(tenantId, tenantMetrics));
        
        // Quotas and limits
        data.addSection("quotas", createTenantQuotasSection(tenantId));
        
        return data;
    }
    
    /**
     * Get operations dashboard with detailed operation metrics.
     *
     * @return operations dashboard data
     */
    public DashboardData getOperationsDashboard() {
        DashboardData data = new DashboardData("operations_dashboard");
        
        // Group operations by type
        for (DataCloudMetrics.OperationType op : DataCloudMetrics.OperationType.values()) {
            data.addSection(op.getValue(), createOperationSection(op));
        }
        
        return data;
    }
    
    /**
     * Get alerts dashboard.
     *
     * @return alerts dashboard data
     */
    public DashboardData getAlertsDashboard() {
        DashboardData data = new DashboardData("alerts_dashboard");
        
        // Active alerts
        data.addSection("active_alerts", Map.of(
            "count", activeAlerts.size(),
            "alerts", new ArrayList<>(activeAlerts).stream()
                .map(Alert::toMap)
                .collect(Collectors.toList())
        ));
        
        // Thresholds
        data.addSection("thresholds", thresholds.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().toMap()
            )));
        
        return data;
    }
    
    // ==================== Alert Management ====================
    
    /**
     * Add a custom threshold.
     *
     * @param name the threshold name
     * @param threshold the threshold configuration
     */
    public void addThreshold(String name, AlertThreshold threshold) {
        thresholds.put(name, threshold);
    }
    
    /**
     * Check thresholds and raise alerts.
     */
    public void checkThresholds() {
        activeAlerts.clear();
        
        Map<String, Object> allMetrics = metrics.getDashboardData();
        
        // Check each threshold
        for (Map.Entry<String, AlertThreshold> entry : thresholds.entrySet()) {
            AlertThreshold threshold = entry.getValue();
            Object currentValue = getMetricValue(allMetrics, threshold.getMetricPath());
            
            if (currentValue instanceof Number num) {
                double value = num.doubleValue();
                if (threshold.isTriggered(value)) {
                    activeAlerts.add(new Alert(
                        entry.getKey(),
                        threshold.getSeverity(),
                        String.format("%s threshold exceeded: %.2f (threshold: %.2f)",
                            entry.getKey(), value, threshold.getThreshold()),
                        System.currentTimeMillis()
                    ));
                }
            }
        }
    }
    
    /**
     * Get active alerts.
     *
     * @return list of active alerts
     */
    public List<Alert> getActiveAlerts() {
        return Collections.unmodifiableList(new ArrayList<>(activeAlerts));
    }
    
    // ==================== Helper Methods ====================
    
    private void initializeDefaultThresholds() {
        // Error rate threshold
        thresholds.put("error_rate_high", new AlertThreshold(
            "counters.error_rate", 0.05, AlertSeverity.WARNING, ThresholdType.ABOVE));
        
        thresholds.put("error_rate_critical", new AlertThreshold(
            "counters.error_rate", 0.10, AlertSeverity.CRITICAL, ThresholdType.ABOVE));
        
        // Plugin health
        thresholds.put("plugin_unhealthy", new AlertThreshold(
            "plugins.healthy_ratio", 0.8, AlertSeverity.WARNING, ThresholdType.BELOW));
    }
    
    private Map<String, Object> createHealthSection() {
        Map<String, Object> health = new LinkedHashMap<>();
        
        Map<String, DataCloudMetrics.PluginHealthStatus> statuses = metrics.getPluginHealthStatuses();
        long healthyCount = statuses.values().stream().filter(DataCloudMetrics.PluginHealthStatus::isHealthy).count();
        long totalCount = statuses.size();
        
        health.put("status", healthyCount == totalCount ? "healthy" : "degraded");
        health.put("healthy_plugins", healthyCount);
        health.put("total_plugins", totalCount);
        health.put("health_ratio", totalCount > 0 ? (double) healthyCount / totalCount : 1.0);
        health.put("checked_at", System.currentTimeMillis());
        
        return health;
    }
    
    private Map<String, Object> createThroughputSection(Map<String, Object> allMetrics) {
        Map<String, Object> throughput = new LinkedHashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) allMetrics.get("counters");
        
        long totalOps = counters != null ? counters.values().stream().mapToLong(Long::longValue).sum() : 0;
        throughput.put("total_operations", totalOps);
        
        // Calculate ops/sec (would need time tracking in real implementation)
        throughput.put("operations_per_second", 0.0); // Placeholder
        
        return throughput;
    }
    
    private Map<String, Object> createErrorSection(Map<String, Object> allMetrics) {
        Map<String, Object> errors = new LinkedHashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) allMetrics.get("counters");
        
        long errorCount = counters != null ? 
            counters.entrySet().stream()
                .filter(e -> e.getKey().contains("error"))
                .mapToLong(Map.Entry::getValue)
                .sum() : 0;
        
        long totalOps = counters != null ? counters.values().stream().mapToLong(Long::longValue).sum() : 1;
        
        errors.put("total_errors", errorCount);
        errors.put("error_rate", (double) errorCount / Math.max(totalOps, 1));
        
        return errors;
    }
    
    private Map<String, Object> createAlertsSection() {
        Map<String, Object> alerts = new LinkedHashMap<>();
        alerts.put("active_count", activeAlerts.size());
        alerts.put("critical_count", activeAlerts.stream()
            .filter(a -> a.getSeverity() == AlertSeverity.CRITICAL)
            .count());
        alerts.put("warning_count", activeAlerts.stream()
            .filter(a -> a.getSeverity() == AlertSeverity.WARNING)
            .count());
        return alerts;
    }
    
    private Map<String, Object> createPluginSummarySection(Map<String, Object> allMetrics) {
        Map<String, Object> plugins = new LinkedHashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> pluginData = 
            (Map<String, Map<String, Object>>) allMetrics.get("plugins");
        
        if (pluginData != null) {
            plugins.put("total_count", pluginData.size());
            
            // Count by type
            Map<String, Long> byType = new HashMap<>();
            for (Map<String, Object> p : pluginData.values()) {
                String type = (String) p.get("type");
                byType.merge(type, 1L, Long::sum);
            }
            plugins.put("by_type", byType);
        }
        
        return plugins;
    }
    
    private Map<String, Object> createTenantUsageSection(String tenantId, Map<String, Object> tenantMetrics) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("tenant_id", tenantId);
        
        @SuppressWarnings("unchecked")
        Map<String, Long> gauges = (Map<String, Long>) tenantMetrics.get("gauges");
        
        if (gauges != null) {
            // Extract storage usage
            gauges.entrySet().stream()
                .filter(e -> e.getKey().contains("storage"))
                .findFirst()
                .ifPresent(e -> usage.put("storage_bytes", e.getValue()));
            
            // Extract API calls
            gauges.entrySet().stream()
                .filter(e -> e.getKey().contains("api"))
                .findFirst()
                .ifPresent(e -> usage.put("api_calls", e.getValue()));
        }
        
        return usage;
    }
    
    private Map<String, Object> createTenantOperationsSection(String tenantId, Map<String, Object> tenantMetrics) {
        Map<String, Object> operations = new LinkedHashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) tenantMetrics.get("counters");
        
        if (counters != null) {
            // Group by operation type
            Map<String, Long> byOp = new HashMap<>();
            for (Map.Entry<String, Long> entry : counters.entrySet()) {
                for (DataCloudMetrics.OperationType op : DataCloudMetrics.OperationType.values()) {
                    if (entry.getKey().contains(op.getValue())) {
                        byOp.merge(op.getValue(), entry.getValue(), Long::sum);
                        break;
                    }
                }
            }
            operations.put("by_operation", byOp);
        }
        
        return operations;
    }
    
    private Map<String, Object> createTenantErrorsSection(String tenantId, Map<String, Object> tenantMetrics) {
        Map<String, Object> errors = new LinkedHashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) tenantMetrics.get("counters");
        
        long errorCount = counters != null ?
            counters.entrySet().stream()
                .filter(e -> e.getKey().contains("error"))
                .mapToLong(Map.Entry::getValue)
                .sum() : 0;
        
        errors.put("total_errors", errorCount);
        
        return errors;
    }
    
    private Map<String, Object> createTenantQuotasSection(String tenantId) {
        Map<String, Object> quotas = new LinkedHashMap<>();
        // Placeholder - would integrate with quota management
        quotas.put("api_calls_limit", 100000);
        quotas.put("storage_limit_bytes", 10L * 1024 * 1024 * 1024); // 10GB
        quotas.put("rate_limit_rpm", 10000);
        return quotas;
    }
    
    private Map<String, Object> createOperationSection(DataCloudMetrics.OperationType op) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("operation", op.getValue());
        section.put("category", op.name().split("_")[0].toLowerCase());
        // Would add actual metrics in real implementation
        return section;
    }
    
    private Object getMetricValue(Map<String, Object> metrics, String path) {
        String[] parts = path.split("\\.");
        Object current = metrics;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    // ==================== Inner Classes ====================
    
    /**
     * Dashboard data container.
     */
    public static class DashboardData {
        private final String type;
        private final Map<String, Object> sections;
        private final Map<String, Object> metadata;
        private final long generatedAt;
        
        public DashboardData(String type) {
            this.type = type;
            this.sections = new LinkedHashMap<>();
            this.metadata = new LinkedHashMap<>();
            this.generatedAt = System.currentTimeMillis();
        }
        
        public void addSection(String name, Map<String, Object> data) {
            sections.put(name, data);
        }
        
        public void addMetadata(String key, Object value) {
            metadata.put(key, value);
        }
        
        public String getType() { return type; }
        public Map<String, Object> getSections() { return Collections.unmodifiableMap(sections); }
        public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
        public long getGeneratedAt() { return generatedAt; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            map.put("generated_at", generatedAt);
            map.put("metadata", metadata);
            map.put("sections", sections);
            return map;
        }
    }
    
    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        INFO, WARNING, CRITICAL
    }
    
    /**
     * Threshold comparison types.
     */
    public enum ThresholdType {
        ABOVE, BELOW, EQUALS
    }
    
    /**
     * Alert threshold configuration.
     */
    public static class AlertThreshold {
        private final String metricPath;
        private final double threshold;
        private final AlertSeverity severity;
        private final ThresholdType type;
        
        public AlertThreshold(String metricPath, double threshold, 
                               AlertSeverity severity, ThresholdType type) {
            this.metricPath = metricPath;
            this.threshold = threshold;
            this.severity = severity;
            this.type = type;
        }
        
        public String getMetricPath() { return metricPath; }
        public double getThreshold() { return threshold; }
        public AlertSeverity getSeverity() { return severity; }
        public ThresholdType getType() { return type; }
        
        public boolean isTriggered(double value) {
            return switch (type) {
                case ABOVE -> value > threshold;
                case BELOW -> value < threshold;
                case EQUALS -> Math.abs(value - threshold) < 0.001;
            };
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("metric_path", metricPath);
            map.put("threshold", threshold);
            map.put("severity", severity.name());
            map.put("type", type.name());
            return map;
        }
    }
    
    /**
     * Active alert.
     */
    public static class Alert {
        private final String name;
        private final AlertSeverity severity;
        private final String message;
        private final long triggeredAt;
        
        public Alert(String name, AlertSeverity severity, String message, long triggeredAt) {
            this.name = name;
            this.severity = severity;
            this.message = message;
            this.triggeredAt = triggeredAt;
        }
        
        public String getName() { return name; }
        public AlertSeverity getSeverity() { return severity; }
        public String getMessage() { return message; }
        public long getTriggeredAt() { return triggeredAt; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("severity", severity.name());
            map.put("message", message);
            map.put("triggered_at", triggeredAt);
            return map;
        }
    }
}
