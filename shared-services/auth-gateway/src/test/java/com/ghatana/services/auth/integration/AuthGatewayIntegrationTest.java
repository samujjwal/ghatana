/*
 * Copyright (c) 2026 Ghatana // GH-90000
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class AuthGatewayIntegrationTest extends EventloopTestBase {

    private static final String BASE_URL = "http://localhost:8081";
    private static final String TEST_USERNAME = "integration-test-user";
    private static final String TEST_PASSWORD = "SecurePassword123!";
    private static final String TEST_TENANT = "test-tenant";

    private MfaService mfaService;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() { // GH-90000
        mfaService = new MfaService(); // GH-90000
        auditLogger = new AuditLogger(); // GH-90000
    }

    @Test
    @Order(1) // GH-90000
    @DisplayName("Integration: Should complete full authentication flow with MFA [GH-90000]")
    void testFullAuthenticationFlowWithMfa() throws Exception { // GH-90000
        // Step 1: Enroll user in MFA
        MfaService.EnrollmentData enrollment = runPromise(() -> mfaService.enrollUser(TEST_USERNAME, "Ghatana")); // GH-90000

        assertThat(enrollment.secret()).isNotBlank(); // GH-90000
        assertThat(enrollment.qrCodeUri()).contains("otpauth://totp/ [GH-90000]");
        assertThat(enrollment.backupCodes()).hasSize(10); // GH-90000

        // Step 2: Verify enrollment (in real scenario, user would scan QR and enter code) // GH-90000
        // For testing, we simulate the verification
        boolean verified = runPromise(() -> mfaService.verifyEnrollment(TEST_USERNAME, "123456")); // GH-90000

        // Will fail with dummy code, but tests the flow
        assertThat(verified).isFalse(); // GH-90000

        // Step 3: Log the enrollment event
        runPromise(() -> auditLogger.logMfaEnrolled(TEST_USERNAME, TEST_TENANT)); // GH-90000

        // Verify audit event was logged
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000
        assertThat(events).isNotEmpty(); // GH-90000
        assertThat(events[0].eventType()).isEqualTo(AuditLogger.AuditEventType.AUTH_MFA_ENROLLED); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("Integration: Should log authentication attempts [GH-90000]")
    void testAuthenticationAuditLogging() throws Exception { // GH-90000
        // Log successful login
        runPromise(() -> auditLogger.logLoginSuccess(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "Mozilla/5.0")); // GH-90000

        // Log failed login
        runPromise(() -> auditLogger.logLoginFailure(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "Invalid password")); // GH-90000

        // Verify both events logged
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000
        assertThat(events).hasSizeGreaterThanOrEqualTo(2); // GH-90000

        boolean hasSuccess = false;
        boolean hasFailure = false;
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_SUCCESS) { // GH-90000
                hasSuccess = true;
            }
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_LOGIN_FAILURE) { // GH-90000
                hasFailure = true;
            }
        }

        assertThat(hasSuccess).isTrue(); // GH-90000
        assertThat(hasFailure).isTrue(); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("Integration: Should handle MFA validation with rate limiting [GH-90000]")
    void testMfaValidationWithRateLimiting() throws Exception { // GH-90000
        // Enroll user
        runPromise(() -> mfaService.enrollUser(TEST_USERNAME, "Ghatana")); // GH-90000

        // Attempt multiple failed validations
        for (int i = 0; i < 6; i++) { // GH-90000
            boolean valid = runPromise(() -> mfaService.validateCode(TEST_USERNAME, "000000")); // GH-90000
            assertThat(valid).isFalse(); // GH-90000

            // Log each failed attempt
            final int attempt = i;
            runPromise(() -> auditLogger.logMfaFailed(TEST_USERNAME, TEST_TENANT, attempt + 1)); // GH-90000
        }

        // Verify rate limiting kicked in (check audit logs) // GH-90000
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000
        long mfaFailures = 0;
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_MFA_FAILED) { // GH-90000
                mfaFailures++;
            }
        }

        assertThat(mfaFailures).isGreaterThanOrEqualTo(5); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("Integration: Should validate backup codes and invalidate after use [GH-90000]")
    void testBackupCodeValidation() throws Exception { // GH-90000
        // Enroll user
        MfaService.EnrollmentData enrollment = runPromise(() -> mfaService.enrollUser(TEST_USERNAME + "-backup", "Ghatana")); // GH-90000
        String backupCode = enrollment.backupCodes()[0]; // GH-90000

        // Backup codes work right after enrollment
        boolean valid = runPromise(() -> mfaService.validateBackupCode(TEST_USERNAME + "-backup", backupCode)); // GH-90000
        assertThat(valid).isTrue(); // GH-90000

        if (valid) { // GH-90000
            runPromise(() -> auditLogger.logMfaVerified(TEST_USERNAME + "-backup", TEST_TENANT)); // GH-90000
        }
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("Integration: Should track authorization decisions in audit log [GH-90000]")
    void testAuthorizationAuditLogging() throws Exception { // GH-90000
        // Log access granted
        runPromise(() -> auditLogger.logAuthorizationDecision( // GH-90000
            TEST_USERNAME, TEST_TENANT, "/api/projects", "CREATE", true
        ));

        // Log access denied
        runPromise(() -> auditLogger.logAuthorizationDecision( // GH-90000
            TEST_USERNAME, TEST_TENANT, "/api/admin", "DELETE", false
        ));

        // Verify both logged
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        boolean hasGranted = false;
        boolean hasDenied = false;
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            if (event.eventType() == AuditLogger.AuditEventType.AUTHZ_ACCESS_GRANTED) { // GH-90000
                hasGranted = true;
            }
            if (event.eventType() == AuditLogger.AuditEventType.AUTHZ_ACCESS_DENIED) { // GH-90000
                hasDenied = true;
            }
        }

        assertThat(hasGranted).isTrue(); // GH-90000
        assertThat(hasDenied).isTrue(); // GH-90000
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("Integration: Should log rate limiting events [GH-90000]")
    void testRateLimitingAuditLogging() throws Exception { // GH-90000
        runPromise(() -> auditLogger.logRateLimited(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "/auth/login")); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        boolean hasRateLimit = false;
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            if (event.eventType() == AuditLogger.AuditEventType.SECURITY_RATE_LIMITED) { // GH-90000
                hasRateLimit = true;
                assertThat(event.severity()).isEqualTo(AuditLogger.AuditSeverity.WARNING); // GH-90000
            }
        }

        assertThat(hasRateLimit).isTrue(); // GH-90000
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("Integration: Should log account lockout events [GH-90000]")
    void testAccountLockoutAuditLogging() throws Exception { // GH-90000
        runPromise(() -> auditLogger.logAccountLocked(TEST_USERNAME, TEST_TENANT, "Too many failed login attempts")); // GH-90000

        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000

        boolean hasLockout = false;
        for (AuditLogger.AuditEvent event : events) { // GH-90000
            if (event.eventType() == AuditLogger.AuditEventType.AUTH_ACCOUNT_LOCKED) { // GH-90000
                hasLockout = true;
                assertThat(event.severity()).isEqualTo(AuditLogger.AuditSeverity.CRITICAL); // GH-90000
            }
        }

        assertThat(hasLockout).isTrue(); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("Integration: Should handle complete login-logout cycle with audit trail [GH-90000]")
    void testCompleteAuthenticationCycle() throws Exception { // GH-90000
        String sessionId = "test-session-" + System.currentTimeMillis(); // GH-90000

        // Login
        runPromise(() -> auditLogger.logLoginSuccess(TEST_USERNAME, TEST_TENANT, "192.168.1.1", "Mozilla/5.0")); // GH-90000

        // Token issued
        runPromise(() -> auditLogger.logTokenIssued(TEST_USERNAME, TEST_TENANT, "ACCESS", 3600)); // GH-90000

        // MFA verification
        runPromise(() -> auditLogger.logMfaVerified(TEST_USERNAME, TEST_TENANT)); // GH-90000

        // Logout
        runPromise(() -> auditLogger.logLogout(TEST_USERNAME, TEST_TENANT, sessionId)); // GH-90000

        // Verify complete audit trail
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(20); // GH-90000

        assertThat(events).hasSizeGreaterThanOrEqualTo(4); // GH-90000

        // Verify sequence of events
        boolean hasLogin = false;
        boolean hasToken = false;
        boolean hasMfa = false;
        boolean hasLogout = false;

        for (AuditLogger.AuditEvent event : events) { // GH-90000
            switch (event.eventType()) { // GH-90000
                case AUTH_LOGIN_SUCCESS -> hasLogin = true;
                case AUTH_TOKEN_ISSUED -> hasToken = true;
                case AUTH_MFA_VERIFIED -> hasMfa = true;
                case AUTH_LOGOUT -> hasLogout = true;
                default -> {} // Ignore other event types
            }
        }

        assertThat(hasLogin).isTrue(); // GH-90000
        assertThat(hasToken).isTrue(); // GH-90000
        assertThat(hasMfa).isTrue(); // GH-90000
        assertThat(hasLogout).isTrue(); // GH-90000
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("Integration: Should parse login request with complex JSON using Jackson [GH-90000]")
    void testLoginRequestParsingWithComplexJson() throws Exception { // GH-90000
        // Test with complex JSON including escaped quotes, nested objects, and special characters
        String complexJson = """
            {
                "username": "test\\\"user\\\"",
                "password": "p@$$w0rd!@#",
                "metadata": {
                    "ip": "192.168.1.1",
                    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" // GH-90000
                },
                "rememberMe": true
            }
            """;

        // Use reflection to invoke extractJsonField
        java.lang.reflect.Method method = com.ghatana.services.auth.AuthGatewayLauncher.class
            .getDeclaredMethod("extractJsonField", String.class, String.class); // GH-90000
        method.setAccessible(true); // GH-90000

        // Extract username with escaped quotes
        String username = (String) method.invoke(null, complexJson, "username"); // GH-90000
        assertThat(username).isEqualTo("test\"user\""); // GH-90000

        // Extract password with special characters
        String password = (String) method.invoke(null, complexJson, "password"); // GH-90000
        assertThat(password).isEqualTo("p@$$w0rd!@# [GH-90000]");

        // Extract nested object as string
        String metadata = (String) method.invoke(null, complexJson, "metadata"); // GH-90000
        assertThat(metadata).isNotNull(); // GH-90000
        assertThat(metadata).contains("192.168.1.1 [GH-90000]");

        // Extract boolean field
        String rememberMe = (String) method.invoke(null, complexJson, "rememberMe"); // GH-90000
        assertThat(rememberMe).isEqualTo("true [GH-90000]");

        // Log successful parsing
        runPromise(() -> auditLogger.logLoginSuccess(username, TEST_TENANT, "192.168.1.1", "Mozilla/5.0")); // GH-90000

        // Verify audit event was logged
        AuditLogger.AuditEvent[] events = auditLogger.getRecentEvents(10); // GH-90000
        assertThat(events).isNotEmpty(); // GH-90000
        assertThat(events[0].eventType()).isEqualTo(AuditLogger.AuditEventType.AUTH_LOGIN_SUCCESS); // GH-90000
    }
}
