package com.ghatana.platform.observability.health;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import com.ghatana.platform.observability.MetricsRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton registry for managing health checks in the EventCloud platform.
 * Aggregates individual health checks into overall liveness and readiness status.
 *
 * <p>Provides centralized health check management with support for:
 * <ul>
 *   <li><strong>Liveness</strong>: Critical component checks (JVM, core services)</li>
 *   <li><strong>Readiness</strong>: All component checks (includes external dependencies)</li>
 *   <li><strong>Parallel Execution</strong>: All checks run concurrently via ActiveJ Promises</li>
 *   <li><strong>Metrics Integration</strong>: Records check duration and status</li>
 * </ul>
 *
 * <h2>Liveness vs Readiness:</h2>
 * <pre>
 * Liveness (isCritical=true):
 *   - JVM health (memory, threads)
 *   - Internal observability (metrics, tracing)
 *   - Core services required for basic operation
 *   -> Failure triggers pod restart (Kubernetes liveness probe)
 *
 * Readiness (all checks):
 *   - Database connectivity
 *   - Cache availability
 *   - Message queue connectivity
 *   - External API dependencies
 *   -> Failure removes pod from load balancer (Kubernetes readiness probe)
 * </pre>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Singleton access
 * HealthCheckRegistry registry = HealthCheckRegistry.getInstance();
 *
 * // Register health checks
 * registry.register(new DatabaseHealthCheck(dataSource, "primary-db"));
 * registry.register(new RedisHealthCheck(jedisPool, "redis-cache"));
 * registry.register(observabilityCheck);
 *
 * // Liveness check (critical components only)
 * registry.liveness()
 *     .whenResult(result -> {
 *         if (!result.isHealthy()) {
 *             logger.error("Liveness check failed: {}", result.getMessage());
 *             // Kubernetes will restart pod
 *         }
 *     });
 *
 * // Readiness check (all components)
 * registry.readiness()
 *     .whenResult(result -> {
 *         if (!result.isHealthy()) {
 *             logger.warn("Readiness check failed: {}", result.getMessage());
 *             // Kubernetes will stop sending traffic
 *         }
 *     });
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Centralized health check registry for liveness/readiness aggregation and parallel execution
 * @doc.layer observability
 * @doc.pattern Registry, Singleton, Aggregator
 *
 * <h2>Thread Safety:</h2>
 * Registry is thread-safe (ConcurrentHashMap + AtomicReference for singleton).
 *
 * @since 1.0.0
 */
public class HealthCheckRegistry {
    
    private static final AtomicReference<HealthCheckRegistry> INSTANCE = new AtomicReference<>();
    
    private final Map<String, HealthCheck> healthChecks = new ConcurrentHashMap<>();
    private final Map<String, HealthCheck.HealthCheckResult> lastResults = new ConcurrentHashMap<>();
    private final MetricsRegistry metricsRegistry;
    
    public HealthCheckRegistry(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }
    
    public static synchronized HealthCheckRegistry initialize(MetricsRegistry metricsRegistry) {
        HealthCheckRegistry registry = new HealthCheckRegistry(metricsRegistry);
        INSTANCE.set(registry);
        return registry;
    }
    
    public static HealthCheckRegistry getInstance() {
        HealthCheckRegistry registry = INSTANCE.get();
        if (registry == null) {
            throw new IllegalStateException("HealthCheckRegistry not initialized. Call initialize() first.");
        }
        return registry;
    }
    
    /**
     * Register a health check.
     */
    public void register(HealthCheck healthCheck) {
        healthChecks.put(healthCheck.getName(), healthCheck);
    }
    
    /**
     * Unregister a health check.
     */
    public void unregister(String name) {
        healthChecks.remove(name);
        lastResults.remove(name);
    }
    
    /**
     * Get all registered health checks.
     */
    public Set<String> getHealthCheckNames() {
        return new HashSet<>(healthChecks.keySet());
    }
    
    /**
     * Perform liveness check - basic service functionality.
     */
    public Promise<OverallHealthResult> liveness() {
        // Liveness should only check critical internal components
        List<Promise<NamedHealthResult>> checks = healthChecks.values().stream()
            .filter(HealthCheck::isCritical)
            .filter(this::isInternalCheck)
            .map(this::executeHealthCheck)
            .toList();
        
        return Promises.toList(checks)
            .map(results -> aggregateResults("liveness", results));
    }
    
    /**
     * Perform readiness check - service ready to handle requests.
     */
    public Promise<OverallHealthResult> readiness() {
        // Readiness checks all dependencies
        List<Promise<NamedHealthResult>> checks = healthChecks.values().stream()
            .map(this::executeHealthCheck)
            .toList();
        
        return Promises.toList(checks)
            .map(results -> aggregateResults("readiness", results));
    }
    
    /**
     * Get detailed health status of all checks.
     */
    public Promise<OverallHealthResult> health() {
        List<Promise<NamedHealthResult>> checks = healthChecks.values().stream()
            .map(this::executeHealthCheck)
            .toList();
        
        return Promises.toList(checks)
            .map(results -> aggregateResults("health", results));
    }

    /**
     * Backwards-compatible adapter used by callers expecting a map of individual check results.
     */
    public Promise<java.util.Map<String, HealthCheck.HealthCheckResult>> runAllChecks() {
        return health().map(OverallHealthResult::getChecks);
    }

