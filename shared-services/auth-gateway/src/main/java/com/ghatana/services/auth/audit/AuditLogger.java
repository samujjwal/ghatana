/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.services.auth.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.core.util.PiiRedactor;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Comprehensive audit logging system for security and compliance.
 *
 * <p>Captures all authentication and authorization events with structured logging
 * for security monitoring, compliance reporting, and incident investigation.
 *
 * <h2>Audit Event Types</h2>
 * <ul>
 *   <li>AUTH_LOGIN_SUCCESS - Successful authentication</li>
 *   <li>AUTH_LOGIN_FAILURE - Failed authentication attempt</li>
 *   <li>AUTH_LOGOUT - User logout</li>
 *   <li>AUTH_TOKEN_ISSUED - JWT token issued</li>
 *   <li>AUTH_TOKEN_VALIDATED - Token validation</li>
 *   <li>AUTH_TOKEN_EXPIRED - Token expiration</li>
 *   <li>AUTH_MFA_ENROLLED - MFA enrollment</li>
 *   <li>AUTH_MFA_VERIFIED - MFA code verification</li>
 *   <li>AUTH_MFA_FAILED - MFA verification failure</li>
 *   <li>AUTH_PASSWORD_CHANGED - Password change</li>
 *   <li>AUTH_ACCOUNT_LOCKED - Account locked due to failed attempts</li>
 *   <li>AUTHZ_ACCESS_GRANTED - Authorization granted</li>
 *   <li>AUTHZ_ACCESS_DENIED - Authorization denied</li>
 *   <li>TENANT_SWITCHED - Tenant context switch</li>
 *   <li>SECURITY_RATE_LIMITED - Rate limit exceeded</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Security audit logging
 * @doc.layer platform
 * @doc.pattern Observer
 */
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    private final ConcurrentLinkedQueue<AuditEvent> eventBuffer = new ConcurrentLinkedQueue<>();
    private final boolean asyncMode;
    private final int bufferSize;

    public AuditLogger() {
        this(true, 1000);
    }

    public AuditLogger(boolean asyncMode, int bufferSize) {
        this.asyncMode = asyncMode;
        this.bufferSize = bufferSize;
    }

    /**
     * Logs a successful login event.
     */
    public Promise<Void> logLoginSuccess(String userId, String tenantId, String ipAddress, String userAgent) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_LOGIN_SUCCESS)
                .userId(userId)
                .tenantId(tenantId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .severity(AuditSeverity.INFO)
                .message("User logged in successfully")
                .build());
    }

    /**
     * Logs a failed login attempt.
     */
    public Promise<Void> logLoginFailure(String username, String tenantId, String ipAddress, String reason) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_LOGIN_FAILURE)
                .userId(username)
                .tenantId(tenantId)
                .ipAddress(ipAddress)
                .severity(AuditSeverity.WARNING)
                .message("Login failed: " + reason)
                .metadata(Map.of("reason", reason))
                .build());
    }

    /**
     * Logs a logout event.
     */
    public Promise<Void> logLogout(String userId, String tenantId, String sessionId) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_LOGOUT)
                .userId(userId)
                .tenantId(tenantId)
                .severity(AuditSeverity.INFO)
                .message("User logged out")
                .metadata(Map.of("sessionId", sessionId))
                .build());
    }

    /**
     * Logs token issuance.
     */
    public Promise<Void> logTokenIssued(String userId, String tenantId, String tokenType, long expiresIn) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_TOKEN_ISSUED)
                .userId(userId)
                .tenantId(tenantId)
                .severity(AuditSeverity.INFO)
                .message("Token issued")
                .metadata(Map.of("tokenType", tokenType, "expiresIn", String.valueOf(expiresIn)))
                .build());
    }

    /**
     * Logs MFA enrollment.
     */
    public Promise<Void> logMfaEnrolled(String userId, String tenantId) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_MFA_ENROLLED)
                .userId(userId)
                .tenantId(tenantId)
                .severity(AuditSeverity.INFO)
                .message("MFA enrolled")
                .build());
    }

    /**
     * Logs MFA verification success.
     */
    public Promise<Void> logMfaVerified(String userId, String tenantId) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_MFA_VERIFIED)
                .userId(userId)
                .tenantId(tenantId)
                .severity(AuditSeverity.INFO)
                .message("MFA code verified")
                .build());
    }

    /**
     * Logs MFA verification failure.
     */
    public Promise<Void> logMfaFailed(String userId, String tenantId, int attemptCount) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_MFA_FAILED)
                .userId(userId)
                .tenantId(tenantId)
                .severity(AuditSeverity.WARNING)
                .message("MFA verification failed")
                .metadata(Map.of("attemptCount", String.valueOf(attemptCount)))
                .build());
    }

    /**
     * Logs authorization decision.
     */
    public Promise<Void> logAuthorizationDecision(String userId, String tenantId, String resource,
                                                   String action, boolean granted) {
        return logEvent(AuditEvent.builder()
                .eventType(granted ? AuditEventType.AUTHZ_ACCESS_GRANTED : AuditEventType.AUTHZ_ACCESS_DENIED)
                .userId(userId)
                .tenantId(tenantId)
                .severity(granted ? AuditSeverity.INFO : AuditSeverity.WARNING)
                .message(String.format("Access %s for %s on %s", granted ? "granted" : "denied", action, resource))
                .metadata(Map.of("resource", resource, "action", action))
                .build());
    }

    /**
     * Logs rate limiting event.
     */
    public Promise<Void> logRateLimited(String userId, String tenantId, String ipAddress, String endpoint) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.SECURITY_RATE_LIMITED)
                .userId(userId)
                .tenantId(tenantId)
                .ipAddress(ipAddress)
                .severity(AuditSeverity.WARNING)
                .message("Rate limit exceeded")
                .metadata(Map.of("endpoint", endpoint))
                .build());
    }

    /**
     * Logs account lockout.
     */
    public Promise<Void> logAccountLocked(String userId, String tenantId, String reason) {
        return logEvent(AuditEvent.builder()
                .eventType(AuditEventType.AUTH_ACCOUNT_LOCKED)
                .userId(userId)
                .tenantId(tenantId)
                .severity(AuditSeverity.CRITICAL)
                .message("Account locked: " + reason)
                .metadata(Map.of("reason", reason))
                .build());
    }

    /**
     * Logs a generic audit event.
     */
    public Promise<Void> logEvent(AuditEvent event) {
        AuditEvent sanitizedEvent = sanitizeEvent(event);
        if (asyncMode) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                writeEvent(sanitizedEvent);
                return null;
            });
        } else {
            writeEvent(sanitizedEvent);
            return Promise.complete();
        }
    }

    private AuditEvent sanitizeEvent(AuditEvent event) {
        Map<String, String> metadata = event.metadata() == null ? Map.of() : event.metadata();
        Map<String, String> sanitizedMetadata = new HashMap<>();
        metadata.forEach((key, value) -> sanitizedMetadata.put(key, PiiRedactor.redact(value)));

        return new AuditEvent(
                event.eventId(),
                event.eventType(),
                event.severity(),
                event.userId(),
                event.tenantId(),
                event.ipAddress(),
                PiiRedactor.redact(event.userAgent()),
                PiiRedactor.redact(event.message()),
                sanitizedMetadata,
                event.timestamp()
        );
    }

    private void writeEvent(AuditEvent event) {
        try {
            // Write to structured audit log
            String json = objectMapper.writeValueAsString(event);
            auditLog.info(json);

            // Also log to standard logger for immediate visibility
            log.info("[AUDIT] {} - User: {}, Tenant: {}, IP: {}, Message: {}",
                    event.eventType(),
                    event.userId(),
                    event.tenantId(),
                    event.ipAddress(),
                    event.message());

            // Buffer for batch processing if needed
            if (eventBuffer.size() < bufferSize) {
                eventBuffer.offer(event);
            } else {
                // Buffer full - remove oldest
                eventBuffer.poll();
                eventBuffer.offer(event);
            }

        } catch (Exception e) {
            log.error("Failed to write audit event", e);
        }
    }

    /**
     * Retrieves recent audit events from buffer.
     */
    public AuditEvent[] getRecentEvents(int count) {
        return eventBuffer.stream()
                .limit(count)
                .toArray(AuditEvent[]::new);
    }

    // ─── Data Classes ────────────────────────────────────────────────────────

    public enum AuditEventType {
        AUTH_LOGIN_SUCCESS,
        AUTH_LOGIN_FAILURE,
        AUTH_LOGOUT,
        AUTH_TOKEN_ISSUED,
        AUTH_TOKEN_VALIDATED,
        AUTH_TOKEN_EXPIRED,
        AUTH_MFA_ENROLLED,
        AUTH_MFA_VERIFIED,
        AUTH_MFA_FAILED,
        AUTH_PASSWORD_CHANGED,
        AUTH_ACCOUNT_LOCKED,
        AUTHZ_ACCESS_GRANTED,
        AUTHZ_ACCESS_DENIED,
        TENANT_SWITCHED,
        SECURITY_RATE_LIMITED
    }

    public enum AuditSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    public record AuditEvent(
            String eventId,
            AuditEventType eventType,
            AuditSeverity severity,
            String userId,
            String tenantId,
            String ipAddress,
            String userAgent,
            String message,
            Map<String, String> metadata,
            Instant timestamp
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String eventId = java.util.UUID.randomUUID().toString();
            private AuditEventType eventType;
            private AuditSeverity severity = AuditSeverity.INFO;
            private String userId;
            private String tenantId;
            private String ipAddress;
            private String userAgent;
            private String message;
            private Map<String, String> metadata = new HashMap<>();
            private Instant timestamp = Instant.now();

            public Builder eventType(AuditEventType eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder severity(AuditSeverity severity) {
                this.severity = severity;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder ipAddress(String ipAddress) {
                this.ipAddress = ipAddress;
                return this;
            }

            public Builder userAgent(String userAgent) {
                this.userAgent = userAgent;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public AuditEvent build() {
                return new AuditEvent(
                        eventId,
                        eventType,
                        severity,
                        userId,
                        tenantId,
                        ipAddress,
                        userAgent,
                        message,
                        metadata,
                        timestamp
                );
            }
        }
    }
}
