package com.ghatana.aep.platform.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Health check service for monitoring system health.
 * 
 * <p>Provides comprehensive health monitoring with:
 * <ul>
 *   <li>Component-level health checks</li>
 *   <li>Dependency health tracking</li>
 *   <li>Aggregated health status</li>
 *   <li>Health history and metrics</li>
 *   <li>Custom health indicators</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose System health monitoring
 * @doc.layer platform
 */
public class HealthCheckService {
    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    
    private final Map<String, HealthIndicator> indicators = new ConcurrentHashMap<>();
    private final Map<String, HealthCheckResult> lastResults = new ConcurrentHashMap<>();
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong failedChecks = new AtomicLong(0);
    
    /**
     * Creates a new health check service.
     */
    public HealthCheckService() {
        log.info("HealthCheckService initialized");
    }
    
    /**
     * Registers a health indicator.
     * 
     * @param name indicator name
     * @param indicator health indicator implementation
     */
    public void registerIndicator(String name, HealthIndicator indicator) {
        Objects.requireNonNull(name, "Indicator name cannot be null");
        Objects.requireNonNull(indicator, "Indicator cannot be null");
        
        indicators.put(name, indicator);
        log.info("Registered health indicator: {}", name);
    }
    
    /**
     * Unregisters a health indicator.
     * 
     * @param name indicator name
     */
    public void unregisterIndicator(String name) {
        indicators.remove(name);
        lastResults.remove(name);
        log.info("Unregistered health indicator: {}", name);
    }
    
    /**
     * Performs health check on all registered indicators.
     * 
     * @return aggregated health status
     */
    public HealthStatus checkHealth() {
        log.debug("Performing health check on {} indicators", indicators.size());
        
        Map<String, HealthCheckResult> results = new HashMap<>();
        HealthState overallState = HealthState.UP;
        
        for (Map.Entry<String, HealthIndicator> entry : indicators.entrySet()) {
            String name = entry.getKey();
            HealthIndicator indicator = entry.getValue();
            
            try {
                totalChecks.incrementAndGet();
                HealthCheckResult result = indicator.check();
                results.put(name, result);
                lastResults.put(name, result);
                
                if (result.getState() == HealthState.DOWN) {
                    overallState = HealthState.DOWN;
                    failedChecks.incrementAndGet();
                } else if (result.getState() == HealthState.DEGRADED && overallState != HealthState.DOWN) {
                    overallState = HealthState.DEGRADED;
                }
                
                log.debug("Health check result for {}: {}", name, result.getState());
                
            } catch (Exception e) {
                log.error("Health check failed for {}: {}", name, e.getMessage());
                failedChecks.incrementAndGet();
                
                HealthCheckResult errorResult = new HealthCheckResult(
                    name,
                    HealthState.DOWN,
                    "Health check error: " + e.getMessage(),
                    Map.of("error", e.getClass().getSimpleName()),
                    Instant.now()
                );
                results.put(name, errorResult);
                lastResults.put(name, errorResult);
                overallState = HealthState.DOWN;
            }
        }
        
        HealthStatus status = new HealthStatus(
            overallState,
            results,
            Instant.now()
        );
        
        log.info("Health check complete: {} (checked {} indicators)", overallState, results.size());
        return status;
    }
    
