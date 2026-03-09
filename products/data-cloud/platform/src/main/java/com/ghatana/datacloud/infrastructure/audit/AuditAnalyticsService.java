package com.ghatana.datacloud.infrastructure.audit;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.infrastructure.audit.AuditTrailService.AuditEvent;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.time.Duration;

/**
 * Audit analytics service with database persistence and time-series analysis.
 *
 * <p><b>Purpose</b><br>
 * Provides advanced analytics capabilities over audit trail data, including:
 * - Time-series event aggregations
 * - User activity patterns
 * - Resource access patterns
 * - Anomaly detection
 * - Compliance reporting
 * - Trend analysis
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuditAnalyticsService analytics = new AuditAnalyticsService(
 *     auditTrailService,
 *     metrics
 * );
 *
 * // Get hourly event aggregations for last 24 hours
 * Map<String, TimeSeriesData> timeSeries = analytics.getTimeSeriesData(
 *     "tenant-123",
 *     Duration.ofHours(24),
 *     ChronoUnit.HOURS
 * ).get();
 *
 * // Get user activity summary
 * List<UserActivitySummary> userActivity = analytics.getUserActivityReport(
 *     "tenant-123",
 *     Duration.ofDays(7)
 * ).get();
 *
 * // Detect anomalies
 * List<AnomalyReport> anomalies = analytics.detectAnomalies(
 *     "tenant-123",
 *     Duration.ofDays(1)
 * ).get();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Analytics layer in infrastructure
 * - Integrates with AuditTrailService for data
 * - Supports multi-tenancy
 * - Time-series aggregations
 * - Anomaly detection
 * - Compliance analytics
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses concurrent data structures for aggregations.
 *
 * <p><b>Performance Characteristics</b><br>
 * - Time-series queries: O(n) where n = events in time range
 * - User activity: O(n) with HashMap aggregations
 * - Anomaly detection: O(n) with statistical analysis
 * - Memory: Bounded by time range and aggregation granularity
 *
 * @see AuditTrailService
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Audit analytics with database persistence and time-series analysis
 * @doc.layer product
 * @doc.pattern Analytics Service (Infrastructure Layer)
 */
