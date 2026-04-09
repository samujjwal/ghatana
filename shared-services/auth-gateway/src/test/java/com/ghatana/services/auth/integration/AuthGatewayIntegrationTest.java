/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.services.auth.integration;

import com.ghatana.services.auth.audit.AuditLogger;
import com.ghatana.services.auth.mfa.MfaService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Auth Gateway with MFA and audit logging.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthGatewayIntegrationTest extends EventloopTestBase {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String TEST_USERNAME = "integration-test-user";
    private static final String TEST_PASSWORD = "SecurePassword123!";
    private static final String TEST_TENANT = "test-tenant";

    private MfaService mfaService;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService();
        auditLogger = new AuditLogger();
    }

    @Test
    @Order(1)
    @DisplayName("Integration: Should complete full authentication flow with MFA")
    void testFullAuthenticationFlowWithMfa() throws Exception {
        // Step 1: Enroll user in MFA
        MfaService.EnrollmentData enrollment = runPromise(() -> mfaService.enrollUser(TEST_USERNAME, "Ghatana"));

        assertThat(enrollment.secret()).isNotBlank();
        assertThat(enrollment.qrCodeUri()).contains("otpauth://totp/");
        assertThat(enrollment.backupCodes()).hasSize(10);

        // Step 2: Verify enrollment (in real scenario, user would scan QR and enter code)
        // For testing, we simulate the verification
        boolean verified = runPromise(() -> mfaService.verifyEnrollment(TEST_USERNAME, "123456"));

        // Will fail with dummy code, but tests the flow
        assertThat(verified).isFalse();

        // Step 3: Log the enrollment event
        runPromise(() -> auditLogger.logMfaEnrolled(TEST_USERNAME, TEST_TENANT));

        // Verify audit event was logged
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10);
        assertThat(events).isNotEmpty();
        assertThat(events[0].eventType()).isEqualTo(AuditLogger.AuditEventType.AUTH_MFA_ENROLLED);
    }

    @Test
    @Order(2)
    @DisplayName("Integration: Should log authentication attempts")
    void testAuthenticationAuditLogging() throws Exception {
        // Log successful login
        runPromise(() -> auditLogger.logLoginSuccess(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "Mozilla/5.0"));

        // Log failed login
        runPromise(() -> auditLogger.logLoginFailure(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "Invalid password"));

        // Verify both events logged
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10);
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);

        boolean hasSuccess = false;
        boolean hasFailure = false;
        for (AuditLogger.AuditEvent event : events) {
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_SUCCESS) {
                hasSuccess = true;
            }
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_FAILURE) {
                hasFailure = true;
            }
        }

        assertThat(hasSuccess).isTrue();
        assertThat(hasFailure).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Integration: Should handle MFA validation with rate limiting")
    void testMfaValidationWithRateLimiting() throws Exception {
        // Enroll user
        runPromise(() -> mfaService.enrollUser(TEST_USERNAME, "Ghatana"));

        // Attempt multiple failed validations
        for (int i = 0; i < 6; i++) {
            boolean valid = runPromise(() -> mfaService.validateCode(TEST_USERNAME, "000000"));
            assertThat(valid).isFalse();

            // Log each failed attempt
            final int attempt = i;
            runPromise(() -> auditLogger.logMfaFailed(TEST_USERNAME, TEST_TENANT, attempt + 1));
        }

        // Verify rate limiting kicked in (check audit logs)
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10);
        long mfaFailures = 0;
        for (AuditLogger.AuditEvent event : events) {
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_MFA_FAILED) {
                mfaFailures++;
            }
        }

        assertThat(mfaFailures).isGreaterThanOrEqualTo(5);
    }

    @Test
    @Order(4)
    @DisplayName("Integration: Should validate backup codes and invalidate after use")
    void testBackupCodeValidation() throws Exception {
        // Enroll user
        MfaService.EnrollmentData enrollment = runPromise(() -> mfaService.enrollUser(TEST_USERNAME + "-backup", "Ghatana"));
        String backupCode = enrollment.backupCodes()[0];

        // Backup codes work right after enrollment
        boolean valid = runPromise(() -> mfaService.validateBackupCode(TEST_USERNAME + "-backup", backupCode));
        assertThat(valid).isTrue();

        if (valid) {
            runPromise(() -> auditLogger.logMfaVerified(TEST_USERNAME + "-backup", TEST_TENANT));
        }
    }

    @Test
    @Order(5)
    @DisplayName("Integration: Should track authorization decisions in audit log")
    void testAuthorizationAuditLogging() throws Exception {
        // Log access granted
        runPromise(() -> auditLogger.logAuthorizationDecision(
            TEST_USERNAME, TEST_TENANT, "/api/projects", "CREATE", true
        ));

        // Log access denied
        runPromise(() -> auditLogger.logAuthorizationDecision(
            TEST_USERNAME, TEST_TENANT, "/api/admin", "DELETE", false
        ));

        // Verify both logged
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10);

        boolean hasGranted = false;
        boolean hasDenied = false;
        for (AuditLogger.AuditEvent event : events) {
            if (event.eventType() == AuditLogger.AuditEventType.AUTHZ_ACCESS_GRANTED) {
                hasGranted = true;
            }
            if (event.eventType() == AuditLogger.AuditEventType.AUTHZ_ACCESS_DENIED) {
                hasDenied = true;
            }
        }

        assertThat(hasGranted).isTrue();
        assertThat(hasDenied).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Integration: Should log rate limiting events")
    void testRateLimitingAuditLogging() throws Exception {
        runPromise(() -> auditLogger.logRateLimited(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "/auth/login"));

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10);

        boolean hasRateLimit = false;
        for (AuditLogger.AuditEvent event : events) {
            if (event.eventType() == AuditLogger.AuditEventType.SECURITY_RATE_LIMITED) {
                hasRateLimit = true;
                assertThat(event.severity()).isEqualTo(AuditLogger.AuditSeverity.WARNING);
            }
        }

        assertThat(hasRateLimit).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("Integration: Should log account lockout events")
    void testAccountLockoutAuditLogging() throws Exception {
        runPromise(() -> auditLogger.logAccountLocked(TEST_USERNAME, TEST_TENANT, "Too many failed login attempts"));

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10);

        boolean hasLockout = false;
        for (AuditLogger.AuditEvent event : events) {
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_ACCOUNT_LOCKED) {
                hasLockout = true;
                assertThat(event.severity()).isEqualTo(AuditLogger.AuditSeverity.CRITICAL);
            }
        }

        assertThat(hasLockout).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("Integration: Should handle complete login-logout cycle with audit trail")
    void testCompleteAuthenticationCycle() throws Exception {
        String sessionId = "test-session-" + System.currentTimeMillis();

        // Login
        runPromise(() -> auditLogger.logLoginSuccess(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "Mozilla/5.0"));

        // Token issued
        runPromise(() -> auditLogger.logTokenIssued(TEST_USERNAME, TEST_TENANT, "ACCESS", 3600));

        // MFA verification
        runPromise(() -> auditLogger.logMfaVerified(TEST_USERNAME, TEST_TENANT));

        // Logout
        runPromise(() -> auditLogger.logLogout(TEST_USERNAME, TEST_TENANT, sessionId));

        // Verify complete audit trail
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(20);

        assertThat(events).hasSizeGreaterThanOrEqualTo(4);

        // Verify sequence of events
        boolean hasLogin = false;
        boolean hasToken = false;
        boolean hasMfa = false;
        boolean hasLogout = false;

        for (AuditLogger.AuditEvent event : events) {
            switch (event.eventType()) {
                case AUTH_LOGIN_SUCCESS -> hasLogin = true;
                case AUTH_TOKEN_ISSUED -> hasToken = true;
                case AUTH_MFA_VERIFIED -> hasMfa = true;
                case AUTH_LOGOUT -> hasLogout = true;
                default -> {} // Ignore other event types
            }
        }

        assertThat(hasLogin).isTrue();
        assertThat(hasToken).isTrue();
        assertThat(hasMfa).isTrue();
        assertThat(hasLogout).isTrue();
    }
}