    /**
     * Performs health check on a specific indicator.
     * 
     * @param name indicator name
     * @return health check result
     */
    public HealthCheckResult checkIndicator(String name) {
        HealthIndicator indicator = indicators.get(name);
        if (indicator == null) {
            throw new IllegalArgumentException("Indicator not found: " + name);
        }
        
        try {
            totalChecks.incrementAndGet();
            HealthCheckResult result = indicator.check();
            lastResults.put(name, result);
            
            if (result.getState() != HealthState.UP) {
                failedChecks.incrementAndGet();
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Health check failed for {}: {}", name, e.getMessage());
            failedChecks.incrementAndGet();
            
            return new HealthCheckResult(
                name,
                HealthState.DOWN,
                "Health check error: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName()),
                Instant.now()
            );
        }
    }
    
    /**
     * Gets the last health check result for an indicator.
     * 
     * @param name indicator name
     * @return last health check result or null if not available
     */
    public HealthCheckResult getLastResult(String name) {
        return lastResults.get(name);
    }
    
    /**
     * Gets all registered indicator names.
     * 
     * @return set of indicator names
     */
    public Set<String> getIndicatorNames() {
        return new HashSet<>(indicators.keySet());
    }
    
    /**
     * Gets health check statistics.
     * 
     * @return health check statistics
     */
    public HealthStats getStats() {
        long total = totalChecks.get();
        long failed = failedChecks.get();
        double failureRate = total > 0 ? (double) failed / total : 0.0;
        
        return new HealthStats(
            indicators.size(),
            total,
            failed,
            failureRate
        );
    }
    
    /**
     * Resets health check statistics.
     */
    public void resetStats() {
        totalChecks.set(0);
        failedChecks.set(0);
        log.info("Health check statistics reset");
    }
    
    /**
     * Health indicator interface.
     */
    @FunctionalInterface
    public interface HealthIndicator {
        HealthCheckResult check();
    }
    
    /**
     * Health state enumeration.
     */
    public enum HealthState {
        UP,       // System is healthy
        DEGRADED, // System is running but with issues
        DOWN      // System is not functioning
    }
    
    /**
     * Health check result.
     */
    public static class HealthCheckResult {
        private final String name;
        private final HealthState state;
        private final String message;
        private final Map<String, Object> details;
        private final Instant timestamp;
        
        public HealthCheckResult(String name, HealthState state, String message, 
                                Map<String, Object> details, Instant timestamp) {
            this.name = name;
            this.state = state;
            this.message = message;
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
            this.timestamp = timestamp;
        }
        
        public String getName() { return name; }
        public HealthState getState() { return state; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return Collections.unmodifiableMap(details); }
        public Instant getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("HealthCheckResult[%s: %s - %s]", name, state, message);
        }
    }
    
    /**
     * Aggregated health status.
     */
    public static class HealthStatus {
        private final HealthState state;
        private final Map<String, HealthCheckResult> indicators;
        private final Instant timestamp;
        
        public HealthStatus(HealthState state, Map<String, HealthCheckResult> indicators, Instant timestamp) {
            this.state = state;
            this.indicators = new HashMap<>(indicators);
            this.timestamp = timestamp;
        }
        
        public HealthState getState() { return state; }
        public Map<String, HealthCheckResult> getIndicators() { return Collections.unmodifiableMap(indicators); }
        public Instant getTimestamp() { return timestamp; }
        
        public boolean isHealthy() {
            return state == HealthState.UP;
        }
        
        @Override
        public String toString() {
            return String.format("HealthStatus[%s: %d indicators at %s]", 
                state, indicators.size(), timestamp);
        }
    }
    
    /**
     * Health check statistics.
     */
    public static class HealthStats {
        private final int indicatorCount;
        private final long totalChecks;
        private final long failedChecks;
        private final double failureRate;
        
        public HealthStats(int indicatorCount, long totalChecks, long failedChecks, double failureRate) {
            this.indicatorCount = indicatorCount;
            this.totalChecks = totalChecks;
            this.failedChecks = failedChecks;
            this.failureRate = failureRate;
        }
        
        public int getIndicatorCount() { return indicatorCount; }
        public long getTotalChecks() { return totalChecks; }
        public long getFailedChecks() { return failedChecks; }
        public double getFailureRate() { return failureRate; }
        
        @Override
        public String toString() {
            return String.format("HealthStats[indicators=%d, total=%d, failed=%d, failureRate=%.2f%%]",
                indicatorCount, totalChecks, failedChecks, failureRate * 100);
        }
    }
}