    /** Adapter for liveness checks. */
    public Promise<java.util.Map<String, HealthCheck.HealthCheckResult>> runLivenessChecks() {
        return liveness().map(OverallHealthResult::getChecks);
    }

    /** Adapter for readiness checks. */
    public Promise<java.util.Map<String, HealthCheck.HealthCheckResult>> runReadinessChecks() {
        return readiness().map(OverallHealthResult::getChecks);
    }

    /**
     * Get the last known result for a specific health check.
     */
    public Optional<HealthCheck.HealthCheckResult> getLastResult(String name) {
        return Optional.ofNullable(lastResults.get(name));
    }
    
    private Promise<NamedHealthResult> executeHealthCheck(HealthCheck healthCheck) {
        Instant start = Instant.now();
        return Promise.ofCallback(cb -> {
            healthCheck.check()
                .whenResult(result -> {
                    lastResults.put(healthCheck.getName(), result);
                    recordHealthCheckMetrics(healthCheck.getName(), result);
                    cb.accept(new NamedHealthResult(healthCheck.getName(), result), null);
                })
                .whenException(throwable -> {
                    Duration duration = Duration.between(start, Instant.now());
                    HealthCheck.HealthCheckResult errorResult = HealthCheck.HealthCheckResult.unhealthy(
                        "Health check failed: " + (throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName()), throwable);
                    HealthCheck.HealthCheckResult enriched = new HealthCheck.HealthCheckResult(
                        errorResult.getStatus(),
                        errorResult.getMessage(),
                        errorResult.getDetails(),
                        duration,
                        errorResult.getError());
                    lastResults.put(healthCheck.getName(), enriched);
                    recordHealthCheckMetrics(healthCheck.getName(), enriched);
                    cb.accept(new NamedHealthResult(healthCheck.getName(), enriched), null);
                });
        });
    }
    
    private OverallHealthResult aggregateResults(String checkType, List<NamedHealthResult> results) {
        Map<String, HealthCheck.HealthCheckResult> resultMap = new HashMap<>();
        HealthCheck.Status overallStatus = HealthCheck.Status.UP;
        List<String> issues = new ArrayList<>();
        
        for (NamedHealthResult namedResult : results) {
            resultMap.put(namedResult.name, namedResult.result);
            
            if (namedResult.result.isUnhealthy()) {
                overallStatus = HealthCheck.Status.DOWN;
                issues.add(namedResult.name + ": " + namedResult.result.getMessage());
            } else if (namedResult.result.isDegraded() && overallStatus == HealthCheck.Status.UP) {
                overallStatus = HealthCheck.Status.DEGRADED;
                issues.add(namedResult.name + ": " + namedResult.result.getMessage());
            }
        }
        
        String message = overallStatus == HealthCheck.Status.UP ? 
            "All health checks passed" : 
            "Health check issues: " + String.join(", ", issues);
        
        return new OverallHealthResult(overallStatus, message, resultMap, checkType);
    }
    
    private void recordHealthCheckMetrics(String checkName, HealthCheck.HealthCheckResult result) {
        if (metricsRegistry != null) {
            // Record health check execution
            metricsRegistry.customCounter(
                    "health.checks.total",
                    "Health check executions",
                    io.micrometer.core.instrument.Tag.of("check_name", checkName),
                    io.micrometer.core.instrument.Tag.of("status", result.getStatus().getValue()))
                .increment();

            // Record duration
            metricsRegistry.customTimer(
                    "health.checks.duration",
                    "Health check duration",
                    io.micrometer.core.instrument.Tag.of("check_name", checkName))
                .record(result.getDuration());
        }
    }
    
    private boolean isInternalCheck(HealthCheck healthCheck) {
        // Determine if this is an internal check (not external dependency)
        String name = healthCheck.getName().toLowerCase();
        return !name.contains("database") && 
               !name.contains("redis") && 
               !name.contains("kafka") && 
               !name.contains("external");
    }
    
    /**
     * Named health check result for internal tracking.
     */
    private static class NamedHealthResult {
        final String name;
        final HealthCheck.HealthCheckResult result;
        
        NamedHealthResult(String name, HealthCheck.HealthCheckResult result) {
            this.name = name;
            this.result = result;
        }
    }
    
    /**
     * Overall health result aggregating multiple health checks.
     */
    public static class OverallHealthResult {
        private final HealthCheck.Status status;
        private final String message;
        private final Map<String, HealthCheck.HealthCheckResult> checks;
        private final String checkType;
        private final Instant timestamp;
        
        public OverallHealthResult(HealthCheck.Status status, String message, 
                                 Map<String, HealthCheck.HealthCheckResult> checks, String checkType) {
            this.status = status;
            this.message = message;
            this.checks = Map.copyOf(checks);
            this.checkType = checkType;
            this.timestamp = Instant.now();
        }
        
        public HealthCheck.Status getStatus() { return status; }
        public String getMessage() { return message; }
        public Map<String, HealthCheck.HealthCheckResult> getChecks() { return checks; }
        public String getCheckType() { return checkType; }
        public Instant getTimestamp() { return timestamp; }
        
        public boolean isHealthy() { return status == HealthCheck.Status.UP; }
        public boolean isUnhealthy() { return status == HealthCheck.Status.DOWN; }
        public boolean isDegraded() { return status == HealthCheck.Status.DEGRADED; }
    }
}
