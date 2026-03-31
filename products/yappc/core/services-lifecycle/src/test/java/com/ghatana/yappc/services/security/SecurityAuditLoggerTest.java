package com.ghatana.yappc.services.security;

import com.ghatana.audit.AuditLogger;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SecurityAuditLogger}.
 *
 * <p>Uses an in-memory {@link AuditLogger} stub to inspect the structured events
 * forwarded by the logger. No async event loop needed — the stub resolves synchronously.
 */
@DisplayName("SecurityAuditLogger")
class SecurityAuditLoggerTest {

    /**
     * Capturing test double that records every event map passed to {@code log()}.
     */
    private static class CapturingAuditLogger implements AuditLogger {
        final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public Promise<Void> log(Map<String, Object> event) {
            events.add(Map.copyOf(event));
            return Promise.complete();
        }

        Map<String, Object> last() {
            assertThat(events).isNotEmpty();
            return events.get(events.size() - 1);
        }
    }

    private CapturingAuditLogger delegate;
    private SecurityAuditLogger  logger;

    @BeforeEach
    void setUp() {
        delegate = new CapturingAuditLogger();
        logger   = new SecurityAuditLogger(delegate);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws on null delegate")
    void constructorThrowsOnNullDelegate() {
        assertThatThrownBy(() -> new SecurityAuditLogger(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── loginSuccess ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginSuccess emits AUTH_LOGIN_SUCCESS with ALLOW outcome")
    void loginSuccessEmitsCorrectEvent() {
        logger.loginSuccess("alice", "tenant-1", "10.0.0.1");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "AUTH_LOGIN_SUCCESS");
        assertThat(event).containsEntry("outcome", "ALLOW");
        assertThat(event).containsEntry("principal", "alice");
        assertThat(event).containsEntry("tenant_id", "tenant-1");
        assertThat(event).containsKey("timestamp");
    }

    // ── loginFailure ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginFailure emits AUTH_LOGIN_FAILURE with DENY outcome")
    void loginFailureEmitsCorrectEvent() {
        logger.loginFailure("bob", "tenant-2", "bad-password");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "AUTH_LOGIN_FAILURE");
        assertThat(event).containsEntry("outcome", "DENY");
        assertThat(event).containsEntry("principal", "bob");
    }

    // ── tokenValidation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("tokenValidation with valid=true emits AUTH_TOKEN_VALID and ALLOW")
    void tokenValidationValidEmitsAllow() {
        logger.tokenValidation(true, "alice", "tenant-1");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "AUTH_TOKEN_VALID");
        assertThat(event).containsEntry("outcome", "ALLOW");
    }

    @Test
    @DisplayName("tokenValidation with valid=false emits AUTH_TOKEN_INVALID and DENY")
    void tokenValidationInvalidEmitsDeny() {
        logger.tokenValidation(false, "unknown", "tenant-x");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "AUTH_TOKEN_INVALID");
        assertThat(event).containsEntry("outcome", "DENY");
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout emits AUTH_LOGOUT with ALLOW outcome")
    void logoutEmitsCorrectEvent() {
        logger.logout("alice", "tenant-1");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "AUTH_LOGOUT");
        assertThat(event).containsEntry("outcome", "ALLOW");
    }

    // ── authorizationDecision ─────────────────────────────────────────────────

    @Test
    @DisplayName("authorizationDecision allowed emits AUTHZ_ALLOW")
    void authorizationDecisionAllowedEmitsAllow() {
        logger.authorizationDecision(true, "alice", "tenant-1", "lifecycle-api", "write");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "AUTHZ_ALLOW");
        assertThat(event).containsEntry("outcome", "ALLOW");
        assertThat(event).containsEntry("action", "write");
    }

    @Test
    @DisplayName("authorizationDecision denied emits AUTHZ_DENY")
    void authorizationDecisionDeniedEmitsDeny() {
        logger.authorizationDecision(false, "viewer", "tenant-1", "admin-api", "delete");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "AUTHZ_DENY");
        assertThat(event).containsEntry("outcome", "DENY");
    }

    // ── tenantIsolationViolation ──────────────────────────────────────────────

    @Test
    @DisplayName("tenantIsolationViolation emits TENANT_ISOLATION_VIOLATION with DENY")
    void tenantIsolationViolationEmitsCorrectEvent() {
        logger.tenantIsolationViolation("alice", "tenant-A", "tenant-B", "/projects/123");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "TENANT_ISOLATION_VIOLATION");
        assertThat(event).containsEntry("outcome", "DENY");
        assertThat(event).containsEntry("tenant_id", "tenant-A");
        assertThat(event).containsEntry("target_tenant", "tenant-B");
    }

    // ── rateLimitTriggered ───────────────────────────────────────────────────

    @Test
    @DisplayName("rateLimitTriggered emits RATE_LIMIT_TRIGGERED with DENY")
    void rateLimitTriggeredEmitsCorrectEvent() {
        logger.rateLimitTriggered("alice", "tenant-1", "/api/scaffold", 100);

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "RATE_LIMIT_TRIGGERED");
        assertThat(event).containsEntry("outcome", "DENY");
        assertThat(event).containsEntry("limit", "100");
    }

    // ── sensitiveDataAccess ──────────────────────────────────────────────────

    @Test
    @DisplayName("sensitiveDataAccess READ emits SENSITIVE_DATA_ACCESSED with ALLOW")
    void sensitiveDataAccessReadEmitsCorrectEvent() {
        logger.sensitiveDataAccess("alice", "tenant-1", "api-key", "READ");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "SENSITIVE_DATA_ACCESSED");
        assertThat(event).containsEntry("outcome", "ALLOW");
        assertThat(event).containsEntry("operation", "READ");
    }

    @Test
    @DisplayName("sensitiveDataAccess WRITE emits SENSITIVE_DATA_MODIFIED with ALLOW")
    void sensitiveDataAccessWriteEmitsCorrectEvent() {
        logger.sensitiveDataAccess("alice", "tenant-1", "encryption-key", "WRITE");

        Map<String, Object> event = delegate.last();
        assertThat(event).containsEntry("event_type", "SENSITIVE_DATA_MODIFIED");
        assertThat(event).containsEntry("outcome", "ALLOW");
    }

    // ── null safety ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("null principal is replaced with 'anonymous'")
    void nullPrincipalIsReplaced() {
        logger.loginSuccess(null, "tenant-1", "127.0.0.1");

        assertThat(delegate.last()).containsEntry("principal", "anonymous");
    }

    @Test
    @DisplayName("null tenantId is replaced with 'unknown'")
    void nullTenantIdIsReplaced() {
        logger.loginSuccess("alice", null, "127.0.0.1");

        assertThat(delegate.last()).containsEntry("tenant_id", "unknown");
    }

    // ── resilience: delegate failure does not propagate ───────────────────────

    @Test
    @DisplayName("delegate exception does not propagate to caller")
    void delegateExceptionDoesNotPropagate() {
        AuditLogger failingDelegate = event -> { throw new RuntimeException("audit system down"); };
        SecurityAuditLogger resilientLogger = new SecurityAuditLogger(failingDelegate);

        // Should NOT throw
        resilientLogger.loginSuccess("alice", "tenant-1", "127.0.0.1");
    }

    // ── event count ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("each method call produces exactly one audit event")
    void eachCallProducesOneEvent() {
        logger.loginSuccess("alice", "t1", "127.0.0.1");
        logger.loginFailure("bob", "t1", "wrong-key");
        logger.logout("alice", "t1");

        assertThat(delegate.events).hasSize(3);
    }
}
