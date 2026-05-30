package com.ghatana.datacloud.security;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced security audit service for Data Cloud with comprehensive security event tracking.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized security audit logging, event correlation, and security metrics
 * collection for compliance and monitoring. Tracks all security-relevant events
 * including authentication, authorization, policy decisions, and potential threats.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SecurityAuditService auditService = SecurityAuditService.builder()
 *     .auditService(auditService)
 *     .build();
 * 
 * auditService.logSecurityEvent(
 *     principal, "GET", "/api/v1/entities", 
 *     SecurityEventType.ACCESS_GRANTED, "Access allowed"
 * );
 * }</pre>
 *
 * @see SecurityPolicyService
 * @doc.type class
 * @doc.purpose Enhanced security audit logging and metrics
 * @doc.layer product
 * @doc.pattern Service, Observer
 */
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final AuditService auditService;
    private final boolean enabled;
    private final Map<String, SecurityMetrics> metrics = new ConcurrentHashMap<>();
    private final AtomicLong totalEvents = new AtomicLong(0);

    private SecurityAuditService(Builder builder) {
        this.auditService = builder.auditService;
        this.enabled = builder.enabled;
    }

    /**
     * Logs a security event.
     *
     * @param principal the authenticated principal
     * @param method HTTP method
     * @param path request path
     * @param eventType security event type
     * @param reason event description
     */
    public void logSecurityEvent(
            Principal principal,
            String method,
            String path,
            SecurityEventType eventType,
            String reason) {
        
        logSecurityEvent(principal, method, path, eventType, reason, Map.of());
    }

    /**
     * Logs a security event with additional metadata.
     *
     * @param principal the authenticated principal
     * @param method HTTP method
     * @param path request path
     * @param eventType security event type
     * @param reason event description
     * @param metadata additional event metadata
     */
    public void logSecurityEvent(
            Principal principal,
            String method,
            String path,
            SecurityEventType eventType,
            String reason,
            Map<String, Object> metadata) {
        
        if (!enabled) {
            return;
        }

        try {
            totalEvents.incrementAndGet();
            
            // Update metrics
            updateMetrics(eventType, principal);

            // Create audit event
            AuditEvent auditEvent = createAuditEvent(principal, method, path, eventType, reason, metadata);
            
            // Send to audit service asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    auditService.record(auditEvent);
                } catch (Exception e) {
                    log.error("Failed to log security audit event: {}", e.getMessage(), e);
                }
            });

            // Log high-priority events synchronously
            if (eventType.isHighPriority()) {
                log.warn("High-priority security event: {} - {} {} - {}", 
                        eventType, method, path, reason);
            }

        } catch (Exception e) {
            log.error("Failed to create security audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Logs a security policy evaluation result.
     *
     * @param principal the authenticated principal
     * @param method HTTP method
     * @param path request path
     * @param result policy evaluation result
     */
    public void logPolicyEvaluation(
            Principal principal,
            String method,
            String path,
            SecurityPolicyService.SecurityEvaluationResult result) {
        
        SecurityEventType eventType = result.isAllowed() ? 
            SecurityEventType.POLICY_ALLOWED : SecurityEventType.POLICY_DENIED;
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sensitivity", result.getSensitivity().name());
        metadata.put("evaluationTime", result.getEvaluationTime().toMillis());
        metadata.put("breakGlassUsed", result.isBreakGlassUsed());
        
        if (result.getErrorCode() != null) {
            metadata.put("errorCode", result.getErrorCode());
        }
        
        if (result.getPolicyDecision() != null) {
            metadata.put("policyDecision", result.getPolicyDecision().toString());
        }

        logSecurityEvent(principal, method, path, eventType, result.getReason(), metadata);
    }

    /**
     * Logs a potential security threat.
     *
     * @param principal the authenticated principal
     * @param method HTTP method
     * @param path request path
     * @param threatType type of threat detected
     * @param description threat description
     * @param severity threat severity
     */
    public void logSecurityThreat(
            Principal principal,
            String method,
            String path,
            String threatType,
            String description,
            ThreatSeverity severity) {
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("threatType", threatType);
        metadata.put("severity", severity.name());
        metadata.put("requiresInvestigation", severity.requiresInvestigation());
        
        SecurityEventType eventType = switch (severity) {
            case CRITICAL -> SecurityEventType.THREAT_CRITICAL;
            case HIGH -> SecurityEventType.THREAT_HIGH;
            case MEDIUM -> SecurityEventType.THREAT_MEDIUM;
            case LOW -> SecurityEventType.THREAT_LOW;
        };

        logSecurityEvent(principal, method, path, eventType, description, metadata);
    }

    /**
     * Gets security metrics for monitoring.
     */
    public SecurityMetricsSnapshot getSecurityMetrics() {
        Map<String, Long> eventCounts = new HashMap<>();
        metrics.forEach((key, metric) -> eventCounts.put(key, metric.getCount()));
        
        return new SecurityMetricsSnapshot(
            totalEvents.get(),
            eventCounts,
            metrics.size()
        );
    }

    /**
     * Resets security metrics.
     */
    public void resetMetrics() {
        metrics.clear();
        totalEvents.set(0);
    }

    /**
     * Creates an audit event from security parameters.
     */
    private AuditEvent createAuditEvent(
            Principal principal,
            String method,
            String path,
            SecurityEventType eventType,
            String reason,
            Map<String, Object> metadata) {
        
        Map<String, Object> auditMetadata = new HashMap<>(metadata);
        auditMetadata.put("eventType", eventType.name());
        auditMetadata.put("category", eventType.getCategory());
        auditMetadata.put("priority", eventType.getPriority());
        
        if (principal != null) {
            auditMetadata.put("userId", principal.getName());
            auditMetadata.put("tenantId", principal.getTenantId());
            auditMetadata.put("roles", principal.getRoles());
        }

        return AuditEvent.builder()
            .id(UUID.randomUUID().toString())
            .tenantId(principal == null ? "unknown" : principal.getTenantId())
            .eventType("SECURITY_EVENT")
            .timestamp(Instant.now())
            .principal(principal == null ? "anonymous" : principal.getName())
            .resourceType("http_route")
            .resourceId(path)
            .success(!eventType.isHighPriority())
            .detail("action", method + " " + path)
            .detail("description", reason)
            .details(auditMetadata)
            .build();
    }

    /**
     * Updates security metrics.
     */
    private void updateMetrics(SecurityEventType eventType, Principal principal) {
        String key = eventType.name();
        metrics.compute(key, (k, existing) -> {
            if (existing == null) {
                return new SecurityMetrics(k, 1);
            } else {
                existing.increment();
                return existing;
            }
        });

        // Update tenant-specific metrics
        if (principal != null && principal.getTenantId() != null) {
            String tenantKey = principal.getTenantId() + ":" + eventType.name();
            metrics.compute(tenantKey, (k, existing) -> {
                if (existing == null) {
                    return new SecurityMetrics(k, 1);
                } else {
                    existing.increment();
                    return existing;
                }
            });
        }
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AuditService auditService;
        private boolean enabled = true;

        public Builder auditService(AuditService auditService) {
            this.auditService = auditService;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public SecurityAuditService build() {
            return new SecurityAuditService(this);
        }
    }

    /**
     * Security event types for audit logging.
     */
    public enum SecurityEventType {
        // Authentication events
        AUTH_SUCCESS("AUTHENTICATION", 1),
        AUTH_FAILURE("AUTHENTICATION", 2),
        AUTH_EXPIRED("AUTHENTICATION", 1),
        
        // Authorization events
        ACCESS_GRANTED("AUTHORIZATION", 1),
        ACCESS_DENIED("AUTHORIZATION", 2),
        INSUFFICIENT_PRIVILEGES("AUTHORIZATION", 2),
        
        // Policy events
        POLICY_ALLOWED("POLICY", 1),
        POLICY_DENIED("POLICY", 2),
        POLICY_ERROR("POLICY", 2),
        
        // Tenant isolation events
        TENANT_ISOLATION_VIOLATION("TENANT_ISOLATION", 3),
        CROSS_TENANT_ACCESS("TENANT_ISOLATION", 2),
        
        // Rate limiting events
        RATE_LIMIT_EXCEEDED("RATE_LIMITING", 2),
        RATE_LIMIT_WARNING("RATE_LIMITING", 1),
        
        // Security threats
        THREAT_CRITICAL("THREAT", 4),
        THREAT_HIGH("THREAT", 3),
        THREAT_MEDIUM("THREAT", 2),
        THREAT_LOW("THREAT", 1),
        
        // Break-glass events
        BREAK_GLASS_USED("BREAK_GLASS", 2),
        BREAK_GLASS_DENIED("BREAK_GLASS", 2);

        private final String category;
        private final int priority;

        SecurityEventType(String category, int priority) {
            this.category = category;
            this.priority = priority;
        }

        public String getCategory() {
            return category;
        }

        public int getPriority() {
            return priority;
        }

        public boolean isHighPriority() {
            return priority >= 3;
        }
    }

    /**
     * Threat severity levels.
     */
    public enum ThreatSeverity {
        CRITICAL(true),
        HIGH(true),
        MEDIUM(false),
        LOW(false);

        private final boolean requiresInvestigation;

        ThreatSeverity(boolean requiresInvestigation) {
            this.requiresInvestigation = requiresInvestigation;
        }

        public boolean requiresInvestigation() {
            return requiresInvestigation;
        }
    }

    /**
     * Security metrics for internal tracking.
     */
    private static class SecurityMetrics {
        private final String eventType;
        private final AtomicLong count = new AtomicLong(0);

        SecurityMetrics(String eventType, long initialCount) {
            this.eventType = eventType;
            this.count.set(initialCount);
        }

        void increment() {
            count.incrementAndGet();
        }

        long getCount() {
            return count.get();
        }

        String getEventType() {
            return eventType;
        }
    }

    /**
     * Security metrics snapshot for external consumption.
     */
    public static class SecurityMetricsSnapshot {
        private final long totalEvents;
        private final Map<String, Long> eventCounts;
        private final int uniqueEventTypes;

        public SecurityMetricsSnapshot(long totalEvents, Map<String, Long> eventCounts, int uniqueEventTypes) {
            this.totalEvents = totalEvents;
            this.eventCounts = Map.copyOf(eventCounts);
            this.uniqueEventTypes = uniqueEventTypes;
        }

        public long getTotalEvents() {
            return totalEvents;
        }

        public Map<String, Long> getEventCounts() {
            return eventCounts;
        }

        public int getUniqueEventTypes() {
            return uniqueEventTypes;
        }

        public long getEventCount(String eventType) {
            return eventCounts.getOrDefault(eventType, 0L);
        }

        @Override
        public String toString() {
            return String.format("SecurityMetricsSnapshot{total=%d, types=%d, events=%s}",
                    totalEvents, uniqueEventTypes, eventCounts);
        }
    }
}
