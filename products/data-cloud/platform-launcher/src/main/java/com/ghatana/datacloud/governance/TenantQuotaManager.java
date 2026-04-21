package com.ghatana.datacloud.governance;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.ghatana.datacloud.infrastructure.config.JpaThreadPoolConfig;

/**
 * Tenant resource quota management and monitoring.
 *
 * <p>This service provides comprehensive resource quota management:
 * <ul>
 *   <li>Storage quotas (per-tenant data limits)</li>
 *   <li>API rate limiting (requests per minute/hour)</li>
 *   <li>Concurrent connection limits</li>
 *   <li>Compute resource quotas (CPU, memory)</li>
 *   <li>Event streaming quotas (events per second)</li>
 * </ul>
 *
 * <h2>Quota Enforcement</h2>
 * <ul>
 *   <li>Hard limits: Operations rejected when exceeded</li>
 *   <li>Soft limits: Warnings issued when approaching limits</li>
 *   <li>Throttling: Rate limiting for API requests</li>
 *   <li>Alerts: Notifications when quotas are exceeded</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tenant resource quota management, enforcement, and monitoring
 * @doc.layer governance
 * @doc.pattern ResourceGovernance
 */
public class TenantQuotaManager {

    private static final Logger logger = LoggerFactory.getLogger(TenantQuotaManager.class);

    // Default quota limits
    private static final long DEFAULT_MAX_STORAGE_MB = 10240; // 10 GB
    private static final int DEFAULT_MAX_CONCURRENT_CONNECTIONS = 100;
    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 1000;
    private static final int DEFAULT_MAX_EVENTS_PER_SECOND = 100;
    private static final int DEFAULT_MAX_COLLECTIONS = 100;
    private static final int DEFAULT_MAX_ENTITIES_PER_COLLECTION = 1000000;

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final ExecutorService blockingExecutor;
    private final DistributedRateLimiter distributedRateLimiter;

    // In-memory quota tracking (fallback when distributed rate limiter unavailable)
    private final Map<String, TenantQuotaConfig> tenantQuotas = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> currentConnections = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestsLastMinute = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> eventsLastSecond = new ConcurrentHashMap<>();
    private final Map<String, TenantUsageStats> tenantUsage = new ConcurrentHashMap<>();

    // Metrics
    private final Timer quotaCheckTime;
    private final io.micrometer.core.instrument.Counter quotaViolations;

    public TenantQuotaManager(DataSource dataSource, MeterRegistry meterRegistry) {
        this(dataSource, meterRegistry, null);
    }

    public TenantQuotaManager(DataSource dataSource, MeterRegistry meterRegistry, 
                             DistributedRateLimiter distributedRateLimiter) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.blockingExecutor = JpaThreadPoolConfig.fromEnvironment().createExecutorService();
        this.distributedRateLimiter = distributedRateLimiter;

        // Initialize metrics
        this.quotaCheckTime = Timer.builder("datacloud.quota.check.time")
            .description("Time spent checking quotas")
            .register(meterRegistry);

        this.quotaViolations = io.micrometer.core.instrument.Counter.builder("datacloud.quota.violations.total")
            .description("Total quota violations")
            .register(meterRegistry);

        // Register tenant usage gauges
        Gauge.builder("datacloud.tenant.storage.used.mb", this, TenantQuotaManager::getTotalStorageUsedMB)
            .description("Storage used by tenant in MB")
            .register(meterRegistry);

        Gauge.builder("datacloud.tenant.connections.active", this, TenantQuotaManager::getTotalActiveConnections)
            .description("Active connections per tenant")
            .register(meterRegistry);