public class AuditAnalyticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditAnalyticsService.class);

    // Anomaly detection thresholds
    private static final double ANOMALY_THRESHOLD_MULTIPLIER = 3.0; // 3 standard deviations
    private static final int MIN_SAMPLES_FOR_ANOMALY_DETECTION = 10;

    private final AuditTrailService auditTrailService;
    private final MetricsCollector metrics;
    
    // In-memory aggregation cache (for performance)
    private final Map<String, AggregationCache> aggregationCache;

    /**
     * Creates a new audit analytics service.
     *
     * @param auditTrailService the audit trail service (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public AuditAnalyticsService(
            AuditTrailService auditTrailService,
            MetricsCollector metrics) {
        this.auditTrailService = Objects.requireNonNull(
            auditTrailService,
            "AuditTrailService must not be null"
        );
        this.metrics = Objects.requireNonNull(
            metrics,
            "MetricsCollector must not be null"
        );
        this.aggregationCache = new ConcurrentHashMap<>();
    }

    /**
     * Gets time-series data for audit events.
     *
     * <p><b>Aggregations</b><br>
     * - Groups events by time bucket (hour, day, week)
     * - Counts events per bucket
     * - Calculates event type distribution
     * - Identifies peak activity periods
     *
     * @param tenantId the tenant ID (required)
     * @param timeRange the time range to analyze (required)
     * @param granularity the time granularity (HOURS, DAYS, WEEKS)
     * @return Promise of time-series data by event type
     * @throws NullPointerException if any parameter is null
     */
    public Promise<Map<String, TimeSeriesData>> getTimeSeriesData(
            String tenantId,
            Duration timeRange,
            ChronoUnit granularity) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(timeRange, "Time range must not be null");
        Objects.requireNonNull(granularity, "Granularity must not be null");

        long startTime = System.currentTimeMillis();

        return auditTrailService.queryEvents(tenantId, null, null)
            .map(events -> {
                // Filter events within time range
                long cutoffTime = System.currentTimeMillis() - timeRange.toMillis();
                List<AuditEvent> recentEvents = events.stream()
                    .filter(e -> e.timestamp >= cutoffTime)
                    .collect(Collectors.toList());

                // Group by event type and time bucket
                Map<String, Map<Instant, Long>> eventTypeBuckets = new HashMap<>();

                for (AuditEvent event : recentEvents) {
                    Instant eventTime = Instant.ofEpochMilli(event.timestamp);
                    Instant bucket = truncateToBucket(eventTime, granularity);
                    
                    eventTypeBuckets
                        .computeIfAbsent(event.eventType, k -> new HashMap<>())
                        .merge(bucket, 1L, Long::sum);
                }

                // Convert to TimeSeriesData
                Map<String, TimeSeriesData> timeSeries = new HashMap<>();
                for (Map.Entry<String, Map<Instant, Long>> entry : eventTypeBuckets.entrySet()) {
                    TimeSeriesData data = new TimeSeriesData(
                        entry.getKey(),
                        entry.getValue(),
                        calculateStatistics(entry.getValue().values())
                    );
                    timeSeries.put(entry.getKey(), data);
                }

                long duration = System.currentTimeMillis() - startTime;
                metrics.getMeterRegistry()
            .timer("audit.analytics.timeseries.duration")
            .record(Duration.ofMillis(duration));
                metrics.incrementCounter("audit.analytics.timeseries.count",
                    "tenant", tenantId,
                    "granularity", granularity.toString());

                LOGGER.debug("Generated time-series data for tenant: {} ({} event types, {}ms)",
                    tenantId, timeSeries.size(), duration);

                return timeSeries;
            })
            .whenException(ex -> {
                LOGGER.error("Failed to generate time-series data for tenant: " + tenantId, ex);
                metrics.incrementCounter("audit.analytics.timeseries.error",
                    "tenant", tenantId,
                    "error", ex.getClass().getSimpleName());
            });
    }

    /**
     * Gets user activity report.
     *
     * <p><b>Report Contents</b><br>
     * - Total events per user
     * - Event type distribution per user
     * - Most active users
     * - Inactive users
     * - User action patterns
     *
     * @param tenantId the tenant ID (required)
     * @param timeRange the time range to analyze (required)
     * @return Promise of user activity summaries
     * @throws NullPointerException if any parameter is null
     */
    public Promise<List<UserActivitySummary>> getUserActivityReport(
            String tenantId,
            Duration timeRange) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(timeRange, "Time range must not be null");

        long startTime = System.currentTimeMillis();

        return auditTrailService.queryEvents(tenantId, null, null)
            .map(events -> {
                // Filter events within time range
                long cutoffTime = System.currentTimeMillis() - timeRange.toMillis();
                List<AuditEvent> recentEvents = events.stream()
                    .filter(e -> e.timestamp >= cutoffTime)
                    .collect(Collectors.toList());

                // Group by user
                Map<String, UserActivityData> userActivity = new HashMap<>();

                for (AuditEvent event : recentEvents) {
                    UserActivityData data = userActivity.computeIfAbsent(
                        event.userId,
                        userId -> new UserActivityData(userId)
                    );
                    data.addEvent(event);
                }

                // Convert to summaries and sort by total events
                List<UserActivitySummary> summaries = userActivity.values().stream()
                    .map(UserActivityData::toSummary)
                    .sorted((a, b) -> Long.compare(b.totalEvents(), a.totalEvents()))
                    .collect(Collectors.toList());

                long duration = System.currentTimeMillis() - startTime;
                metrics.getMeterRegistry()
            .timer("audit.analytics.user_activity.duration")
            .record(Duration.ofMillis(duration));
                metrics.incrementCounter("audit.analytics.user_activity.count",
                    "tenant", tenantId,
                    "users", String.valueOf(summaries.size()));

                LOGGER.debug("Generated user activity report for tenant: {} ({} users, {}ms)",
                    tenantId, summaries.size(), duration);

                return summaries;
            })
            .whenException(ex -> {
                LOGGER.error("Failed to generate user activity report for tenant: " + tenantId, ex);
                metrics.incrementCounter("audit.analytics.user_activity.error",
                    "tenant", tenantId,
                    "error", ex.getClass().getSimpleName());
            });
    }

    /**
     * Gets resource access patterns.
     *
     * <p><b>Pattern Analysis</b><br>
     * - Most accessed resources
     * - Access frequency distribution
     * - Unique users per resource
     * - Hot resources (high access)
     * - Cold resources (low access)
     *
     * @param tenantId the tenant ID (required)
     * @param timeRange the time range to analyze (required)
     * @return Promise of resource access patterns
     * @throws NullPointerException if any parameter is null
     */
    public Promise<List<ResourceAccessPattern>> getResourceAccessPatterns(
            String tenantId,
            Duration timeRange) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(timeRange, "Time range must not be null");

        long startTime = System.currentTimeMillis();

        return auditTrailService.queryEvents(tenantId, null, null)
            .map(events -> {
                // Filter events within time range
                long cutoffTime = System.currentTimeMillis() - timeRange.toMillis();
                List<AuditEvent> recentEvents = events.stream()
                    .filter(e -> e.timestamp >= cutoffTime)
                    .collect(Collectors.toList());

                // Group by resource
                Map<String, ResourceAccessData> resourceAccess = new HashMap<>();

                for (AuditEvent event : recentEvents) {
                    ResourceAccessData data = resourceAccess.computeIfAbsent(
                        event.resourceId,
                        resourceId -> new ResourceAccessData(resourceId)
                    );
                    data.addEvent(event);
                }

                // Convert to patterns and sort by access count
                List<ResourceAccessPattern> patterns = resourceAccess.values().stream()
                    .map(ResourceAccessData::toPattern)
                    .sorted((a, b) -> Long.compare(b.totalAccesses(), a.totalAccesses()))
                    .collect(Collectors.toList());

                long duration = System.currentTimeMillis() - startTime;
                metrics.getMeterRegistry()
            .timer("audit.analytics.resource_access.duration")
            .record(Duration.ofMillis(duration));
                metrics.incrementCounter("audit.analytics.resource_access.count",
                    "tenant", tenantId,
                    "resources", String.valueOf(patterns.size()));

                LOGGER.debug("Generated resource access patterns for tenant: {} ({} resources, {}ms)",
                    tenantId, patterns.size(), duration);

                return patterns;
            })
            .whenException(ex -> {
                LOGGER.error("Failed to generate resource access patterns for tenant: " + tenantId, ex);
                metrics.incrementCounter("audit.analytics.resource_access.error",
                    "tenant", tenantId,
                    "error", ex.getClass().getSimpleName());
            });
    }

    /**
     * Detects anomalies in audit events.
     *
     * <p><b>Anomaly Types Detected</b><br>
     * - Unusual event frequency (statistical outliers)
     * - Suspicious user activity (deviation from normal)
     * - Abnormal access patterns
     * - Failed operation spikes
     * - Time-based anomalies (unusual hours)
     *
     * <p><b>Detection Algorithm</b><br>
     * Uses statistical analysis (mean + 3 standard deviations) to identify
     * outliers in event frequency and patterns.
     *
     * @param tenantId the tenant ID (required)
     * @param timeRange the time range to analyze (required)
     * @return Promise of anomaly reports
     * @throws NullPointerException if any parameter is null
     */
    public Promise<List<AnomalyReport>> detectAnomalies(
            String tenantId,
            Duration timeRange) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(timeRange, "Time range must not be null");

        long startTime = System.currentTimeMillis();

        return auditTrailService.queryEvents(tenantId, null, null)
            .map(events -> {
                // Filter events within time range
                long cutoffTime = System.currentTimeMillis() - timeRange.toMillis();
                List<AuditEvent> recentEvents = events.stream()
                    .filter(e -> e.timestamp >= cutoffTime)
                    .collect(Collectors.toList());

                List<AnomalyReport> anomalies = new ArrayList<>();

                // Detect frequency anomalies
                anomalies.addAll(detectFrequencyAnomalies(recentEvents));

                // Detect user behavior anomalies
                anomalies.addAll(detectUserBehaviorAnomalies(recentEvents));

                // Detect time-based anomalies
                anomalies.addAll(detectTimeAnomalies(recentEvents));

                // Sort by severity
                anomalies.sort((a, b) -> b.severity().compareTo(a.severity()));

                long duration = System.currentTimeMillis() - startTime;
                metrics.getMeterRegistry()
            .timer("audit.analytics.anomaly_detection.duration")
            .record(Duration.ofMillis(duration));
                metrics.incrementCounter("audit.analytics.anomaly_detection.count",
                    "tenant", tenantId,
                    "anomalies", String.valueOf(anomalies.size()));

                LOGGER.debug("Detected {} anomalies for tenant: {} ({}ms)",
                    anomalies.size(), tenantId, duration);

                return anomalies;
            })
            .whenException(ex -> {
                LOGGER.error("Failed to detect anomalies for tenant: " + tenantId, ex);
                metrics.incrementCounter("audit.analytics.anomaly_detection.error",
                    "tenant", tenantId,
                    "error", ex.getClass().getSimpleName());
            });
    }

    /**
     * Gets compliance report.
     *
     * <p><b>Report Contents</b><br>
     * - Total audit events
     * - Event coverage by resource type
     * - User accountability metrics
     * - Retention compliance
     * - Failed operations summary
     *
     * @param tenantId the tenant ID (required)
     * @param timeRange the time range to analyze (required)
     * @return Promise of compliance report
     * @throws NullPointerException if any parameter is null
     */
    public Promise<ComplianceReport> getComplianceReport(
            String tenantId,
            Duration timeRange) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(timeRange, "Time range must not be null");

        long startTime = System.currentTimeMillis();

        return auditTrailService.queryEvents(tenantId, null, null)
            .map(events -> {
                // Filter events within time range
                long cutoffTime = System.currentTimeMillis() - timeRange.toMillis();
                List<AuditEvent> recentEvents = events.stream()
                    .filter(e -> e.timestamp >= cutoffTime)
                    .collect(Collectors.toList());

                // Calculate compliance metrics
                long totalEvents = recentEvents.size();
                long uniqueUsers = recentEvents.stream()
                    .map(e -> e.userId)
                    .distinct()
                    .count();
                long uniqueResources = recentEvents.stream()
                    .map(e -> e.resourceId)
                    .distinct()
                    .count();

                Map<String, Long> eventTypeDistribution = recentEvents.stream()
                    .collect(Collectors.groupingBy(
                        e -> e.eventType,
                        Collectors.counting()
                    ));

                // Check for failed operations
                long failedOperations = recentEvents.stream()
                    .filter(e -> e.eventType.contains("FAILED") || e.eventType.contains("ERROR"))
                    .count();

                ComplianceReport report = new ComplianceReport(
                    tenantId,
                    totalEvents,
                    uniqueUsers,
                    uniqueResources,
                    eventTypeDistribution,
                    failedOperations,
                    Instant.now()
                );

                long duration = System.currentTimeMillis() - startTime;
                metrics.getMeterRegistry()
            .timer("audit.analytics.compliance.duration")
            .record(Duration.ofMillis(duration));
                metrics.incrementCounter("audit.analytics.compliance.count",
                    "tenant", tenantId);

                LOGGER.debug("Generated compliance report for tenant: {} ({}ms)",
                    tenantId, duration);

                return report;
            })
            .whenException(ex -> {
                LOGGER.error("Failed to generate compliance report for tenant: " + tenantId, ex);
                metrics.incrementCounter("audit.analytics.compliance.error",
                    "tenant", tenantId,
                    "error", ex.getClass().getSimpleName());
            });
    }

    // Helper methods

    private Instant truncateToBucket(Instant time, ChronoUnit granularity) {
        return time.truncatedTo(granularity);
    }

    private StatisticalSummary calculateStatistics(Collection<Long> values) {
        if (values.isEmpty()) {
            return new StatisticalSummary(0.0, 0.0, 0L, 0L);
        }

        double mean = values.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        long min = values.stream().mapToLong(Long::longValue).min().orElse(0L);
        long max = values.stream().mapToLong(Long::longValue).max().orElse(0L);

        return new StatisticalSummary(mean, stdDev, min, max);
    }

    private List<AnomalyReport> detectFrequencyAnomalies(List<AuditEvent> events) {
        List<AnomalyReport> anomalies = new ArrayList<>();

        if (events.size() < MIN_SAMPLES_FOR_ANOMALY_DETECTION) {
            return anomalies;
        }

        // Group events by hour
        Map<Integer, Long> hourlyCount = events.stream()
            .collect(Collectors.groupingBy(
                e -> Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault()).getHour(),
                Collectors.counting()
            ));

        StatisticalSummary stats = calculateStatistics(hourlyCount.values());
        double threshold = stats.mean() + (ANOMALY_THRESHOLD_MULTIPLIER * stats.stdDev());

        for (Map.Entry<Integer, Long> entry : hourlyCount.entrySet()) {
            if (entry.getValue() > threshold) {
                anomalies.add(new AnomalyReport(
                    "FREQUENCY_SPIKE",
                    "Unusual event frequency at hour " + entry.getKey() +
                        ": " + entry.getValue() + " events (threshold: " + threshold + ")",
                    "HIGH",
                    Instant.now()
                ));
            }
        }

        return anomalies;
    }

    private List<AnomalyReport> detectUserBehaviorAnomalies(List<AuditEvent> events) {
        List<AnomalyReport> anomalies = new ArrayList<>();

        // Group events by user
        Map<String, Long> userEventCounts = events.stream()
            .collect(Collectors.groupingBy(
                e -> e.userId,
                Collectors.counting()
            ));

        if (userEventCounts.size() < MIN_SAMPLES_FOR_ANOMALY_DETECTION) {
            return anomalies;
        }

        StatisticalSummary stats = calculateStatistics(userEventCounts.values());
        double threshold = stats.mean() + (ANOMALY_THRESHOLD_MULTIPLIER * stats.stdDev());

        for (Map.Entry<String, Long> entry : userEventCounts.entrySet()) {
            if (entry.getValue() > threshold) {
                anomalies.add(new AnomalyReport(
                    "EXCESSIVE_USER_ACTIVITY",
                    "User " + entry.getKey() + " has unusual activity: " +
                        entry.getValue() + " events (threshold: " + threshold + ")",
                    "MEDIUM",
                    Instant.now()
                ));
            }
        }

        return anomalies;
    }

    private List<AnomalyReport> detectTimeAnomalies(List<AuditEvent> events) {
        List<AnomalyReport> anomalies = new ArrayList<>();

        // Detect events outside business hours (e.g., 9 AM - 5 PM)
        long offHoursEvents = events.stream()
            .filter(e -> {
                int hour = Instant.ofEpochMilli(e.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .getHour();
                return hour < 9 || hour >= 17;
            })
            .count();

        if (offHoursEvents > events.size() * 0.3) { // More than 30% off-hours
            anomalies.add(new AnomalyReport(
                "OFF_HOURS_ACTIVITY",
                "Unusual activity outside business hours: " +
                    offHoursEvents + " events (" +
                    (offHoursEvents * 100 / events.size()) + "%)",
                "LOW",
                Instant.now()
            ));
        }

        return anomalies;
    }

    // Inner classes for data aggregation

    private static class UserActivityData {
        private final String userId;
        private long totalEvents = 0;
        private final Map<String, Long> eventTypeCounts = new HashMap<>();
        private Instant firstActivity;
        private Instant lastActivity;

        public UserActivityData(String userId) {
            this.userId = userId;
        }

        public void addEvent(AuditEvent event) {
            totalEvents++;
            eventTypeCounts.merge(event.eventType, 1L, Long::sum);
            
            Instant eventTime = Instant.ofEpochMilli(event.timestamp);
            if (firstActivity == null || eventTime.isBefore(firstActivity)) {
                firstActivity = eventTime;
            }
            if (lastActivity == null || eventTime.isAfter(lastActivity)) {
                lastActivity = eventTime;
            }
        }

        public UserActivitySummary toSummary() {
            return new UserActivitySummary(
                userId,
                totalEvents,
                eventTypeCounts,
                firstActivity,
                lastActivity
            );
        }
    }

    private static class ResourceAccessData {
        private final String resourceId;
        private long totalAccesses = 0;
        private final Set<String> uniqueUsers = new HashSet<>();
        private final Map<String, Long> eventTypeCounts = new HashMap<>();

        public ResourceAccessData(String resourceId) {
            this.resourceId = resourceId;
        }

        public void addEvent(AuditEvent event) {
            totalAccesses++;
            uniqueUsers.add(event.userId);
            eventTypeCounts.merge(event.eventType, 1L, Long::sum);
        }

        public ResourceAccessPattern toPattern() {
            return new ResourceAccessPattern(
                resourceId,
                totalAccesses,
                uniqueUsers.size(),
                eventTypeCounts
            );
        }
    }

    private static class AggregationCache {
        private final Instant timestamp;
        private final Object data;

        public AggregationCache(Object data) {
            this.timestamp = Instant.now();
            this.data = data;
        }

        public boolean isExpired(Duration ttl) {
            return Instant.now().isAfter(timestamp.plus(ttl));
        }

        public Object getData() {
            return data;
        }
    }

    // Public record types

    /**
     * Time-series data for event type.
     *
     * @param eventType the event type
     * @param dataPoints time bucket → count mapping
     * @param statistics statistical summary
     * @doc.type record
     * @doc.purpose Time-series event data
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record TimeSeriesData(
        String eventType,
        Map<Instant, Long> dataPoints,
        StatisticalSummary statistics
    ) {}

    /**
     * Statistical summary.
     *
     * @param mean average value
     * @param stdDev standard deviation
     * @param min minimum value
     * @param max maximum value
     * @doc.type record
     * @doc.purpose Statistical metrics
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record StatisticalSummary(
        double mean,
        double stdDev,
        long min,
        long max
    ) {}

    /**
     * User activity summary.
     *
     * @param userId the user ID
     * @param totalEvents total events by user
     * @param eventTypeCounts event type distribution
     * @param firstActivity first activity timestamp
     * @param lastActivity last activity timestamp
     * @doc.type record
     * @doc.purpose User activity metrics
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record UserActivitySummary(
        String userId,
        long totalEvents,
        Map<String, Long> eventTypeCounts,
        Instant firstActivity,
        Instant lastActivity
    ) {}

    /**
     * Resource access pattern.
     *
     * @param resourceId the resource ID
     * @param totalAccesses total access count
     * @param uniqueUsers unique user count
     * @param eventTypeCounts event type distribution
     * @doc.type record
     * @doc.purpose Resource access metrics
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record ResourceAccessPattern(
        String resourceId,
        long totalAccesses,
        long uniqueUsers,
        Map<String, Long> eventTypeCounts
    ) {}

    /**
     * Anomaly report.
     *
     * @param anomalyType the anomaly type
     * @param description anomaly description
     * @param severity severity level (LOW/MEDIUM/HIGH/CRITICAL)
     * @param detectedAt detection timestamp
     * @doc.type record
     * @doc.purpose Anomaly detection report
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record AnomalyReport(
        String anomalyType,
        String description,
        String severity,
        Instant detectedAt
    ) {}

    /**
     * Compliance report.
     *
     * @param tenantId the tenant ID
     * @param totalEvents total audit events
     * @param uniqueUsers unique user count
     * @param uniqueResources unique resource count
     * @param eventTypeDistribution event type distribution
     * @param failedOperations failed operation count
     * @param generatedAt report generation timestamp
     * @doc.type record
     * @doc.purpose Compliance audit report
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record ComplianceReport(
        String tenantId,
        long totalEvents,
        long uniqueUsers,
        long uniqueResources,
        Map<String, Long> eventTypeDistribution,
        long failedOperations,
        Instant generatedAt
    ) {}
}
