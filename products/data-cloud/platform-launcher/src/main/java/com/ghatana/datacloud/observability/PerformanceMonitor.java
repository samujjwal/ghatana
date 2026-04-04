package com.ghatana.datacloud.observability;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.*;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive performance monitoring and alerting for Data-Cloud.
 * 
 * <p>This class provides real-time performance monitoring with alerting capabilities:
 * <ul>
 *   <li>API response time monitoring with percentile tracking</li>
 *   <li>Database query performance monitoring</li>
 *   <li>Event streaming throughput and latency tracking</li>
 *   <li>Business metrics monitoring</li>
 *   <li>SLI/SLO tracking and alerting</li>
 * </ul>
 * 
 * <h2>Performance Thresholds</h2>
 * <ul>
 *   <li>API Response Time: < 200ms (95th percentile)</li>
 *   <li>Database Query Time: < 50ms (95th percentile)</li>
 *   <li>Event Throughput: > 10,000 events/sec</li>
 *   <li>Error Rate: < 1%</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Performance monitoring and alerting with SLI/SLO tracking
 * @doc.layer observability
 * @doc.pattern PerformanceMonitoring
 */
public class PerformanceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    // Performance thresholds
    private static final double API_RESPONSE_TIME_THRESHOLD_MS = 200.0;
    private static final double DB_QUERY_TIME_THRESHOLD_MS = 50.0;
    private static final double MIN_EVENT_THROUGHPUT = 10000.0;
    private static final double MAX_ERROR_RATE = 0.01;
    private static final double AVAILABILITY_THRESHOLD = 0.999;
    
    private final PrometheusMeterRegistry meterRegistry;
    
    // API Metrics
    private final Timer apiResponseTime;
    private final Counter apiRequests;
    private final Counter apiErrors;
    private final AtomicInteger activeConnections;
    
    // Database Metrics
    private final Timer dbQueryTime;
    private final Counter dbQueries;
    private final Counter dbErrors;
    private final AtomicInteger dbConnectionPoolActive;
    private final AtomicInteger dbConnectionPoolIdle;
    
    // Event Streaming Metrics
    private final Counter eventsPublished;
    private final Counter eventsConsumed;
    private final Timer eventPublishLatency;
    private final Timer eventConsumeLatency;
    private final AtomicInteger consumerLag;
    
    // Business Metrics
    private final Counter userActions;
    private final Counter featureUsage;
    private final AtomicInteger activeUsers;
    private final AtomicInteger tenantCount;
    
    // SLI/SLO Tracking
    private final AtomicInteger sloViolations = new AtomicInteger(0);
    private final AtomicInteger apiAvailabilityViolations = new AtomicInteger(0);
    
    public PerformanceMonitor() {
        this.meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        
        // Initialize JVM and system metrics
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmGcMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);
        new UptimeMetrics().bindTo(meterRegistry);
        
        // Initialize API metrics
        this.apiResponseTime = Timer.builder("datacloud.api.response.time")
            .description("API response time in milliseconds")
            .register(meterRegistry);
        
        this.apiRequests = Counter.builder("datacloud.api.requests.total")
            .description("Total API requests")
            .register(meterRegistry);
        
        this.apiErrors = Counter.builder("datacloud.api.errors.total")
            .description("Total API errors")
            .register(meterRegistry);
        
        this.activeConnections = new AtomicInteger(0);
        Gauge.builder("datacloud.api.connections.active", activeConnections, AtomicInteger::get)
            .description("Active API connections")
            .register(meterRegistry);
        
        // Initialize database metrics
        this.dbQueryTime = Timer.builder("datacloud.db.query.time")
            .description("Database query time in milliseconds")
            .register(meterRegistry);
        
        this.dbQueries = Counter.builder("datacloud.db.queries.total")
            .description("Total database queries")
            .register(meterRegistry);
        
        this.dbErrors = Counter.builder("datacloud.db.errors.total")
            .description("Total database errors")
            .register(meterRegistry);
        
        this.dbConnectionPoolActive = new AtomicInteger(0);
        Gauge.builder("datacloud.db.connections.active", dbConnectionPoolActive, AtomicInteger::get)
            .description("Active database connections")
            .register(meterRegistry);
        
        this.dbConnectionPoolIdle = new AtomicInteger(0);
        Gauge.builder("datacloud.db.connections.idle", dbConnectionPoolIdle, AtomicInteger::get)
            .description("Idle database connections")
            .register(meterRegistry);
        
        // Initialize event streaming metrics
        this.eventsPublished = Counter.builder("datacloud.events.published.total")
            .description("Total events published")
            .register(meterRegistry);
        
        this.eventsConsumed = Counter.builder("datacloud.events.consumed.total")
            .description("Total events consumed")
            .register(meterRegistry);
        
        this.eventPublishLatency = Timer.builder("datacloud.events.publish.latency")
            .description("Event publish latency in milliseconds")
            .register(meterRegistry);
        
        this.eventConsumeLatency = Timer.builder("datacloud.events.consume.latency")
            .description("Event consume latency in milliseconds")
            .register(meterRegistry);
        
        this.consumerLag = new AtomicInteger(0);
        Gauge.builder("datacloud.events.consumer.lag", consumerLag, AtomicInteger::get)
            .description("Consumer lag in number of events")
            .register(meterRegistry);
        
        // Initialize business metrics
        this.userActions = Counter.builder("datacloud.user.actions.total")
            .description("Total user actions")
            .register(meterRegistry);
        
        this.featureUsage = Counter.builder("datacloud.feature.usage.total")
            .description("Total feature usage")
            .register(meterRegistry);
        
        this.activeUsers = new AtomicInteger(0);
        Gauge.builder("datacloud.users.active", activeUsers, AtomicInteger::get)
            .description("Number of active users")
            .register(meterRegistry);
        
        this.tenantCount = new AtomicInteger(0);
        Gauge.builder("datacloud.tenants.total", tenantCount, AtomicInteger::get)
            .description("Total number of tenants")
            .register(meterRegistry);
    }
    
    // ====================================================================================
    // API Performance Monitoring
    // ====================================================================================
    
    /**
     * Record API request metrics.
     * 
     * @param duration Request duration
     * @param method HTTP method
     * @param path Request path
     * @param statusCode HTTP status code
     */
    public void recordApiRequest(Duration duration, String method, String path, int statusCode) {
        long durationMs = duration.toMillis();
        
        apiResponseTime.record(duration);
        apiRequests.increment();
        
        // Check for SLO violations
        if (durationMs > API_RESPONSE_TIME_THRESHOLD_MS) {
            sloViolations.incrementAndGet();
            logger.warn("API SLO violation: {} {} took {}ms (threshold: {}ms)",
                method, path, durationMs, API_RESPONSE_TIME_THRESHOLD_MS);
            
            alertOnSLOViolation("API_RESPONSE_TIME", method + " " + path, durationMs, (long) API_RESPONSE_TIME_THRESHOLD_MS);
        }
        
        // Track errors
        if (statusCode >= 400) {
            apiErrors.increment();
            
            if (statusCode >= 500) {
                alertOnError("API_ERROR", method + " " + path, statusCode);
            }
        }
    }
    
    /**
     * Record active connection count.
     * 
     * @param count Number of active connections
     */
    public void setActiveConnections(int count) {
        activeConnections.set(count);
    }
    
    // ====================================================================================
    // Database Performance Monitoring
    // ====================================================================================
    
    /**
     * Record database query metrics.
     * 
     * @param duration Query duration
     * @param queryType Type of query (SELECT, INSERT, UPDATE, DELETE)
     * @param tableName Table name
     */
    public void recordDbQuery(Duration duration, String queryType, String tableName) {
        long durationMs = duration.toMillis();
        
        dbQueryTime.record(duration);
        dbQueries.increment();
        
        // Check for performance threshold violations
        if (durationMs > DB_QUERY_TIME_THRESHOLD_MS) {
            logger.warn("Slow query detected: {} on {} took {}ms (threshold: {}ms)",
                queryType, tableName, durationMs, DB_QUERY_TIME_THRESHOLD_MS);
            
            alertOnSlowQuery(queryType, tableName, durationMs);
        }
    }
    
    /**
     * Record database error.
     * 
     * @param errorType Type of error
     * @param errorMessage Error message
     */
    public void recordDbError(String errorType, String errorMessage) {
        dbErrors.increment();
        logger.error("Database error: {} - {}", errorType, errorMessage);
        
        alertOnError("DB_ERROR", errorType, errorMessage);
    }
    
    /**
     * Update connection pool metrics.
     * 
     * @param active Active connections
     * @param idle Idle connections
     */
    public void updateConnectionPoolMetrics(int active, int idle) {
        dbConnectionPoolActive.set(active);
        dbConnectionPoolIdle.set(idle);
    }
    
    // ====================================================================================
    // Event Streaming Performance Monitoring
    // ====================================================================================
    
    /**
     * Record event publish metrics.
     * 
     * @param latency Publish latency
     * @param eventType Type of event
     */
    public void recordEventPublish(Duration latency, String eventType) {
        eventPublishLatency.record(latency);
        eventsPublished.increment();
        
        // Tag the counter with event type
        meterRegistry.counter("datacloud.events.published", "type", eventType).increment();
    }
    
    /**
     * Record event consume metrics.
     * 
     * @param latency Consume latency
     * @param eventType Type of event
     */
    public void recordEventConsume(Duration latency, String eventType) {
        eventConsumeLatency.record(latency);
        eventsConsumed.increment();
        
        // Tag the counter with event type
        meterRegistry.counter("datacloud.events.consumed", "type", eventType).increment();
    }
    
    /**
     * Update consumer lag metric.
     * 
     * @param lag Consumer lag in number of events
     */
    public void setConsumerLag(int lag) {
        consumerLag.set(lag);
        
        if (lag > 1000) {
            alertOnHighConsumerLag(lag);
        }
    }
    
    /**
     * Check event throughput and alert if below threshold.
     * 
     * @param eventsPerSecond Current throughput
     */
    public void checkEventThroughput(double eventsPerSecond) {
        if (eventsPerSecond < MIN_EVENT_THROUGHPUT) {
            alertOnSLOViolation("EVENT_THROUGHPUT", "event-streaming", 
                (long) eventsPerSecond, (long) MIN_EVENT_THROUGHPUT);
        }
    }
    
    // ====================================================================================
    // Business Metrics Monitoring
    // ====================================================================================
    
    /**
     * Record user action.
     * 
     * @param actionType Type of action (e.g., "entity.create", "query.execute")
     * @param userId User ID
     * @param tenantId Tenant ID
     */
    public void recordUserAction(String actionType, String userId, String tenantId) {
        userActions.increment();
        
        // Tag with action type, user, and tenant
        meterRegistry.counter("datacloud.user.actions", 
            "action", actionType,
            "user", userId,
            "tenant", tenantId).increment();
    }
    
    /**
     * Record feature usage.
     * 
     * @param featureName Name of feature
     * @param tenantId Tenant ID
     */
    public void recordFeatureUsage(String featureName, String tenantId) {
        featureUsage.increment();
        
        // Tag with feature name and tenant
        meterRegistry.counter("datacloud.feature.usage",
            "feature", featureName,
            "tenant", tenantId).increment();
    }
    
    /**
     * Update active users count.
     * 
     * @param count Number of active users
     */
    public void setActiveUsers(int count) {
        activeUsers.set(count);
    }
    
    /**
     * Update tenant count.
     * 
     * @param count Number of tenants
     */
    public void setTenantCount(int count) {
        tenantCount.set(count);
    }
    
    // ====================================================================================
    // SLI/SLO Monitoring
    // ====================================================================================
    
    /**
     * Check API availability and record violation if below threshold.
     * 
     * @param availability Current availability (0.0 - 1.0)
     */
    public void checkApiAvailability(double availability) {
        meterRegistry.gauge("datacloud.api.availability", availability);
        
        if (availability < AVAILABILITY_THRESHOLD) {
            apiAvailabilityViolations.incrementAndGet();
            alertOnSLOViolation("API_AVAILABILITY", "system", 
                (long) (availability * 100), (long) (AVAILABILITY_THRESHOLD * 100));
        }
    }
    
    /**
     * Check error rate and alert if above threshold.
     * 
     * @param errorRate Current error rate (0.0 - 1.0)
     */
    public void checkErrorRate(double errorRate) {
        meterRegistry.gauge("datacloud.api.error_rate", errorRate);
        
        if (errorRate > MAX_ERROR_RATE) {
            alertOnSLOViolation("ERROR_RATE", "system",
                (long) (errorRate * 100), (long) (MAX_ERROR_RATE * 100));
        }
    }
    
    /**
     * Get current SLO status.
     * 
     * @return SLOStatus with current metrics
     */
    public SLOStatus getSLOStatus() {
        double apiP95 = getPercentileMillis(apiResponseTime, 0.95);
        double dbP95 = getPercentileMillis(dbQueryTime, 0.95);
        double errorRate = apiErrors.count() / Math.max(apiRequests.count(), 1);
        
        return new SLOStatus(
            apiP95,
            dbP95,
            errorRate,
            sloViolations.get(),
            apiAvailabilityViolations.get(),
            apiP95 <= API_RESPONSE_TIME_THRESHOLD_MS,
            dbP95 <= DB_QUERY_TIME_THRESHOLD_MS,
            errorRate <= MAX_ERROR_RATE
        );
    }
    
    // ====================================================================================
    // Alerting
    // ====================================================================================
    
    private void alertOnSLOViolation(String sloName, String resource, long actual, long threshold) {
        String message = String.format("SLO VIOLATION: %s on %s. Actual: %s, Threshold: %s",
            sloName, resource, actual, threshold);
        
        logger.error(message);
        
        // In production: send to alerting system (PagerDuty, OpsGenie, etc.)
        sendAlert("SLO_VIOLATION", message, "CRITICAL");
    }
    
    private void alertOnError(String errorType, String resource, Object details) {
        String message = String.format("ERROR: %s on %s. Details: %s", errorType, resource, details);
        
        logger.error(message);
        
        // Send alert for 5xx errors
        sendAlert("ERROR", message, "HIGH");
    }

    private static double getPercentileMillis(Timer timer, double percentile) {
        for (ValueAtPercentile valueAtPercentile : timer.takeSnapshot().percentileValues()) {
            if (Double.compare(valueAtPercentile.percentile(), percentile) == 0) {
                return valueAtPercentile.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0.0;
    }
    
    private void alertOnSlowQuery(String queryType, String tableName, long durationMs) {
        String message = String.format("SLOW QUERY: %s on %s took %dms", queryType, tableName, durationMs);
        
        logger.warn(message);
        
        // Only alert if consistently slow (would need additional tracking in production)
        if (durationMs > DB_QUERY_TIME_THRESHOLD_MS * 2) {
            sendAlert("SLOW_QUERY", message, "MEDIUM");
        }
    }
    
    private void alertOnHighConsumerLag(int lag) {
        String message = String.format("HIGH CONSUMER LAG: %d events", lag);
        
        logger.warn(message);
        
        sendAlert("HIGH_LAG", message, "MEDIUM");
    }
    
    private void sendAlert(String alertType, String message, String severity) {
        // In production: integrate with alerting system
        // This is a placeholder for the actual implementation
        logger.info("ALERT: Type={}, Severity={}, Message={}", alertType, severity, message);
        
        // Example integration points:
        // - PagerDuty
        // - OpsGenie
        // - Slack notifications
        // - Email notifications
        // - Webhook calls
    }
    
    // ====================================================================================
    // Metrics Export
    // ====================================================================================
    
    /**
     * Get Prometheus metrics in scrape format.
     * 
     * @return Prometheus-formatted metrics
     */
    public String getPrometheusMetrics() {
        return meterRegistry.scrape();
    }
    
    /**
     * Get the meter registry for custom metrics.
     * 
     * @return PrometheusMeterRegistry
     */
    public PrometheusMeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
    
    // ====================================================================================
    // Inner Classes
    // ====================================================================================
    
    public static class SLOStatus {
        private final double apiP95Latency;
        private final double dbP95Latency;
        private final double errorRate;
        private final int sloViolations;
        private final int availabilityViolations;
        private final boolean apiLatencySLOMet;
        private final boolean dbLatencySLOMet;
        private final boolean errorRateSLOMet;
        
        public SLOStatus(double apiP95Latency, double dbP95Latency, double errorRate,
                        int sloViolations, int availabilityViolations,
                        boolean apiLatencySLOMet, boolean dbLatencySLOMet, boolean errorRateSLOMet) {
            this.apiP95Latency = apiP95Latency;
            this.dbP95Latency = dbP95Latency;
            this.errorRate = errorRate;
            this.sloViolations = sloViolations;
            this.availabilityViolations = availabilityViolations;
            this.apiLatencySLOMet = apiLatencySLOMet;
            this.dbLatencySLOMet = dbLatencySLOMet;
            this.errorRateSLOMet = errorRateSLOMet;
        }
        
        public double getApiP95Latency() { return apiP95Latency; }
        public double getDbP95Latency() { return dbP95Latency; }
        public double getErrorRate() { return errorRate; }
        public int getSloViolations() { return sloViolations; }
        public int getAvailabilityViolations() { return availabilityViolations; }
        public boolean isApiLatencySLOMet() { return apiLatencySLOMet; }
        public boolean isDbLatencySLOMet() { return dbLatencySLOMet; }
        public boolean isErrorRateSLOMet() { return errorRateSLOMet; }
        
        public boolean areAllSLOsMet() {
            return apiLatencySLOMet && dbLatencySLOMet && errorRateSLOMet;
        }
        
        @Override
        public String toString() {
            return String.format(
                "SLOStatus{apiP95=%.2fms, dbP95=%.2fms, errorRate=%.4f, violations=%d, allMet=%s}",
                apiP95Latency, dbP95Latency, errorRate, sloViolations, areAllSLOsMet()
            );
        }
    }
}
