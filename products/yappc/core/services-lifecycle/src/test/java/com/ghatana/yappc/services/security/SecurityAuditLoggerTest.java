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
@DisplayName("SecurityAuditLogger [GH-90000]")
class SecurityAuditLoggerTest {

    /**
     * Capturing test double that records every event map passed to {@code log()}. // GH-90000
     */
    private static class CapturingAuditLogger implements AuditLogger {
        final List<Map<String, Object>> events = new ArrayList<>(); // GH-90000

        @Override
        public Promise<Void> log(Map<String, Object> event) { // GH-90000
            events.add(Map.copyOf(event)); // GH-90000
            return Promise.complete(); // GH-90000
        }

        Map<String, Object> last() { // GH-90000
            assertThat(events).isNotEmpty(); // GH-90000
            return events.get(events.size() - 1); // GH-90000
        }
    }

    private CapturingAuditLogger delegate;
    private SecurityAuditLogger  logger;

    @BeforeEach
    void setUp() { // GH-90000
        delegate = new CapturingAuditLogger(); // GH-90000
        logger   = new SecurityAuditLogger(delegate); // GH-90000
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws on null delegate [GH-90000]")
    void constructorThrowsOnNullDelegate() { // GH-90000
        assertThatThrownBy(() -> new SecurityAuditLogger(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── loginSuccess ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginSuccess emits AUTH_LOGIN_SUCCESS with ALLOW outcome [GH-90000]")
    void loginSuccessEmitsCorrectEvent() { // GH-90000
        logger.loginSuccess("alice", "tenant-1", "10.0.0.1"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "AUTH_LOGIN_SUCCESS"); // GH-90000
        assertThat(event).containsEntry("outcome", "ALLOW"); // GH-90000
        assertThat(event).containsEntry("principal", "alice"); // GH-90000
        assertThat(event).containsEntry("tenant_id", "tenant-1"); // GH-90000
        assertThat(event).containsKey("timestamp [GH-90000]");
    }

    // ── loginFailure ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginFailure emits AUTH_LOGIN_FAILURE with DENY outcome [GH-90000]")
    void loginFailureEmitsCorrectEvent() { // GH-90000
        logger.loginFailure("bob", "tenant-2", "bad-password"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "AUTH_LOGIN_FAILURE"); // GH-90000
        assertThat(event).containsEntry("outcome", "DENY"); // GH-90000
        assertThat(event).containsEntry("principal", "bob"); // GH-90000
    }

    // ── tokenValidation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("tokenValidation with valid=true emits AUTH_TOKEN_VALID and ALLOW [GH-90000]")
    void tokenValidationValidEmitsAllow() { // GH-90000
        logger.tokenValidation(true, "alice", "tenant-1"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "AUTH_TOKEN_VALID"); // GH-90000
        assertThat(event).containsEntry("outcome", "ALLOW"); // GH-90000
    }

    @Test
    @DisplayName("tokenValidation with valid=false emits AUTH_TOKEN_INVALID and DENY [GH-90000]")
    void tokenValidationInvalidEmitsDeny() { // GH-90000
        logger.tokenValidation(false, "unknown", "tenant-x"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "AUTH_TOKEN_INVALID"); // GH-90000
        assertThat(event).containsEntry("outcome", "DENY"); // GH-90000
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout emits AUTH_LOGOUT with ALLOW outcome [GH-90000]")
    void logoutEmitsCorrectEvent() { // GH-90000
        logger.logout("alice", "tenant-1"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "AUTH_LOGOUT"); // GH-90000
        assertThat(event).containsEntry("outcome", "ALLOW"); // GH-90000
    }

    // ── authorizationDecision ─────────────────────────────────────────────────

    @Test
    @DisplayName("authorizationDecision allowed emits AUTHZ_ALLOW [GH-90000]")
    void authorizationDecisionAllowedEmitsAllow() { // GH-90000
        logger.authorizationDecision(true, "alice", "tenant-1", "lifecycle-api", "write"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "AUTHZ_ALLOW"); // GH-90000
        assertThat(event).containsEntry("outcome", "ALLOW"); // GH-90000
        assertThat(event).containsEntry("action", "write"); // GH-90000
    }

    @Test
    @DisplayName("authorizationDecision denied emits AUTHZ_DENY [GH-90000]")
    void authorizationDecisionDeniedEmitsDeny() { // GH-90000
        logger.authorizationDecision(false, "viewer", "tenant-1", "admin-api", "delete"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "AUTHZ_DENY"); // GH-90000
        assertThat(event).containsEntry("outcome", "DENY"); // GH-90000
    }

    // ── tenantIsolationViolation ──────────────────────────────────────────────

    @Test
    @DisplayName("tenantIsolationViolation emits TENANT_ISOLATION_VIOLATION with DENY [GH-90000]")
    void tenantIsolationViolationEmitsCorrectEvent() { // GH-90000
        logger.tenantIsolationViolation("alice", "tenant-A", "tenant-B", "/projects/123"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "TENANT_ISOLATION_VIOLATION"); // GH-90000
        assertThat(event).containsEntry("outcome", "DENY"); // GH-90000
        assertThat(event).containsEntry("tenant_id", "tenant-A"); // GH-90000
        assertThat(event).containsEntry("target_tenant", "tenant-B"); // GH-90000
    }

    // ── rateLimitTriggered ───────────────────────────────────────────────────

    @Test
    @DisplayName("rateLimitTriggered emits RATE_LIMIT_TRIGGERED with DENY [GH-90000]")
    void rateLimitTriggeredEmitsCorrectEvent() { // GH-90000
        logger.rateLimitTriggered("alice", "tenant-1", "/api/scaffold", 100); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "RATE_LIMIT_TRIGGERED"); // GH-90000
        assertThat(event).containsEntry("outcome", "DENY"); // GH-90000
        assertThat(event).containsEntry("limit", "100"); // GH-90000
    }

    // ── sensitiveDataAccess ──────────────────────────────────────────────────

    @Test
    @DisplayName("sensitiveDataAccess READ emits SENSITIVE_DATA_ACCESSED with ALLOW [GH-90000]")
    void sensitiveDataAccessReadEmitsCorrectEvent() { // GH-90000
        logger.sensitiveDataAccess("alice", "tenant-1", "api-key", "READ"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "SENSITIVE_DATA_ACCESSED"); // GH-90000
        assertThat(event).containsEntry("outcome", "ALLOW"); // GH-90000
        assertThat(event).containsEntry("operation", "READ"); // GH-90000
    }

    @Test
    @DisplayName("sensitiveDataAccess WRITE emits SENSITIVE_DATA_MODIFIED with ALLOW [GH-90000]")
    void sensitiveDataAccessWriteEmitsCorrectEvent() { // GH-90000
        logger.sensitiveDataAccess("alice", "tenant-1", "encryption-key", "WRITE"); // GH-90000

        Map<String, Object> event = delegate.last(); // GH-90000
        assertThat(event).containsEntry("event_type", "SENSITIVE_DATA_MODIFIED"); // GH-90000
        assertThat(event).containsEntry("outcome", "ALLOW"); // GH-90000
    }

    // ── null safety ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("null principal is replaced with 'anonymous' [GH-90000]")
    void nullPrincipalIsReplaced() { // GH-90000
        logger.loginSuccess(null, "tenant-1", "127.0.0.1"); // GH-90000

        assertThat(delegate.last()).containsEntry("principal", "anonymous"); // GH-90000
    }

    @Test
    @DisplayName("null tenantId is replaced with 'unknown' [GH-90000]")
    void nullTenantIdIsReplaced() { // GH-90000
        logger.loginSuccess("alice", null, "127.0.0.1"); // GH-90000

        assertThat(delegate.last()).containsEntry("tenant_id", "unknown"); // GH-90000
    }

    // ── resilience: delegate failure does not propagate ───────────────────────

    @Test
    @DisplayName("delegate exception does not propagate to caller [GH-90000]")
    void delegateExceptionDoesNotPropagate() { // GH-90000
        AuditLogger failingDelegate = event -> { throw new RuntimeException("audit system down [GH-90000]"); };
        SecurityAuditLogger resilientLogger = new SecurityAuditLogger(failingDelegate); // GH-90000

        // Should NOT throw
        resilientLogger.loginSuccess("alice", "tenant-1", "127.0.0.1"); // GH-90000
    }

    // ── event count ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("each method call produces exactly one audit event [GH-90000]")
    void eachCallProducesOneEvent() { // GH-90000
        logger.loginSuccess("alice", "t1", "127.0.0.1"); // GH-90000
        logger.loginFailure("bob", "t1", "wrong-key"); // GH-90000
        logger.logout("alice", "t1"); // GH-90000

        assertThat(delegate.events).hasSize(3); // GH-90000
    }
}
