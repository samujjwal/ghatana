/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Security Audit Logger
 */
package com.ghatana.yappc.services.security;

import com.ghatana.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Security-domain audit logger that emits structured security events to the platform
 * {@link AuditLogger} and to the SLF4J security logger.
 *
 * <p>Covers the key security event types for YAPPC operations:
 * <ul>
 *   <li>Authentication — login success/failure, token validation</li>
 *   <li>Authorization — RBAC allow/deny decisions</li>
 *   <li>Tenant isolation — cross-tenant access attempts</li>
 *   <li>Rate limiting — throttle triggers</li>
 *   <li>Sensitive data access — encryption/decryption operations on PII</li>
 * </ul>
 *
 * <p>Every event includes:
 * <ul>
 *   <li>{@code event_type} — one of the canonical types above</li>
 *   <li>{@code principal} — resolved user/agent identifier, or {@code "anonymous"}</li>
 *   <li>{@code tenant_id} — tenant scope of the event</li>
 *   <li>{@code resource} — the resource being acted upon</li>
 *   <li>{@code outcome} — {@code ALLOW} or {@code DENY}</li>
 *   <li>{@code timestamp} — ISO-8601 instant</li>
 *   <li>{@code detail} — optional free-form detail string</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Structured security audit logger for YAPPC lifecycle security events
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("yappc.security.audit");

    /** Canonical security event types emitted by this logger. */
    public enum EventType {
        AUTH_LOGIN_SUCCESS,
        AUTH_LOGIN_FAILURE,
        AUTH_TOKEN_VALID,
        AUTH_TOKEN_INVALID,
        AUTH_LOGOUT,
        AUTHZ_ALLOW,
        AUTHZ_DENY,
        TENANT_ISOLATION_VIOLATION,
        RATE_LIMIT_TRIGGERED,
        SENSITIVE_DATA_ACCESSED,
        SENSITIVE_DATA_MODIFIED,
        SECURITY_HEADER_APPLIED
    }

    /** Outcome for a security event (allow or deny). */
    public enum Outcome { ALLOW, DENY }

    private final AuditLogger delegate;

    /**
     * @param delegate platform audit logger to forward structured events to
     */
    public SecurityAuditLogger(AuditLogger delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate audit logger must not be null");
    }

    // ── Public logging API ───────────────────────────────────────────────────

    /** Records a successful login event. */
    public void loginSuccess(String principal, String tenantId, String fromIp) {
        emit(EventType.AUTH_LOGIN_SUCCESS, Outcome.ALLOW, principal, tenantId,
                "session", Map.of("from_ip", safe(fromIp)));
    }

    /** Records a failed login attempt. */
    public void loginFailure(String principal, String tenantId, String reason) {
        emit(EventType.AUTH_LOGIN_FAILURE, Outcome.DENY, principal, tenantId,
                "session", Map.of("reason", safe(reason)));
    }

    /** Records a JWT token validation result. */
    public void tokenValidation(boolean valid, String principal, String tenantId) {
        EventType type = valid ? EventType.AUTH_TOKEN_VALID : EventType.AUTH_TOKEN_INVALID;
        emit(type, valid ? Outcome.ALLOW : Outcome.DENY, principal, tenantId, "jwt", Map.of());
    }

    /** Records a logout event. */
    public void logout(String principal, String tenantId) {
        emit(EventType.AUTH_LOGOUT, Outcome.ALLOW, principal, tenantId, "session", Map.of());
    }

    /**
     * Records an RBAC authorization decision.
     *
     * @param allowed   true if access was granted
     * @param principal who is requesting access
     * @param tenantId  tenant scope
     * @param resource  resource being accessed (e.g. {@code "yappc:lifecycle-api"})
     * @param action    action attempted (e.g. {@code "write"})
     */
    public void authorizationDecision(boolean allowed, String principal, String tenantId,
                                      String resource, String action) {
        EventType type = allowed ? EventType.AUTHZ_ALLOW : EventType.AUTHZ_DENY;
        emit(type, allowed ? Outcome.ALLOW : Outcome.DENY, principal, tenantId, resource,
                Map.of("action", safe(action)));
    }

    /**
     * Records an attempted cross-tenant access violation.
     *
     * @param principal    requesting principal
     * @param requestTenant tenant the principal belongs to
     * @param targetTenant  tenant of the resource being accessed
     * @param resource      the resource path
     */
    public void tenantIsolationViolation(String principal, String requestTenant,
                                         String targetTenant, String resource) {
        emit(EventType.TENANT_ISOLATION_VIOLATION, Outcome.DENY, principal, requestTenant,
                resource, Map.of("target_tenant", safe(targetTenant)));
        log.warn("[SECURITY] Tenant isolation violation: principal={} tenant={} targetTenant={} resource={}",
                principal, requestTenant, targetTenant, resource);
    }

    /** Records a rate-limit trigger event. */
    public void rateLimitTriggered(String principal, String tenantId, String resource, int limit) {
        emit(EventType.RATE_LIMIT_TRIGGERED, Outcome.DENY, principal, tenantId, resource,
                Map.of("limit", String.valueOf(limit)));
    }

    /**
     * Records access to sensitive (encrypted) data.
     *
     * @param principal  who accessed the data
     * @param tenantId   tenant scope
     * @param dataType   logical name of the sensitive data type
     * @param operation  {@code "READ"} or {@code "WRITE"}
     */
    public void sensitiveDataAccess(String principal, String tenantId, String dataType, String operation) {
        EventType type = "WRITE".equalsIgnoreCase(operation)
                ? EventType.SENSITIVE_DATA_MODIFIED
                : EventType.SENSITIVE_DATA_ACCESSED;
        emit(type, Outcome.ALLOW, principal, tenantId, dataType,
                Map.of("operation", safe(operation)));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void emit(EventType type, Outcome outcome, String principal, String tenantId,
                      String resource, Map<String, String> extra) {
        String principalSafe = principal != null ? principal : "unknown";
        String tenantSafe    = tenantId  != null ? tenantId  : "unknown";

        // Structured log line that log-aggregation can parse
        log.info("[SECURITY] type={} outcome={} principal={} tenant={} resource={} ts={} extra={}",
                type, outcome, principalSafe, tenantSafe, resource, Instant.now(), extra);

        // Forward to platform AuditLogger (fire-and-forget; do not block caller)
        try {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put("event_type", type.name());
            event.put("outcome",    outcome.name());
            event.put("principal",  principalSafe);
            event.put("tenant_id",  tenantSafe);
            event.put("resource",   safe(resource));
            event.put("timestamp",  Instant.now().toString());
            extra.forEach(event::put);
            delegate.log(event);
        } catch (Exception ex) {
            // Never let audit failures propagate into the business path
            log.error("[SECURITY] Failed to forward audit event to delegate: {}", ex.getMessage(), ex);
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
