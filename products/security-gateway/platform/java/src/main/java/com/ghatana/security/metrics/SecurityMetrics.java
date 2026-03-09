package com.ghatana.security.metrics;

import com.ghatana.security.alert.SecurityAlert;
import com.ghatana.security.config.MetricsProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Service for tracking security-related metrics.
 
 *
 * @doc.type class
 * @doc.purpose Security metrics
 * @doc.layer core
 * @doc.pattern Metrics
*/
public class SecurityMetrics {
    private static final Logger logger = LoggerFactory.getLogger(SecurityMetrics.class);
    
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    
    // Metric names
    private static final String RATE_LIMIT_EXCEEDED = "security.rate_limit.exceeded";
    private static final String AUTH_ATTEMPTS = "security.auth.attempts";
    private static final String AUTH_SUCCESS = "security.auth.success";
    private static final String AUTH_FAILURE = "security.auth.failure";
    private static final String AUTH_LATENCY = "security.auth.latency";
    private static final String PERMISSION_CHECKS = "security.rbac.checks";
    private static final String SECURITY_ALERTS = "security.alerts";
    private static final String SECURITY_ALERT_SEVERITY = "security.alert.severity";
    
    public SecurityMetrics(MeterRegistry meterRegistry, MetricsProperties metricsProperties) {
        this.meterRegistry = meterRegistry;
        initializeCounters();
        
        // Apply any metrics configuration from properties
        if (metricsProperties != null) {
            metricsProperties.getTags().forEach((k, v) -> 
                meterRegistry.config().commonTags(k, v)
            );
        }
    }
    
    private void initializeCounters() {
        // Initialize rate limit metrics
        getOrCreateCounter(RATE_LIMIT_EXCEEDED, "Global rate limit exceeded", "scope", "global");
        getOrCreateCounter(RATE_LIMIT_EXCEEDED, "IP rate limit exceeded", "scope", "ip");
        getOrCreateCounter(RATE_LIMIT_EXCEEDED, "User rate limit exceeded", "scope", "user");
        
        // Initialize auth metrics
        getOrCreateCounter(AUTH_ATTEMPTS, "Authentication attempts", "type", "total");
        getOrCreateCounter(AUTH_SUCCESS, "Successful authentications", "type", "success");
        getOrCreateCounter(AUTH_FAILURE, "Failed authentications", "type", "failure");
        
        // Initialize RBAC metrics
        getOrCreateCounter(PERMISSION_CHECKS, "Permission checks", "result", "total");
        getOrCreateCounter(PERMISSION_CHECKS, "Permission checks granted", "result", "granted");
        getOrCreateCounter(PERMISSION_CHECKS, "Permission checks denied", "result", "denied");
    }
    
    /**
     * Record a rate limit exceeded event.
     */
    public void recordRateLimitExceeded(String scope, String key) {
        getOrCreateCounter(RATE_LIMIT_EXCEEDED, "Rate limit exceeded: " + scope, "scope", scope)
            .increment();
        
        logger.warn("Rate limit exceeded - scope: {}, key: {}", scope, key);
    }
    
    /**
     * Record an authentication attempt.
     */
    public void recordAuthAttempt(String type) {
        getOrCreateCounter(AUTH_ATTEMPTS, "Authentication attempt: " + type, "type", type)
            .increment();
    }
    
    /**
     * Record a successful authentication.
     */
    public void recordAuthSuccess(String method) {
        getOrCreateCounter(AUTH_SUCCESS, "Authentication success: " + method, "method", method)
            .increment();
    }
    
    /**
     * Record a failed authentication attempt.
     */
    public void recordAuthFailure(String method, String reason) {
        getOrCreateCounter(AUTH_FAILURE, "Authentication failure: " + method, "method", method, "reason", reason)
            .increment();
    }
    
    /**
     * Record a permission check.
     */
    public void recordPermissionCheck(String permission, boolean granted) {
        getOrCreateCounter(PERMISSION_CHECKS, "Permission check", "permission", permission, "granted", String.valueOf(granted))
            .increment();
    }
    
