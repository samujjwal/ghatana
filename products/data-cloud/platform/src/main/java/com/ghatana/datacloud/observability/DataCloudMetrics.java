package com.ghatana.datacloud.observability;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Comprehensive metrics collector for Data-Cloud plugins and operations.
 *
 * <p><b>Purpose</b><br>
 * Provides detailed metrics for all Data-Cloud components:
 * <ul>
 *   <li>Plugin-level metrics (storage, streaming, analytics)</li>
 *   <li>Operation metrics (latency, throughput, errors)</li>
 *   <li>Tenant-level metrics (usage, quotas)</li>
 *   <li>Health metrics (availability, saturation)</li>
 * </ul>
 *
 * <p><b>Six Pillars Compliance</b><br>
 * <ul>
 *   <li><b>Observability</b>: Complete visibility into system behavior</li>
 *   <li><b>Scalability</b>: Low-overhead metric collection</li>
 *   <li><b>Debuggability</b>: Detailed breakdown by component</li>
 *   <li><b>Cost</b>: Track resource consumption per tenant</li>
 * </ul>
 *
 * <p><b>Metric Naming Convention</b><br>
 * <pre>
 * datacloud.{component}.{operation}.{metric_type}
 * 
 * Examples:
 *   datacloud.storage.entity.count
 *   datacloud.storage.entity.create.latency
 *   datacloud.streaming.event.append.count
 *   datacloud.search.query.latency
 *   datacloud.plugin.status (gauge)
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DataCloudMetrics metrics = DataCloudMetrics.create(meterRegistry);
 * 
 * // Record operation
 * Timer.Sample sample = metrics.startTimer();
 * try {
 *     // perform operation
 *     metrics.recordSuccess(sample, OperationType.ENTITY_CREATE, tenantId, pluginName);
 * } catch (Exception e) {
 *     metrics.recordError(sample, OperationType.ENTITY_CREATE, tenantId, pluginName, e);
 *     throw e;
 * }
 * }</pre>
 *
 * @see MeterRegistry
 * @doc.type class
 * @doc.purpose Metrics collection for Data-Cloud
 * @doc.layer observability
 * @doc.pattern Metrics, Facade
 */
public class DataCloudMetrics {
    private static final Logger logger = LoggerFactory.getLogger(DataCloudMetrics.class);
    
    // Metric prefixes
    private static final String PREFIX = "datacloud";
    private static final String STORAGE_PREFIX = PREFIX + ".storage";
    private static final String STREAMING_PREFIX = PREFIX + ".streaming";
    private static final String SEARCH_PREFIX = PREFIX + ".search";
    private static final String AI_PREFIX = PREFIX + ".ai";
    private static final String PLUGIN_PREFIX = PREFIX + ".plugin";
    private static final String TENANT_PREFIX = PREFIX + ".tenant";
    private static final String RATE_LIMIT_PREFIX = PREFIX + ".ratelimit";
    private static final String SECURITY_PREFIX = PREFIX + ".security";
    
    // Tags
    private static final String TAG_TENANT = "tenant";
    private static final String TAG_PLUGIN = "plugin";
    private static final String TAG_OPERATION = "operation";
    private static final String TAG_COLLECTION = "collection";
    private static final String TAG_STREAM = "stream";
    private static final String TAG_STATUS = "status";
    private static final String TAG_ERROR_TYPE = "error_type";
    
    /**
     * Operation types for metrics.
     */
    public enum OperationType {
        // Entity operations
        ENTITY_CREATE("entity.create"),
        ENTITY_READ("entity.read"),
        ENTITY_UPDATE("entity.update"),
        ENTITY_DELETE("entity.delete"),
        ENTITY_QUERY("entity.query"),
        ENTITY_BULK_CREATE("entity.bulk_create"),
        ENTITY_BULK_DELETE("entity.bulk_delete"),
        
        // Event operations
        EVENT_APPEND("event.append"),
        EVENT_READ("event.read"),
        EVENT_BATCH_APPEND("event.batch_append"),
        EVENT_SUBSCRIBE("event.subscribe"),
        
        // Search operations
        SEARCH_EXECUTE("search.execute"),
        SEARCH_FACETS("search.facets"),
        SEARCH_AGGREGATE("search.aggregate"),
        
        // AI operations
        AI_PROCESS("ai.process"),
        AI_FEATURES("ai.features"),
        AI_QUALITY("ai.quality"),
        
        // Admin operations
        HEALTH_CHECK("health.check"),
        METRICS_RETRIEVE("metrics.retrieve"),
        COLLECTION_ADMIN("collection.admin");
        
