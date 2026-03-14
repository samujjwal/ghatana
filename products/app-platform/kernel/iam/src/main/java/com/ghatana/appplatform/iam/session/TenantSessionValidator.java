/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Validates that a session's tenant matches the tenant encoded in the JWT (STORY-K01-008).
 *
 * <p>Prevents session-fixation attacks where an authenticated session for tenant A is
 * replayed against tenant B's resources. The validator is a stateless component that
 * compares the {@code tenantId} from the stored {@link SessionStore.SessionEntry} with
 * the {@code tenantId} extracted from the JWT payload by the caller.
 *
 * <h3>Integration point</h3>
 * <p>Call {@link #validate} in the request filter pipeline <em>after</em> the JWT has
 * been verified and before request routing. If validation fails, respond with HTTP 401
 * and include a {@link ValidationFailure} with the failure reason for structured logging.
 *
 * @doc.type class
 * @doc.purpose Cross-tenant session isolation guard (K01-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TenantSessionValidator {

    private static final Logger log = LoggerFactory.getLogger(TenantSessionValidator.class);

    private final SessionStore sessions;

    /**
     * @param sessions session store used to look up existing sessions
     */
    public TenantSessionValidator(SessionStore sessions) {
        this.sessions = sessions;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Validates that the active session for {@code sessionId} belongs to {@code jwtTenantId}.
     *
     * <p>Returns {@link ValidationResult#ok()} when:
     * <ul>
     *   <li>the session exists in the store</li>
     *   <li>the tenant in the session matches the tenant in the JWT</li>
     * </ul>
     *
     * @param sessionId   cookie/header session identifier
     * @param jwtTenantId tenantId extracted from the verified JWT {@code tenant_id} claim
     * @return {@link ValidationResult} — inspect {@code valid()} before proceeding
     */
    public ValidationResult validate(String sessionId, String jwtTenantId) {
        Optional<SessionStore.SessionEntry> entry = sessions.get(sessionId);

        if (entry.isEmpty()) {
            log.warn("Session not found: sessionId={}", sessionId);
            return ValidationResult.failure(ValidationFailure.SESSION_NOT_FOUND);
        }

        SessionStore.SessionEntry session = entry.get();

        if (!session.tenantId().equals(jwtTenantId)) {
            log.error(
                "Tenant mismatch — potential session-fixation attack: " +
                "sessionId={} sessionTenant={} jwtTenant={}",
                sessionId, session.tenantId(), jwtTenantId
            );
            return ValidationResult.failure(ValidationFailure.TENANT_MISMATCH);
        }

        return ValidationResult.ok();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Result types
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Structured outcome of a tenant-session validation check.
     *
     * @param valid   true when the session is present and tenant IDs match
     * @param failure populated when {@code valid} is false; null otherwise
     */
    public record ValidationResult(boolean valid, ValidationFailure failure) {

        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        static ValidationResult failure(ValidationFailure reason) {
            return new ValidationResult(false, reason);
        }
    }

    /** Reason codes for validation failures used in structured logging and HTTP response mapping. */
    public enum ValidationFailure {
        /** No session found for the provided session ID (expired or non-existent). */
        SESSION_NOT_FOUND,
        /** Session exists but its tenant does not match the JWT tenant claim. */
        TENANT_MISMATCH
    }
}
