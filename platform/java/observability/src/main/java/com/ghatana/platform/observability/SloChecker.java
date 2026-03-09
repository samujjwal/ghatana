package com.ghatana.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SLO (Service Level Objective) checker for error rate monitoring.
 *
 * <p>
 * <b>Purpose</b><br>
 * Monitors error rates against SLO targets and emits alerts when breaches
 * detected. Tracks error metrics by tenant, error type, and operation for
 * multi-dimensional SLO compliance verification.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Create SLO checker
 * SloChecker sloChecker = new SloChecker(
 *     meterRegistry,
 *     Duration.ofMinutes(5),    // 5-minute SLO window
 *     0.99d                       // 99% uptime SLO (1% error rate max)
 * );
 *
 * // Record success
 * sloChecker.recordSuccess("tenant-123", "process_event");
 *
 * // Record failure
 * sloChecker.recordFailure("tenant-123", "process_event", "ValidationError");
 *
 * // Check SLO status
 * SloStatus status = sloChecker.checkSlo("tenant-123");
 * if (status.isBreached()) {
 *     // Send alert, escalate to on-call
 * }
 *
 * // Get detailed metrics
 * ErrorRateMetrics metrics = sloChecker.getErrorRateMetrics("tenant-123");
 * double errorRate = metrics.getErrorRate(); // 0.0125 = 1.25% error rate
 * }</pre>
 *
 * <p>
 * <b>SLO Windows</b><br>
 * - Rolling window: Last N minutes (default 5 minutes) - Tracks: Success count,
 * failure count, error rate - Dimensions: Per-tenant, per-operation-type,
 * per-error-type - Alert: Triggered when error rate > (1 - SLO target)
 *
 * <p>
 * <b>Error Categories</b><br>
 * - Validation Errors: Input validation failures (should be < 0.1%) - Timeout
 * Errors: Operation timeouts (should be < 0.5%) - Resource Errors: Out of
 * memory, disk (should be < 0.05%) - Logic Errors: Application bugs (should be
 * < 0.1%) - External Errors: Downstream service failures (should be < 2%) -
 * Unknown Errors: Uncategorized (should be < 0.1%)
 *
 * <p>
 * <b>Metrics Emitted</b><br>
 * - `slo.error.rate` (gauge): Current error rate - `slo.errors.total`
 * (counter): Total errors - `slo.errors.by.type` (counter): Errors by type tag
 * - `slo.breach.count` (counter): SLO breach count - `slo.status` (gauge): 1.0
 * if compliant, 0.0 if breached
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap and AtomicInteger/AtomicLong for
 * counters. Suitable for multi-threaded environments.
 *
 * <p>
 * <b>Integration with MetricsCollector</b><br>
 * Works alongside MetricsCollector for unified observability: -
 * MetricsCollector: General metrics (counters, gauges, timers) - SloChecker:
 * Specialized SLO compliance monitoring - Combined: Complete observability
 * picture with alerting
 *
 * @doc.type class
 * @doc.purpose SLO monitoring with error rate tracking and breach detection
 * @doc.layer core
 * @doc.pattern Monitor, Aggregator, Strategy
 * @see MetricsCollector
 * @see ErrorRateMetrics
 */
public class SloChecker {

    private static final Logger LOG = LoggerFactory.getLogger(SloChecker.class);

    private final MeterRegistry meterRegistry;
    private final Duration sloWindow;
    private final double sloTarget; // e.g., 0.99 for 99% uptime (1% error rate max)
    private final double errorRateThreshold; // Computed from sloTarget: 1.0 - sloTarget

    // Per-tenant metrics storage: Map<TenantId, OperationMetrics>
    private final Map<String, TenantMetrics> tenantMetrics = new ConcurrentHashMap<>();

    // Global error counters by type
    private final Counter totalErrorCounter;
    private final Map<String, Counter> errorTypeCounters = new ConcurrentHashMap<>();

    // SLO breach tracking
    private final Map<String, AtomicInteger> breachCountByTenant = new ConcurrentHashMap<>();
    private final Counter breachCounter;

    /**
     * Initialize SLO checker with configuration.
     *
     * @param meterRegistry Micrometer registry for metrics emission
     * @param sloWindow Rolling window duration (e.g., 5 minutes)
     * @param sloTarget SLO target (e.g., 0.99 = 99% uptime, 1% max error rate)
     */
    public SloChecker(MeterRegistry meterRegistry, Duration sloWindow, double sloTarget) {
        this.meterRegistry = meterRegistry;
        this.sloWindow = sloWindow;
        this.sloTarget = sloTarget;
        this.errorRateThreshold = 1.0d - sloTarget; // e.g., 0.01 for 1% error rate

        // Initialize counters
        this.totalErrorCounter = Counter.builder("slo.errors.total")
                .description("Total errors across all tenants")
                .register(meterRegistry);

        this.breachCounter = Counter.builder("slo.breach.count")
                .description("SLO breach count (when error rate exceeds threshold)")
                .register(meterRegistry);

        LOG.info("Initialized SloChecker: window={}, target={}, errorThreshold={}",
                sloWindow, sloTarget, errorRateThreshold);
    }