        private final String value;
        
        OperationType(String value) {
            this.value = value;
        }
        
        public String getValue() { return value; }
    }
    
    /**
     * Plugin types for metrics.
     */
    public enum PluginType {
        STORAGE("storage"),
        STREAMING("streaming"),
        ANALYTICS("analytics"),
        ROUTING("routing"),
        SEARCH("search"),
        AI("ai");
        
        private final String value;
        
        PluginType(String value) {
            this.value = value;
        }
        
        public String getValue() { return value; }
    }
    
    private final MeterRegistry registry;
    
    // Cached meters
    private final Map<String, Counter> counters;
    private final Map<String, Timer> timers;
    private final Map<String, AtomicLong> gaugeValues;
    
    // Plugin health trackers
    private final Map<String, PluginHealthStatus> pluginHealth;
    
    private DataCloudMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.counters = new ConcurrentHashMap<>();
        this.timers = new ConcurrentHashMap<>();
        this.gaugeValues = new ConcurrentHashMap<>();
        this.pluginHealth = new ConcurrentHashMap<>();
        
        // Register global gauges
        registerGlobalGauges();
    }
    
    /**
     * Create metrics collector with registry.
     *
     * @param registry the meter registry
     * @return metrics instance
     */
    public static DataCloudMetrics create(MeterRegistry registry) {
        return new DataCloudMetrics(registry);
    }
    
    // ==================== Operation Metrics ====================
    
    /**
     * Start a timer sample.
     *
     * @return timer sample
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }
    
    /**
     * Record successful operation.
     *
     * @param sample the timer sample
     * @param operation the operation type
     * @param tenantId the tenant ID
     * @param pluginName the plugin name
     */
    public void recordSuccess(Timer.Sample sample, OperationType operation, 
                               String tenantId, String pluginName) {
        String timerName = getTimerName(operation, pluginName);
        Timer timer = getOrCreateTimer(timerName, tenantId, pluginName, "success");
        sample.stop(timer);
        
        incrementCounter(operation, tenantId, pluginName, "success");
    }
    
    /**
     * Record operation error.
     *
     * @param sample the timer sample
     * @param operation the operation type
     * @param tenantId the tenant ID
     * @param pluginName the plugin name
     * @param error the error
     */
    public void recordError(Timer.Sample sample, OperationType operation,
                             String tenantId, String pluginName, Throwable error) {
        String timerName = getTimerName(operation, pluginName);
        Timer timer = getOrCreateTimer(timerName, tenantId, pluginName, "error");
        sample.stop(timer);
        
        incrementCounter(operation, tenantId, pluginName, "error");
        
        // Record error type
        String errorType = error.getClass().getSimpleName();
        incrementErrorCounter(tenantId, pluginName, errorType);
    }
    
    /**
     * Record operation with duration.
     *
     * @param operation the operation type
     * @param tenantId the tenant ID
     * @param pluginName the plugin name
     * @param durationMs duration in milliseconds
     * @param success whether operation succeeded
     */
    public void recordOperation(OperationType operation, String tenantId, String pluginName,
                                 long durationMs, boolean success) {
        String status = success ? "success" : "error";
        String timerName = getTimerName(operation, pluginName);
        Timer timer = getOrCreateTimer(timerName, tenantId, pluginName, status);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        
        incrementCounter(operation, tenantId, pluginName, status);
    }
    
    /**
     * Record timed operation using supplier.
     *
     * @param operation the operation type
     * @param tenantId the tenant ID
     * @param pluginName the plugin name
     * @param action the action to time
     * @return the result of the action
     */
    public <T> T recordTimed(OperationType operation, String tenantId, String pluginName,
                              Supplier<T> action) {
        Timer.Sample sample = startTimer();
        try {
            T result = action.get();
            recordSuccess(sample, operation, tenantId, pluginName);
            return result;
        } catch (Exception e) {
            recordError(sample, operation, tenantId, pluginName, e);
            throw e;
        }
    }
    
    /**
     * Record timed operation using runnable.
     *
     * @param operation the operation type
     * @param tenantId the tenant ID
     * @param pluginName the plugin name
     * @param action the action to time
     */
    public void recordTimed(OperationType operation, String tenantId, String pluginName,
                             Runnable action) {
        Timer.Sample sample = startTimer();
        try {
            action.run();
            recordSuccess(sample, operation, tenantId, pluginName);
        } catch (Exception e) {
            recordError(sample, operation, tenantId, pluginName, e);
            throw e;
        }
    }
    
    // ==================== Storage Metrics ====================
    
    /**
     * Record entity count for a collection.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param count the entity count
     */
    public void recordEntityCount(String tenantId, String collectionName, long count) {
        String gaugeName = STORAGE_PREFIX + ".entity.count";
        AtomicLong gauge = getOrCreateGauge(gaugeName, tenantId, collectionName);
        gauge.set(count);
    }
    
    /**
     * Record entity size in bytes.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param sizeBytes the size in bytes
     */
    public void recordEntitySize(String tenantId, String collectionName, long sizeBytes) {
        String gaugeName = STORAGE_PREFIX + ".entity.size_bytes";
        AtomicLong gauge = getOrCreateGauge(gaugeName, tenantId, collectionName);
        gauge.set(sizeBytes);
    }
    
    // ==================== Streaming Metrics ====================
    
    /**
     * Record event offset for a stream.
     *
     * @param tenantId the tenant ID
     * @param streamName the stream name
     * @param offset the current offset
     */
    public void recordEventOffset(String tenantId, String streamName, long offset) {
        String gaugeName = STREAMING_PREFIX + ".event.offset";
        AtomicLong gauge = getOrCreateGauge(gaugeName + ":" + tenantId + ":" + streamName, 
            tenantId, streamName);
        gauge.set(offset);
    }
    
    /**
     * Record event throughput.
     *
     * @param tenantId the tenant ID
     * @param streamName the stream name
     * @param eventsPerSecond events per second
     */
    public void recordEventThroughput(String tenantId, String streamName, double eventsPerSecond) {
        registry.gauge(STREAMING_PREFIX + ".event.throughput", 
            Tags.of(TAG_TENANT, tenantId, TAG_STREAM, streamName),
            eventsPerSecond);
    }
    
    // ==================== Search Metrics ====================
    
    /**
     * Record search hit count.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param hitCount the number of hits
     */
    public void recordSearchHits(String tenantId, String collectionName, long hitCount) {
        Counter counter = getOrCreateCounter(SEARCH_PREFIX + ".hits.total",
            tenantId, collectionName, "search");
        counter.increment(hitCount);
    }
    
    /**
     * Record search latency percentile.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param latencyMs latency in milliseconds
     */
    public void recordSearchLatency(String tenantId, String collectionName, long latencyMs) {
        Timer timer = Timer.builder(SEARCH_PREFIX + ".query.latency")
            .tags(TAG_TENANT, tenantId, TAG_COLLECTION, collectionName)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        timer.record(latencyMs, TimeUnit.MILLISECONDS);
    }
    
    // ==================== Plugin Metrics ====================
    
    /**
     * Record plugin health status.
     *
     * @param pluginType the plugin type
     * @param pluginName the plugin name
     * @param healthy whether plugin is healthy
     */
    public void recordPluginHealth(PluginType pluginType, String pluginName, boolean healthy) {
        String key = pluginType.getValue() + ":" + pluginName;
        PluginHealthStatus status = pluginHealth.computeIfAbsent(key, 
            k -> new PluginHealthStatus(pluginType, pluginName));
        status.setHealthy(healthy);
        
        // Update gauge
        registry.gauge(PLUGIN_PREFIX + ".health",
            Tags.of("type", pluginType.getValue(), "name", pluginName),
            status, s -> s.isHealthy() ? 1.0 : 0.0);
    }
    
    /**
     * Record plugin operation count.
     *
     * @param pluginType the plugin type
     * @param pluginName the plugin name
     * @param operationCount total operation count
     */
    public void recordPluginOperations(PluginType pluginType, String pluginName, long operationCount) {
        String key = pluginType.getValue() + ":" + pluginName;
        PluginHealthStatus status = pluginHealth.computeIfAbsent(key,
            k -> new PluginHealthStatus(pluginType, pluginName));
        status.setTotalOperations(operationCount);
    }
    
    /**
     * Record plugin error count.
     *
     * @param pluginType the plugin type
     * @param pluginName the plugin name
     * @param errorCount total error count
     */
    public void recordPluginErrors(PluginType pluginType, String pluginName, long errorCount) {
        String key = pluginType.getValue() + ":" + pluginName;
        PluginHealthStatus status = pluginHealth.computeIfAbsent(key,
            k -> new PluginHealthStatus(pluginType, pluginName));
        status.setTotalErrors(errorCount);
    }
    
    /**
     * Get all plugin health statuses.
     *
     * @return map of plugin health statuses
     */
    public Map<String, PluginHealthStatus> getPluginHealthStatuses() {
        return Collections.unmodifiableMap(pluginHealth);
    }
    
    // ==================== Tenant Metrics ====================
    
    /**
     * Record tenant request count.
     *
     * @param tenantId the tenant ID
     */
    public void recordTenantRequest(String tenantId) {
        Counter counter = getOrCreateCounter(TENANT_PREFIX + ".requests.total", 
            tenantId, "all", "request");
        counter.increment();
    }
    
    /**
     * Record tenant API usage.
     *
     * @param tenantId the tenant ID
     * @param apiCalls number of API calls
     */
    public void recordTenantApiUsage(String tenantId, long apiCalls) {
        String gaugeName = TENANT_PREFIX + ".api.calls";
        AtomicLong gauge = getOrCreateGauge(gaugeName + ":" + tenantId, tenantId, "api");
        gauge.set(apiCalls);
    }
    
    /**
     * Record tenant storage usage.
     *
     * @param tenantId the tenant ID
     * @param storageBytes storage usage in bytes
     */
    public void recordTenantStorageUsage(String tenantId, long storageBytes) {
        String gaugeName = TENANT_PREFIX + ".storage.bytes";
        AtomicLong gauge = getOrCreateGauge(gaugeName + ":" + tenantId, tenantId, "storage");
        gauge.set(storageBytes);
    }
    
    // ==================== Rate Limit Metrics ====================
    
    /**
     * Record rate limit check.
     *
     * @param tenantId the tenant ID
     * @param allowed whether request was allowed
     */
    public void recordRateLimitCheck(String tenantId, boolean allowed) {
        String status = allowed ? "allowed" : "denied";
        Counter counter = getOrCreateCounter(RATE_LIMIT_PREFIX + ".checks.total",
            tenantId, "ratelimit", status);
        counter.increment();
    }
    
    /**
     * Record rate limit rejection.
     *
     * @param tenantId the tenant ID
     * @param reason the rejection reason
     */
    public void recordRateLimitRejection(String tenantId, String reason) {
        Counter counter = registry.counter(RATE_LIMIT_PREFIX + ".rejections.total",
            Tags.of(TAG_TENANT, tenantId, "reason", reason));
        counter.increment();
    }
    
    // ==================== Security Metrics ====================
    
    /**
     * Record RBAC check.
     *
     * @param tenantId the tenant ID
     * @param allowed whether access was allowed
     */
    public void recordRbacCheck(String tenantId, boolean allowed) {
        String status = allowed ? "allowed" : "denied";
        Counter counter = getOrCreateCounter(SECURITY_PREFIX + ".rbac.checks.total",
            tenantId, "rbac", status);
        counter.increment();
    }
    
    /**
     * Record audit event.
     *
     * @param tenantId the tenant ID
     * @param eventType the audit event type
     */
    public void recordAuditEvent(String tenantId, String eventType) {
        Counter counter = registry.counter(SECURITY_PREFIX + ".audit.events.total",
            Tags.of(TAG_TENANT, tenantId, "event_type", eventType));
        counter.increment();
    }
    
    // ==================== Dashboard Data ====================
    
    /**
     * Get metrics dashboard data.
     *
     * @return dashboard data map
     */
    public Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();
        
        // Plugin health
        Map<String, Object> plugins = new LinkedHashMap<>();
        for (Map.Entry<String, PluginHealthStatus> entry : pluginHealth.entrySet()) {
            plugins.put(entry.getKey(), entry.getValue().toMap());
        }
        data.put("plugins", plugins);
        
        // Counter totals
        Map<String, Long> counterTotals = new LinkedHashMap<>();
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            counterTotals.put(entry.getKey(), (long) entry.getValue().count());
        }
        data.put("counters", counterTotals);
        
        // Gauge values
        Map<String, Long> gaugeVals = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : gaugeValues.entrySet()) {
            gaugeVals.put(entry.getKey(), entry.getValue().get());
        }
        data.put("gauges", gaugeVals);
        
        return data;
    }
    
    /**
     * Get metrics for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return tenant metrics map
     */
    public Map<String, Object> getTenantMetrics(String tenantId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        
        // Filter counters by tenant
        Map<String, Long> tenantCounters = new LinkedHashMap<>();
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            if (entry.getKey().contains(tenantId)) {
                tenantCounters.put(entry.getKey(), (long) entry.getValue().count());
            }
        }
        metrics.put("counters", tenantCounters);
        
        // Filter gauges by tenant
        Map<String, Long> tenantGauges = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : gaugeValues.entrySet()) {
            if (entry.getKey().contains(tenantId)) {
                tenantGauges.put(entry.getKey(), entry.getValue().get());
            }
        }
        metrics.put("gauges", tenantGauges);
        
        return metrics;
    }
    
    /**
     * Get metrics for a specific plugin.
     *
     * @param pluginType the plugin type
     * @param pluginName the plugin name
     * @return plugin metrics map
     */
    public Map<String, Object> getPluginMetrics(PluginType pluginType, String pluginName) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        
        String key = pluginType.getValue() + ":" + pluginName;
        PluginHealthStatus health = pluginHealth.get(key);
        if (health != null) {
            metrics.put("health", health.toMap());
        }
        
        // Filter counters by plugin
        String pluginFilter = pluginType.getValue();
        Map<String, Long> pluginCounters = new LinkedHashMap<>();
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            if (entry.getKey().contains(pluginFilter)) {
                pluginCounters.put(entry.getKey(), (long) entry.getValue().count());
            }
        }
        metrics.put("counters", pluginCounters);
        
        return metrics;
    }
    
    // ==================== Helper Methods ====================
    
    private void registerGlobalGauges() {
        // Register global up gauge
        registry.gauge(PREFIX + ".up", 1);
    }
    
    private String getTimerName(OperationType operation, String pluginName) {
        return PREFIX + "." + pluginName + "." + operation.getValue() + ".latency";
    }
    
    private Timer getOrCreateTimer(String name, String tenantId, String plugin, String status) {
        String key = name + ":" + tenantId + ":" + plugin + ":" + status;
        return timers.computeIfAbsent(key, k ->
            Timer.builder(name)
                .tags(TAG_TENANT, tenantId, TAG_PLUGIN, plugin, TAG_STATUS, status)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
        );
    }
    
    private Counter getOrCreateCounter(String name, String tenantId, String plugin, String status) {
        String key = name + ":" + tenantId + ":" + plugin + ":" + status;
        return counters.computeIfAbsent(key, k ->
            Counter.builder(name)
                .tags(TAG_TENANT, tenantId, TAG_PLUGIN, plugin, TAG_STATUS, status)
                .register(registry)
        );
    }
    
    private void incrementCounter(OperationType operation, String tenantId, String plugin, String status) {
        String name = PREFIX + "." + plugin + "." + operation.getValue() + ".count";
        Counter counter = getOrCreateCounter(name, tenantId, plugin, status);
        counter.increment();
    }
    
    private void incrementErrorCounter(String tenantId, String plugin, String errorType) {
        Counter counter = registry.counter(PREFIX + ".errors.total",
            Tags.of(TAG_TENANT, tenantId, TAG_PLUGIN, plugin, TAG_ERROR_TYPE, errorType));
        counter.increment();
    }
    
    private AtomicLong getOrCreateGauge(String name, String tenantId, String resource) {
        String key = name + ":" + tenantId + ":" + resource;
        return gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong(0);
            registry.gauge(name, Tags.of(TAG_TENANT, tenantId, "resource", resource), value);
            return value;
        });
    }
    
    // ==================== Plugin Health Status ====================
    
    /**
     * Health status for a plugin.
     */
    public static class PluginHealthStatus {
        private final PluginType type;
        private final String name;
        private volatile boolean healthy = true;
        private volatile long totalOperations = 0;
        private volatile long totalErrors = 0;
        private volatile long lastCheckTime = 0;
        
        public PluginHealthStatus(PluginType type, String name) {
            this.type = type;
            this.name = name;
        }
        
        public PluginType getType() { return type; }
        public String getName() { return name; }
        public boolean isHealthy() { return healthy; }
        public long getTotalOperations() { return totalOperations; }
        public long getTotalErrors() { return totalErrors; }
        public long getLastCheckTime() { return lastCheckTime; }
        
        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
            this.lastCheckTime = System.currentTimeMillis();
        }
        
        public void setTotalOperations(long totalOperations) {
            this.totalOperations = totalOperations;
        }
        
        public void setTotalErrors(long totalErrors) {
            this.totalErrors = totalErrors;
        }
        
        public double getErrorRate() {
            return totalOperations > 0 ? (double) totalErrors / totalOperations : 0.0;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type.getValue());
            map.put("name", name);
            map.put("healthy", healthy);
            map.put("total_operations", totalOperations);
            map.put("total_errors", totalErrors);
            map.put("error_rate", getErrorRate());
            map.put("last_check_time", lastCheckTime);
            return map;
        }
    }
}