        // Start background cleanup tasks
        startBackgroundTasks();
    }

    // ====================================================================================
    // Quota Configuration Management
    // ====================================================================================

    /**
     * Set quota configuration for a tenant.
     *
     * @param tenantId The tenant ID
     * @param config The quota configuration
     * @return Promise<Void>
     */
    public Promise<Void> setTenantQuota(String tenantId, TenantQuotaConfig config) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO tenant_quotas (tenant_id, max_storage_mb, max_connections, " +
                     "max_requests_per_minute, max_events_per_second, max_collections, " +
                     "max_entities_per_collection, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (tenant_id) DO UPDATE SET " +
                     "max_storage_mb = EXCLUDED.max_storage_mb, " +
                     "max_connections = EXCLUDED.max_connections, " +
                     "max_requests_per_minute = EXCLUDED.max_requests_per_minute, " +
                     "max_events_per_second = EXCLUDED.max_events_per_second, " +
                     "max_collections = EXCLUDED.max_collections, " +
                     "max_entities_per_collection = EXCLUDED.max_entities_per_collection, " +
                     "updated_at = EXCLUDED.updated_at")) {

                stmt.setString(1, tenantId);
                stmt.setLong(2, config.getMaxStorageMB());
                stmt.setInt(3, config.getMaxConcurrentConnections());
                stmt.setInt(4, config.getMaxRequestsPerMinute());
                stmt.setInt(5, config.getMaxEventsPerSecond());
                stmt.setInt(6, config.getMaxCollections());
                stmt.setInt(7, config.getMaxEntitiesPerCollection());
                stmt.setTimestamp(8, java.sql.Timestamp.from(Instant.now()));

                stmt.executeUpdate();

                // Update in-memory cache
                tenantQuotas.put(tenantId, config);

                logger.info("Quota configuration updated for tenant: {}", tenantId);

            } catch (SQLException e) {
                logger.error("Failed to set quota for tenant: {}", tenantId, e);
                throw new RuntimeException("Failed to set tenant quota", e);
            }
        });
    }

    /**
     * Get quota configuration for a tenant.
     *
     * @param tenantId The tenant ID
     * @return Promise<TenantQuotaConfig>
     */
    public Promise<TenantQuotaConfig> getTenantQuota(String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // Check in-memory cache first
            TenantQuotaConfig cached = tenantQuotas.get(tenantId);
            if (cached != null) {
                return cached;
            }

            // Load from database
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM tenant_quotas WHERE tenant_id = ?")) {

                stmt.setString(1, tenantId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    TenantQuotaConfig config = new TenantQuotaConfig(
                        rs.getLong("max_storage_mb"),
                        rs.getInt("max_connections"),
                        rs.getInt("max_requests_per_minute"),
                        rs.getInt("max_events_per_second"),
                        rs.getInt("max_collections"),
                        rs.getInt("max_entities_per_collection")
                    );

                    tenantQuotas.put(tenantId, config);
                    return config;
                } else {
                    // Return default quotas
                    return getDefaultQuota();
                }

            } catch (SQLException e) {
                logger.error("Failed to get quota for tenant: {}", tenantId, e);
                return getDefaultQuota();
            }
        });
    }

    /**
     * Check if operation is within quota limits.
     *
     * @param tenantId The tenant ID
     * @param operationType Type of operation
     * @param resourceAmount Amount of resource being used
     * @return Promise<QuotaCheckResult>
     */
    public Promise<QuotaCheckResult> checkQuota(String tenantId, String operationType, long resourceAmount) {
        Instant start = Instant.now();

        return getTenantQuota(tenantId)
            .then(config -> performQuotaCheck(tenantId, operationType, resourceAmount, config))
            .then(result -> {
                // Record metrics
                quotaCheckTime.record(Duration.between(start, Instant.now()));

                if (!result.isAllowed()) {
                    quotaViolations.increment();
                    logger.warn("Quota violation for tenant {}: {} - {}",
                        tenantId, operationType, result.getReason());
                    alertOnQuotaViolation(tenantId, operationType, result);
                }

                return Promise.of(result);
            });
    }

    private Promise<QuotaCheckResult> performQuotaCheck(String tenantId, String operationType,
                                                long resourceAmount, TenantQuotaConfig config) {

        switch (operationType.toUpperCase()) {
            case "STORAGE":
                return Promise.of(checkStorageQuota(tenantId, resourceAmount, config));

            case "CONNECTION":
                return Promise.of(checkConnectionQuota(tenantId, config));

            case "REQUEST":
                return checkRequestQuota(tenantId, config);

            case "EVENT":
                return checkEventQuota(tenantId, resourceAmount, config);

            case "COLLECTION":
                return Promise.of(checkCollectionQuota(tenantId, config));

            case "ENTITY":
                return Promise.of(checkEntityQuota(tenantId, resourceAmount, config));

            default:
                return Promise.of(new QuotaCheckResult(true, null, 0, 0));
        }
    }

    // ====================================================================================
    // Individual Quota Checks
    // ====================================================================================

    private QuotaCheckResult checkStorageQuota(String tenantId, long additionalBytes, TenantQuotaConfig config) {
        long currentUsageMB = getTenantStorageUsageMB(tenantId);
        long additionalMB = additionalBytes / (1024 * 1024);
        long projectedUsageMB = currentUsageMB + additionalMB;

        if (projectedUsageMB > config.getMaxStorageMB()) {
            return new QuotaCheckResult(
                false,
                "Storage quota exceeded. Current: " + currentUsageMB + "MB, " +
                "Projected: " + projectedUsageMB + "MB, " +
                "Limit: " + config.getMaxStorageMB() + "MB",
                projectedUsageMB,
                config.getMaxStorageMB()
            );
        }

        // Warning at 80% of quota
        if (projectedUsageMB > config.getMaxStorageMB() * 0.8) {
            return new QuotaCheckResult(
                true,
                "WARNING: Approaching storage quota limit",
                projectedUsageMB,
                config.getMaxStorageMB()
            );
        }

        return new QuotaCheckResult(true, null, projectedUsageMB, config.getMaxStorageMB());
    }

    private QuotaCheckResult checkConnectionQuota(String tenantId, TenantQuotaConfig config) {
        AtomicInteger current = currentConnections.computeIfAbsent(tenantId, k -> new AtomicInteger(0));
        int currentCount = current.incrementAndGet();

        if (currentCount > config.getMaxConcurrentConnections()) {
            current.decrementAndGet();
            return new QuotaCheckResult(
                false,
                "Connection quota exceeded. Current: " + (currentCount - 1) + ", Limit: " + config.getMaxConcurrentConnections(),
                currentCount - 1,
                config.getMaxConcurrentConnections()
            );
        }

        return new QuotaCheckResult(true, null, currentCount, config.getMaxConcurrentConnections());
    }

    private Promise<QuotaCheckResult> checkRequestQuota(String tenantId, TenantQuotaConfig config) {
        if (distributedRateLimiter != null) {
            return distributedRateLimiter.checkRequestRateLimit(tenantId, config.getMaxRequestsPerMinute())
                .then(result -> Promise.of(new QuotaCheckResult(
                    result.isAllowed(),
                    result.getReason(),
                    result.getCurrentUsage(),
                    result.getLimit()
                )));
        }

        // Fallback to in-memory rate limiting
        AtomicLong current = requestsLastMinute.computeIfAbsent(tenantId, k -> new AtomicLong(0));
        long currentCount = current.incrementAndGet();

        if (currentCount > config.getMaxRequestsPerMinute()) {
            return Promise.of(new QuotaCheckResult(
                false,
                "Rate limit exceeded. Requests last minute: " + currentCount +
                ", Limit: " + config.getMaxRequestsPerMinute() + "/min",
                currentCount,
                config.getMaxRequestsPerMinute()
            ));
        }

        return Promise.of(new QuotaCheckResult(true, null, currentCount, config.getMaxRequestsPerMinute()));
    }

    private Promise<QuotaCheckResult> checkEventQuota(String tenantId, long eventCount, TenantQuotaConfig config) {
        if (distributedRateLimiter != null) {
            return distributedRateLimiter.checkEventRateLimit(tenantId, eventCount, config.getMaxEventsPerSecond())
                .then(result -> Promise.of(new QuotaCheckResult(
                    result.isAllowed(),
                    result.getReason(),
                    result.getCurrentUsage(),
                    result.getLimit()
                )));
        }

        // Fallback to in-memory rate limiting
        AtomicLong current = eventsLastSecond.computeIfAbsent(tenantId, k -> new AtomicLong(0));
        long currentCount = current.addAndGet(eventCount);

        if (currentCount > config.getMaxEventsPerSecond()) {
            return Promise.of(new QuotaCheckResult(
                false,
                "Event quota exceeded. Events this second: " + currentCount +
                ", Limit: " + config.getMaxEventsPerSecond() + "/sec",
                currentCount,
                config.getMaxEventsPerSecond()
            ));
        }

        return Promise.of(new QuotaCheckResult(true, null, currentCount, config.getMaxEventsPerSecond()));
    }

    private QuotaCheckResult checkCollectionQuota(String tenantId, TenantQuotaConfig config) {
        int currentCollections = getTenantCollectionCount(tenantId);

        if (currentCollections >= config.getMaxCollections()) {
            return new QuotaCheckResult(
                false,
                "Collection quota exceeded. Current: " + currentCollections +
                ", Limit: " + config.getMaxCollections(),
                currentCollections,
                config.getMaxCollections()
            );
        }

        return new QuotaCheckResult(true, null, currentCollections, config.getMaxCollections());
    }

    private QuotaCheckResult checkEntityQuota(String tenantId, long additionalEntities, TenantQuotaConfig config) {
        // This would need to be implemented based on the specific collection
        // For now, return allowed with warning
        return new QuotaCheckResult(true, null, additionalEntities, config.getMaxEntitiesPerCollection());
    }

    // ====================================================================================
    // Usage Tracking
    // ====================================================================================

    /**
     * Release a connection for a tenant.
     *
     * @param tenantId The tenant ID
     */
    public void releaseConnection(String tenantId) {
        AtomicInteger current = currentConnections.get(tenantId);
        if (current != null) {
            current.decrementAndGet();
        }
    }

    /**
     * Get current usage statistics for a tenant.
     *
     * @param tenantId The tenant ID
     * @return TenantUsageStats
     */
    public TenantUsageStats getTenantUsage(String tenantId) {
        return tenantUsage.computeIfAbsent(tenantId, k -> {
            TenantUsageStats stats = new TenantUsageStats();
            stats.setStorageMB(getTenantStorageUsageMB(tenantId));
            stats.setActiveConnections(currentConnections.getOrDefault(tenantId, new AtomicInteger(0)).get());
            stats.setRequestsLastMinute(requestsLastMinute.getOrDefault(tenantId, new AtomicLong(0)).get());
            stats.setCollectionCount(getTenantCollectionCount(tenantId));
            return stats;
        });
    }

    // ====================================================================================
    // Database Queries
    // ====================================================================================

    private long getTenantStorageUsageMB(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COALESCE(SUM(pg_column_size(data)), 0) / (1024 * 1024) as size_mb " +
                 "FROM entities WHERE tenant_id = ?")) {

            stmt.setString(1, tenantId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getLong("size_mb");
            }
        } catch (SQLException e) {
            logger.error("Failed to get storage usage for tenant: {}", tenantId, e);
        }
        return 0;
    }

    private int getTenantCollectionCount(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(DISTINCT collection_name) as count FROM entities WHERE tenant_id = ?")) {

            stmt.setString(1, tenantId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            logger.error("Failed to get collection count for tenant: {}", tenantId, e);
        }
        return 0;
    }

    // ====================================================================================
    // Background Tasks
    // ====================================================================================

    private void startBackgroundTasks() {
        // Reset request counters every minute
        Thread requestResetThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // 1 minute
                    requestsLastMinute.clear();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        requestResetThread.setDaemon(true);
        requestResetThread.start();

        // Reset event counters every second
        Thread eventResetThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000); // 1 second
                    eventsLastSecond.clear();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        eventResetThread.setDaemon(true);
        eventResetThread.start();
    }

    // ====================================================================================
    // Alerting
    // ====================================================================================

    private void alertOnQuotaViolation(String tenantId, String operationType, QuotaCheckResult result) {
        String message = String.format(
            "Quota Violation: Tenant=%s, Operation=%s, Reason=%s",
            tenantId, operationType, result.getReason()
        );

        logger.error(message);

        // In production: send to alerting system
        // sendAlert("QUOTA_VIOLATION", message, "HIGH", tenantId);
    }

    // ====================================================================================
    // Metrics for Micrometer
    // ====================================================================================

    private double getTotalStorageUsedMB() {
        return tenantUsage.values().stream()
            .mapToLong(TenantUsageStats::getStorageMB)
            .sum();
    }

    private double getTotalActiveConnections() {
        return currentConnections.values().stream()
            .mapToInt(AtomicInteger::get)
            .sum();
    }

    private TenantQuotaConfig getDefaultQuota() {
        return new TenantQuotaConfig(
            DEFAULT_MAX_STORAGE_MB,
            DEFAULT_MAX_CONCURRENT_CONNECTIONS,
            DEFAULT_MAX_REQUESTS_PER_MINUTE,
            DEFAULT_MAX_EVENTS_PER_SECOND,
            DEFAULT_MAX_COLLECTIONS,
            DEFAULT_MAX_ENTITIES_PER_COLLECTION
        );
    }

    // ====================================================================================
    // Inner Classes
    // ====================================================================================

    public static class TenantQuotaConfig {
        private final long maxStorageMB;
        private final int maxConcurrentConnections;
        private final int maxRequestsPerMinute;
        private final int maxEventsPerSecond;
        private final int maxCollections;
        private final int maxEntitiesPerCollection;

        public TenantQuotaConfig(long maxStorageMB, int maxConcurrentConnections,
                                int maxRequestsPerMinute, int maxEventsPerSecond,
                                int maxCollections, int maxEntitiesPerCollection) {
            this.maxStorageMB = maxStorageMB;
            this.maxConcurrentConnections = maxConcurrentConnections;
            this.maxRequestsPerMinute = maxRequestsPerMinute;
            this.maxEventsPerSecond = maxEventsPerSecond;
            this.maxCollections = maxCollections;
            this.maxEntitiesPerCollection = maxEntitiesPerCollection;
        }

        public long getMaxStorageMB() { return maxStorageMB; }
        public int getMaxConcurrentConnections() { return maxConcurrentConnections; }
        public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
        public int getMaxEventsPerSecond() { return maxEventsPerSecond; }
        public int getMaxCollections() { return maxCollections; }
        public int getMaxEntitiesPerCollection() { return maxEntitiesPerCollection; }
    }

    public static class QuotaCheckResult {
        private final boolean allowed;
        private final String reason;
        private final long currentUsage;
        private final long limit;

        public QuotaCheckResult(boolean allowed, String reason, long currentUsage, long limit) {
            this.allowed = allowed;
            this.reason = reason;
            this.currentUsage = currentUsage;
            this.limit = limit;
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public long getCurrentUsage() { return currentUsage; }
        public long getLimit() { return limit; }

        public double getUsagePercentage() {
            return limit > 0 ? (double) currentUsage / limit * 100 : 0;
        }
    }

    public static class TenantUsageStats {
        private long storageMB;
        private int activeConnections;
        private long requestsLastMinute;
        private int collectionCount;
        private Instant lastUpdated = Instant.now();

        public long getStorageMB() { return storageMB; }
        public void setStorageMB(long storageMB) { this.storageMB = storageMB; }

        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }

        public long getRequestsLastMinute() { return requestsLastMinute; }
        public void setRequestsLastMinute(long requestsLastMinute) { this.requestsLastMinute = requestsLastMinute; }

        public int getCollectionCount() { return collectionCount; }
        public void setCollectionCount(int collectionCount) { this.collectionCount = collectionCount; }

        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }
}