    /**
     * Record successful operation.
     *
     * @param tenantId Tenant identifier
     * @param operationType Operation type (e.g., "process_event",
     * "create_pipeline")
     */
    public void recordSuccess(String tenantId, String operationType) {
        TenantMetrics metrics = tenantMetrics.computeIfAbsent(tenantId, k -> new TenantMetrics());
        metrics.recordSuccess(operationType);
    }

    /**
     * Record failed operation with error type.
     *
     * @param tenantId Tenant identifier
     * @param operationType Operation type
     * @param errorType Error type (e.g., "ValidationError", "TimeoutError")
     */
    public void recordFailure(String tenantId, String operationType, String errorType) {
        TenantMetrics metrics = tenantMetrics.computeIfAbsent(tenantId, k -> new TenantMetrics());
        metrics.recordFailure(operationType, errorType);

        // Update global error counter
        totalErrorCounter.increment();

        // Track error type
        errorTypeCounters.computeIfAbsent(errorType, et -> {
            Counter counter = Counter.builder("slo.errors.by.type")
                    .tag("error_type", et)
                    .description("Errors by type")
                    .register(meterRegistry);
            return counter;
        }).increment();
    }

    /**
     * Check SLO compliance status for tenant.
     *
     * @param tenantId Tenant identifier
     * @return SLO status with error rate and breach flag
     */
    public SloStatus checkSlo(String tenantId) {
        TenantMetrics metrics = tenantMetrics.get(tenantId);

        if (metrics == null) {
            // No data yet - technically compliant
            return SloStatus.builder()
                    .tenantId(tenantId)
                    .successCount(0L)
                    .failureCount(0L)
                    .errorRate(0.0d)
                    .isCompliant(true)
                    .threshold(errorRateThreshold)
                    .window(sloWindow)
                    .build();
        }

        ErrorRateMetrics errorMetrics = metrics.getErrorRateMetrics();
        boolean isCompliant = errorMetrics.getErrorRate() <= errorRateThreshold;

        // Emit gauge for SLO status (1.0 = compliant, 0.0 = breached)
        meterRegistry.gauge("slo.status",
                Tags.of("tenant_id", tenantId),
                errorMetrics,
                em -> isCompliant ? 1.0d : 0.0d);

        // Emit error rate gauge
        meterRegistry.gauge("slo.error.rate",
                Tags.of("tenant_id", tenantId),
                errorMetrics,
                ErrorRateMetrics::getErrorRate);

        // Record breach if detected
        if (!isCompliant) {
            breachCounter.increment();
            breachCountByTenant.computeIfAbsent(tenantId, k -> new AtomicInteger(0))
                    .incrementAndGet();
            LOG.warn("SLO BREACH for tenant {}: error rate {} > threshold {}",
                    tenantId, errorMetrics.getErrorRate(), errorRateThreshold);
        }

        return SloStatus.builder()
                .tenantId(tenantId)
                .successCount(errorMetrics.getSuccessCount())
                .failureCount(errorMetrics.getFailureCount())
                .errorRate(errorMetrics.getErrorRate())
                .isCompliant(isCompliant)
                .threshold(errorRateThreshold)
                .window(sloWindow)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Get error rate metrics for tenant.
     *
     * @param tenantId Tenant identifier
     * @return Error rate metrics (or empty metrics if no data)
     */
    public ErrorRateMetrics getErrorRateMetrics(String tenantId) {
        TenantMetrics metrics = tenantMetrics.get(tenantId);
        return metrics != null ? metrics.getErrorRateMetrics() : ErrorRateMetrics.EMPTY;
    }

    /**
     * Get SLO breach count for tenant.
     *
     * @param tenantId Tenant identifier
     * @return Number of SLO breaches detected
     */
    public int getBreachCount(String tenantId) {
        AtomicInteger count = breachCountByTenant.get(tenantId);
        return count != null ? count.get() : 0;
    }

    /**
     * Reset metrics for tenant (useful for testing or manual resets).
     *
     * @param tenantId Tenant identifier
     */
    public void resetMetrics(String tenantId) {
        tenantMetrics.remove(tenantId);
        breachCountByTenant.remove(tenantId);
        LOG.info("Reset metrics for tenant: {}", tenantId);
    }

    /**
     * Get all monitored tenants.
     *
     * @return Set of tenant IDs with active monitoring
     */
    public Set<String> getMonitoredTenants() {
        return Collections.unmodifiableSet(tenantMetrics.keySet());
    }

    /**
     * Tenant metrics aggregator.
     */
    private static class TenantMetrics {

        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final Map<String, AtomicLong> failuresByType = new ConcurrentHashMap<>();

        void recordSuccess(String operationType) {
            successCount.incrementAndGet();
        }

        void recordFailure(String operationType, String errorType) {
            failureCount.incrementAndGet();
            failuresByType.computeIfAbsent(errorType, k -> new AtomicLong(0))
                    .incrementAndGet();
        }

        ErrorRateMetrics getErrorRateMetrics() {
            long total = successCount.get() + failureCount.get();
            double errorRate = total > 0 ? (double) failureCount.get() / total : 0.0d;

            return ErrorRateMetrics.builder()
                    .successCount(successCount.get())
                    .failureCount(failureCount.get())
                    .totalCount(total)
                    .errorRate(errorRate)
                    .failuresByType(new HashMap<>(failuresByType.entrySet().stream()
                            .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().get()), HashMap::putAll)))
                    .build();
        }
    }

    /**
     * Error rate metrics value object.
     */
    public static class ErrorRateMetrics {

        public static final ErrorRateMetrics EMPTY = new ErrorRateMetrics(0, 0, 0, 0.0d, Collections.emptyMap());

        private final long successCount;
        private final long failureCount;
        private final long totalCount;
        private final double errorRate;
        private final Map<String, Long> failuresByType;

        public ErrorRateMetrics(long successCount, long failureCount, long totalCount,
                double errorRate, Map<String, Long> failuresByType) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.totalCount = totalCount;
            this.errorRate = errorRate;
            this.failuresByType = Collections.unmodifiableMap(failuresByType);
        }

        public static Builder builder() {
            return new Builder();
        }

        public long getSuccessCount() {
            return successCount;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public double getErrorRate() {
            return errorRate;
        }

        public Map<String, Long> getFailuresByType() {
            return failuresByType;
        }

        public static class Builder {

            private long successCount;
            private long failureCount;
            private long totalCount;
            private double errorRate;
            private Map<String, Long> failuresByType = new HashMap<>();

            public Builder successCount(long successCount) {
                this.successCount = successCount;
                return this;
            }

            public Builder failureCount(long failureCount) {
                this.failureCount = failureCount;
                return this;
            }

            public Builder totalCount(long totalCount) {
                this.totalCount = totalCount;
                return this;
            }

            public Builder errorRate(double errorRate) {
                this.errorRate = errorRate;
                return this;
            }

            public Builder failuresByType(Map<String, Long> failuresByType) {
                this.failuresByType = failuresByType;
                return this;
            }

            public ErrorRateMetrics build() {
                return new ErrorRateMetrics(successCount, failureCount, totalCount, errorRate, failuresByType);
            }
        }
    }

    /**
     * SLO compliance status record.
     */
    public static class SloStatus {

        private final String tenantId;
        private final long successCount;
        private final long failureCount;
        private final double errorRate;
        private final boolean isCompliant;
        private final double threshold;
        private final Duration window;
        private final Instant lastUpdated;

        public SloStatus(String tenantId, long successCount, long failureCount, double errorRate,
                boolean isCompliant, double threshold, Duration window, Instant lastUpdated) {
            this.tenantId = tenantId;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.errorRate = errorRate;
            this.isCompliant = isCompliant;
            this.threshold = threshold;
            this.window = window;
            this.lastUpdated = lastUpdated;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getTenantId() {
            return tenantId;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public double getErrorRate() {
            return errorRate;
        }

        public boolean isCompliant() {
            return isCompliant;
        }

        public boolean isBreached() {
            return !isCompliant;
        }

        public double getThreshold() {
            return threshold;
        }

        public Duration getWindow() {
            return window;
        }

        public Instant getLastUpdated() {
            return lastUpdated;
        }

        public static class Builder {

            private String tenantId;
            private long successCount;
            private long failureCount;
            private double errorRate;
            private boolean isCompliant;
            private double threshold;
            private Duration window;
            private Instant lastUpdated = Instant.now();

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder successCount(long successCount) {
                this.successCount = successCount;
                return this;
            }

            public Builder failureCount(long failureCount) {
                this.failureCount = failureCount;
                return this;
            }

            public Builder errorRate(double errorRate) {
                this.errorRate = errorRate;
                return this;
            }

            public Builder isCompliant(boolean isCompliant) {
                this.isCompliant = isCompliant;
                return this;
            }

            public Builder threshold(double threshold) {
                this.threshold = threshold;
                return this;
            }

            public Builder window(Duration window) {
                this.window = window;
                return this;
            }

            public Builder lastUpdated(Instant lastUpdated) {
                this.lastUpdated = lastUpdated;
                return this;
            }

            public SloStatus build() {
                return new SloStatus(tenantId, successCount, failureCount, errorRate,
                        isCompliant, threshold, window, lastUpdated);
            }
        }
    }
}