    /**
     * Record a security alert.
     */
    public void recordAlert(SecurityAlert alert) {
        getOrCreateCounter(SECURITY_ALERTS, "Security alert: " + alert.getType(), 
                "type", alert.getType(), 
                "severity", alert.getSeverity().name())
            .increment();
        
        logger.info("Security alert recorded - type: {}, severity: {}", 
                alert.getType(), alert.getSeverity());
    }
    
    /**
     * Record a dropped alert due to rate limiting.
     *
     * @param alert The alert that was dropped
     */
    public void recordDroppedAlert(SecurityAlert alert) {
        getOrCreateCounter(SECURITY_ALERTS, "Dropped security alert", 
                "type", alert.getType(), 
                "severity", alert.getSeverity().name(),
                "status", "dropped")
            .increment();
        
        logger.warn("Alert dropped due to rate limiting - type: {}, severity: {}", 
                alert.getType(), alert.getSeverity());
    }
    
    /**
     * Record an error in an alert handler.
     */
    public void recordAlertHandlerError(String handlerName) {
        getOrCreateCounter("security.alert.handler.errors", 
                "Error in alert handler: " + handlerName, 
                "handler", handlerName)
            .increment();
        
        logger.error("Error in alert handler: {}", handlerName);
    }
    
    /**
     * Record a security alert with severity.
     */
    public void recordSecurityAlert(SecurityAlert alert) {
        // Count alerts by type and severity
        getOrCreateCounter(SECURITY_ALERTS, "Security alerts by type", 
                "type", alert.getType(), 
                "severity", alert.getSeverity().name())
            .increment();
            
        // Track alert severity distribution
        getOrCreateCounter(SECURITY_ALERT_SEVERITY, "Security alert severity distribution",
                "severity", alert.getSeverity().name())
            .increment();
            
        // Record any additional alert details as tags
        if (alert.getDetails() != null && !alert.getDetails().isEmpty()) {
            alert.getDetails().forEach((key, value) -> {
                getOrCreateCounter(SECURITY_ALERTS + ".details", 
                        "Security alert details",
                        "type", alert.getType(),
                        "detail_key", key,
                        "detail_value", String.valueOf(value))
                    .increment();
            });
        }
    }
    
    /**
     * Time an authentication operation.
     */
    public <T> T timeAuthOperation(String operation, Supplier<T> supplier) {
        try {
            return getOrCreateTimer(AUTH_LATENCY, "Authentication latency: " + operation, "operation", operation)
                .recordCallable(supplier::get);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Get or create a counter with the given name and tags.
     * 
     * @param name The metric name
     * @param description Human-readable description of the metric
     * @param tags Pairs of tag names and values (must be even number of arguments)
     * @return The counter metric
     * @throws IllegalArgumentException if tags has an odd number of elements
     */
    private Counter getOrCreateCounter(String name, String description, String... tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be provided as key-value pairs");
        }
        
        String cacheKey = name + String.join("", tags);
        Counter existingCounter = counters.get(cacheKey);
        if (existingCounter != null) {
            return existingCounter;
        }
        
        // Create new counter with tags
        Counter.Builder builder = Counter.builder(name)
                .description(description);
                
        for (int i = 0; i < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        
        Counter newCounter = builder.register(meterRegistry);
        Counter previousCounter = counters.putIfAbsent(cacheKey, newCounter);
        
        // If another thread added a counter while we were creating one, use that one instead
        return previousCounter != null ? previousCounter : newCounter;
    }
    
    /**
     * Get or create a timer with the given name and tags.
     */
    private Timer getOrCreateTimer(String name, String description, String... tagKeyValues) {
        if (tagKeyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be key-value pairs");
        }
        
        String cacheKey = name + "_" + String.join("_", tagKeyValues);
        return timers.computeIfAbsent(cacheKey, k -> {
            Timer.Builder builder = Timer.builder(name)
                .description(description)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram();
                
            for (int i = 0; i < tagKeyValues.length; i += 2) {
                builder = builder.tag(tagKeyValues[i], tagKeyValues[i + 1]);
            }
            
            return builder.register(meterRegistry);
        });
    }
}
