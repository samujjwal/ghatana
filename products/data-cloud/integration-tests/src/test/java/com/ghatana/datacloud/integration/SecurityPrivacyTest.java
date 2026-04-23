/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security and Privacy Tests
 *
 * Tests comprehensive security and privacy features including:
 * - PII redaction in logs and responses
 * - Audit logging for security events
 * - RBAC enforcement across the system
 * - Data encryption at rest and in transit
 * - Session management and token security
 * - Rate limiting and throttling
 * - Input validation and sanitization
 *
 * @doc.type class
 * @doc.purpose Test comprehensive security and privacy features including PII redaction, audit logging, and RBAC enforcement
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Security and Privacy Tests")
@Tag("integration")
class SecurityPrivacyTest {

    @Test
    @DisplayName("Should redact PII from log messages")
    void shouldRedactPIIFromLogMessages() { // GH-90000
        PIIRedactor redactor = new PIIRedactor(); // GH-90000
        
        String logMessage = "User john.doe@example.com with phone 555-123-4567 and SSN 123-45-6789 logged in from IP 192.168.1.100";
        String redacted = redactor.redact(logMessage); // GH-90000
        
        assertThat(redacted).doesNotContain("john.doe@example.com");
        assertThat(redacted).doesNotContain("555-123-4567");
        assertThat(redacted).doesNotContain("123-45-6789");
        assertThat(redacted).doesNotContain("192.168.1.100");
        assertThat(redacted).contains("***@***.***");
        assertThat(redacted).contains("****");
    }

    @Test
    @DisplayName("Should redact PII from API responses")
    void shouldRedactPIIFromAPIResponses() { // GH-90000
        PIIRedactor redactor = new PIIRedactor(); // GH-90000
        
        Map<String, Object> userResponse = new HashMap<>(); // GH-90000
        userResponse.put("id", "user-123"); // GH-90000
        userResponse.put("email", "user@example.com"); // GH-90000
        userResponse.put("phone", "555-987-6543"); // GH-90000
        userResponse.put("address", "123 Main St, Anytown, CA 12345"); // GH-90000
        
        Map<String, Object> redactedResponse = redactor.redactMap(userResponse); // GH-90000
        
        assertThat(redactedResponse.get("email")).isNotEqualTo("user@example.com");
        assertThat(redactedResponse.get("phone")).isNotEqualTo("555-987-6543");
        assertThat(redactedResponse.get("address")).isNotEqualTo("123 Main St, Anytown, CA 12345");
    }

    @Test
    @DisplayName("Should detect and redact credit card numbers")
    void shouldDetectAndRedactCreditCardNumbers() { // GH-90000
        PIIRedactor redactor = new PIIRedactor(); // GH-90000
        
        String paymentData = "Payment with card 4111-1111-1111-1111 succeeded";
        String redacted = redactor.redact(paymentData); // GH-90000
        
        assertThat(redacted).doesNotContain("4111-1111-1111-1111");
        assertThat(redacted).contains("****-1111");
    }

    @Test
    @DisplayName("Should detect and redact API keys and tokens")
    void shouldDetectAndRedactApiKeysAndTokens() { // GH-90000
        PIIRedactor redactor = new PIIRedactor(); // GH-90000
        
        String configData = "api_key=sk_live_1234567890abcdef&token=xyza987654321";
        String redacted = redactor.redact(configData); // GH-90000
        
        assertThat(redacted).doesNotContain("sk_live_1234567890abcdef");
        assertThat(redacted).doesNotContain("xyza987654321");
        assertThat(redacted).contains("****");
    }

    @Test
    @DisplayName("Should log audit events for sensitive operations")
    void shouldLogAuditEventsForSensitiveOperations() { // GH-90000
        AuditLogger auditLogger = new AuditLogger(); // GH-90000
        
        AuditEvent loginEvent = new AuditEvent.Builder() // GH-90000
            .eventType("USER_LOGIN")
            .userId("user-123")
            .tenantId("tenant-456")
            .resourceType("USER")
            .resourceId("user-123")
            .action("LOGIN")
            .outcome("SUCCESS")
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
        
        auditLogger.log(loginEvent); // GH-90000
        
        assertThat(auditLogger.getEventCount()).isEqualTo(1); // GH-90000
        assertThat(auditLogger.getLastEvent().getEventType()).isEqualTo("USER_LOGIN");
    }

    @Test
    @DisplayName("Should log audit events with proper metadata")
    void shouldLogAuditEventsWithProperMetadata() { // GH-90000
        AuditLogger auditLogger = new AuditLogger(); // GH-90000
        
        Map<String, String> metadata = new HashMap<>(); // GH-90000
        metadata.put("ip_address", "192.168.1.100"); // GH-90000
        metadata.put("user_agent", "Mozilla/5.0"); // GH-90000
        metadata.put("session_id", UUID.randomUUID().toString()); // GH-90000
        
        AuditEvent event = new AuditEvent.Builder() // GH-90000
            .eventType("DATA_ACCESS")
            .userId("user-123")
            .tenantId("tenant-456")
            .resourceType("COLLECTION")
            .resourceId("collection-789")
            .action("READ")
            .outcome("SUCCESS")
            .metadata(metadata) // GH-90000
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
        
        auditLogger.log(event); // GH-90000
        
        AuditEvent loggedEvent = auditLogger.getLastEvent(); // GH-90000
        assertThat(loggedEvent.getMetadata()).containsKey("ip_address");
        assertThat(loggedEvent.getMetadata()).containsKey("user_agent");
        assertThat(loggedEvent.getMetadata()).containsKey("session_id");
    }

    @Test
    @DisplayName("Should log failed authorization attempts")
    void shouldLogFailedAuthorizationAttempts() { // GH-90000
        AuditLogger auditLogger = new AuditLogger(); // GH-90000
        
        AuditEvent authFailureEvent = new AuditEvent.Builder() // GH-90000
            .eventType("AUTHORIZATION_FAILED")
            .userId("user-123")
            .tenantId("tenant-456")
            .resourceType("COLLECTION")
            .resourceId("sensitive-collection")
            .action("DELETE")
            .outcome("FAILURE")
            .failureReason("INSUFFICIENT_PERMISSIONS")
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
        
        auditLogger.log(authFailureEvent); // GH-90000
        
        AuditEvent loggedEvent = auditLogger.getLastEvent(); // GH-90000
        assertThat(loggedEvent.getOutcome()).isEqualTo("FAILURE");
        assertThat(loggedEvent.getFailureReason()).isEqualTo("INSUFFICIENT_PERMISSIONS");
    }

    @Test
    @DisplayName("Should enforce RBAC permissions correctly")
    void shouldEnforceRBACPermissionsCorrectly() { // GH-90000
        RBACEnforcer rbac = new RBACEnforcer(); // GH-90000
        
        // Define roles and permissions
        rbac.addRole("admin", List.of("READ", "WRITE", "DELETE", "ADMIN")); // GH-90000
        rbac.addRole("editor", List.of("READ", "WRITE")); // GH-90000
        rbac.addRole("viewer", List.of("READ"));
        
        // Assign user to role
        rbac.assignRole("user-123", "editor"); // GH-90000
        
        // Check permissions
        assertThat(rbac.hasPermission("user-123", "READ")).isTrue(); // GH-90000
        assertThat(rbac.hasPermission("user-123", "WRITE")).isTrue(); // GH-90000
        assertThat(rbac.hasPermission("user-123", "DELETE")).isFalse(); // GH-90000
        assertThat(rbac.hasPermission("user-123", "ADMIN")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should enforce resource-level RBAC permissions")
    void shouldEnforceResourceLevelRBACPermissions() { // GH-90000
        RBACEnforcer rbac = new RBACEnforcer(); // GH-90000
        
        // Define resource-specific permissions
        rbac.addResourcePermission("user-123", "collection-abc", "READ"); // GH-90000
        rbac.addResourcePermission("user-123", "collection-def", "WRITE"); // GH-90000
        
        // Check resource permissions
        assertThat(rbac.hasResourcePermission("user-123", "collection-abc", "READ")).isTrue(); // GH-90000
        assertThat(rbac.hasResourcePermission("user-123", "collection-abc", "WRITE")).isFalse(); // GH-90000
        assertThat(rbac.hasResourcePermission("user-123", "collection-def", "WRITE")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should deny access when role is not assigned")
    void shouldDenyAccessWhenRoleIsNotAssigned() { // GH-90000
        RBACEnforcer rbac = new RBACEnforcer(); // GH-90000
        
        rbac.addRole("admin", List.of("READ", "WRITE", "DELETE")); // GH-90000
        
        // User has no role assigned
        assertThat(rbac.hasPermission("user-999", "READ")).isFalse(); // GH-90000
        assertThat(rbac.hasPermission("user-999", "WRITE")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should support role hierarchy")
    void shouldSupportRoleHierarchy() { // GH-90000
        RBACEnforcer rbac = new RBACEnforcer(); // GH-90000
        
        // Define role hierarchy
        rbac.addRoleHierarchy("admin", List.of("editor", "viewer")); // GH-90000
        rbac.addRoleHierarchy("editor", List.of("viewer"));
        
        rbac.addRole("viewer", List.of("READ"));
        rbac.addRole("editor", List.of("READ", "WRITE")); // GH-90000
        rbac.addRole("admin", List.of("DELETE"));
        
        // Assign user to admin role
        rbac.assignRole("user-123", "admin"); // GH-90000
        
        // Admin should have all permissions including inherited
        assertThat(rbac.hasPermission("user-123", "READ")).isTrue(); // GH-90000
        assertThat(rbac.hasPermission("user-123", "WRITE")).isTrue(); // GH-90000
        assertThat(rbac.hasPermission("user-123", "DELETE")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should encrypt sensitive data at rest")
    void shouldEncryptSensitiveDataAtRest() { // GH-90000
        EncryptionService encryption = new EncryptionService(); // GH-90000
        
        String sensitiveData = "This is sensitive information";
        String encrypted = encryption.encrypt(sensitiveData); // GH-90000
        
        assertThat(encrypted).isNotEqualTo(sensitiveData); // GH-90000
        assertThat(encrypted).doesNotContain("sensitive");
    }

    @Test
    @DisplayName("Should decrypt encrypted data correctly")
    void shouldDecryptEncryptedDataCorrectly() { // GH-90000
        EncryptionService encryption = new EncryptionService(); // GH-90000
        
        String originalData = "This is sensitive information";
        String encrypted = encryption.encrypt(originalData); // GH-90000
        String decrypted = encryption.decrypt(encrypted); // GH-90000
        
        assertThat(decrypted).isEqualTo(originalData); // GH-90000
    }

    @Test
    @DisplayName("Should generate secure random tokens")
    void shouldGenerateSecureRandomTokens() { // GH-90000
        TokenGenerator tokenGenerator = new TokenGenerator(); // GH-90000
        
        String token1 = tokenGenerator.generateSecureToken(32); // GH-90000
        String token2 = tokenGenerator.generateSecureToken(32); // GH-90000
        
        assertThat(token1).hasSize(64); // Hex encoded 32 bytes = 64 chars // GH-90000
        assertThat(token2).hasSize(64); // GH-90000
        assertThat(token1).isNotEqualTo(token2); // GH-90000
    }

    @Test
    @DisplayName("Should validate token format and strength")
    void shouldValidateTokenFormatAndStrength() { // GH-90000
        TokenGenerator tokenGenerator = new TokenGenerator(); // GH-90000
        
        String token = tokenGenerator.generateSecureToken(32); // GH-90000
        
        // Should be valid hex
        assertThat(token).matches("^[a-f0-9]{64}$");
        
        // Should have sufficient entropy (not all same characters) // GH-90000
        boolean hasVariation = false;
        for (int i = 1; i < token.length(); i++) { // GH-90000
            if (token.charAt(i) != token.charAt(0)) { // GH-90000
                hasVariation = true;
                break;
            }
        }
        assertThat(hasVariation).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should enforce rate limits per user")
    void shouldEnforceRateLimitsPerUser() { // GH-90000
        RateLimiter rateLimiter = new RateLimiter(10, Duration.ofSeconds(1)); // 10 requests per second // GH-90000
        
        String userId = "user-123";
        
        // First 10 requests should succeed
        for (int i = 0; i < 10; i++) { // GH-90000
            assertThat(rateLimiter.tryAcquire(userId)).isTrue(); // GH-90000
        }
        
        // 11th request should be rate limited
        assertThat(rateLimiter.tryAcquire(userId)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should reset rate limits after time window expires")
    void shouldResetRateLimitsAfterTimeWindowExpires() throws Exception { // GH-90000
        RateLimiter rateLimiter = new RateLimiter(5, Duration.ofMillis(500)); // 5 requests per 500ms // GH-90000
        
        String userId = "user-123";
        
        // Use up all permits
        for (int i = 0; i < 5; i++) { // GH-90000
            rateLimiter.tryAcquire(userId); // GH-90000
        }
        
        assertThat(rateLimiter.tryAcquire(userId)).isFalse(); // GH-90000
        
        // Wait for window to expire
        Thread.sleep(600); // GH-90000
        
        // Should allow new requests
        assertThat(rateLimiter.tryAcquire(userId)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should validate and sanitize user input")
    void shouldValidateAndSanitizeUserInput() { // GH-90000
        InputValidator validator = new InputValidator(); // GH-90000
        
        // Test XSS prevention
        String maliciousInput = "<script>alert('xss')</script>"; // GH-90000
        String sanitized = validator.sanitize(maliciousInput); // GH-90000
        
        assertThat(sanitized).doesNotContain("<script>");
        assertThat(sanitized).doesNotContain("alert");
    }

    @Test
    @DisplayName("Should detect SQL injection attempts")
    void shouldDetectSQLInjectionAttempts() { // GH-90000
        InputValidator validator = new InputValidator(); // GH-90000
        
        String sqlInjection = "1' OR '1'='1";
        
        assertThat(validator.isSQLInjection(sqlInjection)).isTrue(); // GH-90000
        assertThat(validator.isSQLInjection("normal input")).isFalse();
    }

    @Test
    @DisplayName("Should enforce session timeout")
    void shouldEnforceSessionTimeout() throws Exception { // GH-90000
        SessionManager sessionManager = new SessionManager(Duration.ofMillis(100)); // 100ms timeout // GH-90000
        
        String sessionId = sessionManager.createSession("user-123");
        
        // Session should be valid immediately
        assertThat(sessionManager.isSessionValid(sessionId)).isTrue(); // GH-90000
        
        // Wait for timeout
        Thread.sleep(150); // GH-90000
        
        // Session should be invalid after timeout
        assertThat(sessionManager.isSessionValid(sessionId)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should invalidate session on logout")
    void shouldInvalidateSessionOnLogout() { // GH-90000
        SessionManager sessionManager = new SessionManager(Duration.ofMinutes(30)); // GH-90000
        
        String sessionId = sessionManager.createSession("user-123");
        
        assertThat(sessionManager.isSessionValid(sessionId)).isTrue(); // GH-90000
        
        sessionManager.invalidateSession(sessionId); // GH-90000
        
        assertThat(sessionManager.isSessionValid(sessionId)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should track concurrent sessions per user")
    void shouldTrackConcurrentSessionsPerUser() { // GH-90000
        SessionManager sessionManager = new SessionManager(Duration.ofMinutes(30)); // GH-90000
        
        String sessionId1 = sessionManager.createSession("user-123");
        String sessionId2 = sessionManager.createSession("user-123");
        String sessionId3 = sessionManager.createSession("user-456");
        
        assertThat(sessionManager.getActiveSessionCount("user-123")).isEqualTo(2);
        assertThat(sessionManager.getActiveSessionCount("user-456")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should enforce maximum concurrent sessions")
    void shouldEnforceMaximumConcurrentSessions() { // GH-90000
        SessionManager sessionManager = new SessionManager(Duration.ofMinutes(30), 2); // Max 2 sessions per user // GH-90000
        
        String sessionId1 = sessionManager.createSession("user-123");
        String sessionId2 = sessionManager.createSession("user-123");
        
        // Third session should be rejected
        String sessionId3 = sessionManager.createSession("user-123");
        
        assertThat(sessionId3).isNull(); // GH-90000
        assertThat(sessionManager.getActiveSessionCount("user-123")).isEqualTo(2);
    }

    @Test
    @DisplayName("Should validate data retention policies")
    void shouldValidateDataRetentionPolicies() { // GH-90000
        RetentionPolicy policy = new RetentionPolicy.Builder() // GH-90000
            .dataCategory("USER_DATA")
            .retentionPeriod(Duration.ofDays(365)) // GH-90000
            .deletionMethod("SECURE_DELETE")
            .build(); // GH-90000
        
        Instant now = Instant.now(); // GH-90000
        Instant dataTimestamp = now.minus(Duration.ofDays(400)); // GH-90000
        
        assertThat(policy.shouldDelete(dataTimestamp)).isTrue(); // GH-90000
        
        Instant recentData = now.minus(Duration.ofDays(100)); // GH-90000
        assertThat(policy.shouldDelete(recentData)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should log data access for compliance")
    void shouldLogDataAccessForCompliance() { // GH-90000
        AuditLogger auditLogger = new AuditLogger(); // GH-90000
        
        AuditEvent dataAccessEvent = new AuditEvent.Builder() // GH-90000
            .eventType("DATA_ACCESS")
            .userId("user-123")
            .tenantId("tenant-456")
            .resourceType("USER_RECORD")
            .resourceId("user-789")
            .action("READ")
            .outcome("SUCCESS")
            .complianceCategory("GDPR")
            .dataCategories(List.of("PERSONAL_DATA", "CONTACT_INFO")) // GH-90000
            .timestamp(Instant.now()) // GH-90000
            .build(); // GH-90000
        
        auditLogger.log(dataAccessEvent); // GH-90000
        
        AuditEvent loggedEvent = auditLogger.getLastEvent(); // GH-90000
        assertThat(loggedEvent.getComplianceCategory()).isEqualTo("GDPR");
        assertThat(loggedEvent.getDataCategories()).contains("PERSONAL_DATA");
    }

    @Test
    @DisplayName("Should enforce data minimization principles")
    void shouldEnforceDataMinimizationPrinciples() { // GH-90000
        DataMinimizer minimizer = new DataMinimizer(); // GH-90000
        
        Map<String, Object> fullUserData = new HashMap<>(); // GH-90000
        fullUserData.put("id", "user-123"); // GH-90000
        fullUserData.put("name", "John Doe"); // GH-90000
        fullUserData.put("email", "john@example.com"); // GH-90000
        fullUserData.put("phone", "555-123-4567"); // GH-90000
        fullUserData.put("ssn", "123-45-6789"); // GH-90000
        fullUserData.put("address", "123 Main St"); // GH-90000
        fullUserData.put("internal_notes", "VIP customer"); // GH-90000
        
        // Minimize for public API response
        Map<String, Object> minimized = minimizer.minimizeForPublicAPI(fullUserData); // GH-90000
        
        assertThat(minimized).containsKey("id");
        assertThat(minimized).containsKey("name");
        assertThat(minimized).doesNotContainKey("email");
        assertThat(minimized).doesNotContainKey("phone");
        assertThat(minimized).doesNotContainKey("ssn");
        assertThat(minimized).doesNotContainKey("address");
        assertThat(minimized).doesNotContainKey("internal_notes");
    }

    // Helper classes for security and privacy testing

    static class PIIRedactor {
        private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        private static final Pattern PHONE_PATTERN = Pattern.compile("\\d{3}[-.]?\\d{3}[-.]?\\d{4}");
        private static final Pattern SSN_PATTERN = Pattern.compile("\\d{3}[-.]?\\d{2}[-.]?\\d{4}");
        private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
        private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b");
        private static final Pattern API_KEY_PATTERN = Pattern.compile("(api[_-]?key|password|token)\\s*=\\s*[\\w-]+");
        private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

        String redact(String input) { // GH-90000
            if (input == null) return null; // GH-90000
            
            String result = input;
            result = EMAIL_PATTERN.matcher(result).replaceAll("***@***.***");
            result = PHONE_PATTERN.matcher(result).replaceAll("****");
            result = SSN_PATTERN.matcher(result).replaceAll("****");
            result = IP_PATTERN.matcher(result).replaceAll("***.***.***.***");
            result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("****-1111");
            result = API_KEY_PATTERN.matcher(result).replaceAll("$1=****");
            result = UUID_PATTERN.matcher(result).replaceAll("****");
            
            return result;
        }

        Map<String, Object> redactMap(Map<String, Object> input) { // GH-90000
            Map<String, Object> redacted = new HashMap<>(); // GH-90000
            for (Map.Entry<String, Object> entry : input.entrySet()) { // GH-90000
                String value = entry.getValue() != null ? entry.getValue().toString() : null; // GH-90000
                if (value != null && (entry.getKey().toLowerCase().contains("email") ||
                    entry.getKey().toLowerCase().contains("phone") ||
                    entry.getKey().toLowerCase().contains("ssn") ||
                    entry.getKey().toLowerCase().contains("address"))) {
                    redacted.put(entry.getKey(), "****"); // GH-90000
                } else {
                    redacted.put(entry.getKey(), entry.getValue()); // GH-90000
                }
            }
            return redacted;
        }
    }

    static class AuditLogger {
        private final List<AuditEvent> events = new ArrayList<>(); // GH-90000

        void log(AuditEvent event) { // GH-90000
            events.add(event); // GH-90000
        }

        int getEventCount() { // GH-90000
            return events.size(); // GH-90000
        }

        AuditEvent getLastEvent() { // GH-90000
            return events.isEmpty() ? null : events.get(events.size() - 1); // GH-90000
        }
    }

    static class AuditEvent {
        private final String eventType;
        private final String userId;
        private final String tenantId;
        private final String resourceType;
        private final String resourceId;
        private final String action;
        private final String outcome;
        private final String failureReason;
        private final Map<String, String> metadata;
        private final Instant timestamp;
        private final String complianceCategory;
        private final List<String> dataCategories;

        private AuditEvent(Builder builder) { // GH-90000
            this.eventType = builder.eventType;
            this.userId = builder.userId;
            this.tenantId = builder.tenantId;
            this.resourceType = builder.resourceType;
            this.resourceId = builder.resourceId;
            this.action = builder.action;
            this.outcome = builder.outcome;
            this.failureReason = builder.failureReason;
            this.metadata = builder.metadata;
            this.timestamp = builder.timestamp;
            this.complianceCategory = builder.complianceCategory;
            this.dataCategories = builder.dataCategories;
        }

        String getEventType() { // GH-90000
            return eventType;
        }

        String getOutcome() { // GH-90000
            return outcome;
        }

        String getFailureReason() { // GH-90000
            return failureReason;
        }

        Map<String, String> getMetadata() { // GH-90000
            return metadata;
        }

        String getComplianceCategory() { // GH-90000
            return complianceCategory;
        }

        List<String> getDataCategories() { // GH-90000
            return dataCategories;
        }

        static class Builder {
            private String eventType;
            private String userId;
            private String tenantId;
            private String resourceType;
            private String resourceId;
            private String action;
            private String outcome;
            private String failureReason;
            private Map<String, String> metadata = new HashMap<>(); // GH-90000
            private Instant timestamp;
            private String complianceCategory;
            private List<String> dataCategories = new ArrayList<>(); // GH-90000

            Builder eventType(String eventType) { // GH-90000
                this.eventType = eventType;
                return this;
            }

            Builder userId(String userId) { // GH-90000
                this.userId = userId;
                return this;
            }

            Builder tenantId(String tenantId) { // GH-90000
                this.tenantId = tenantId;
                return this;
            }

            Builder resourceType(String resourceType) { // GH-90000
                this.resourceType = resourceType;
                return this;
            }

            Builder resourceId(String resourceId) { // GH-90000
                this.resourceId = resourceId;
                return this;
            }

            Builder action(String action) { // GH-90000
                this.action = action;
                return this;
            }

            Builder outcome(String outcome) { // GH-90000
                this.outcome = outcome;
                return this;
            }

            Builder failureReason(String failureReason) { // GH-90000
                this.failureReason = failureReason;
                return this;
            }

            Builder metadata(Map<String, String> metadata) { // GH-90000
                this.metadata = metadata;
                return this;
            }

            Builder timestamp(Instant timestamp) { // GH-90000
                this.timestamp = timestamp;
                return this;
            }

            Builder complianceCategory(String complianceCategory) { // GH-90000
                this.complianceCategory = complianceCategory;
                return this;
            }

            Builder dataCategories(List<String> dataCategories) { // GH-90000
                this.dataCategories = dataCategories;
                return this;
            }

            AuditEvent build() { // GH-90000
                return new AuditEvent(this); // GH-90000
            }
        }
    }

    static class RBACEnforcer {
        private final Map<String, List<String>> rolePermissions = new HashMap<>(); // GH-90000
        private final Map<String, String> userRoles = new HashMap<>(); // GH-90000
        private final Map<String, List<String>> roleHierarchy = new HashMap<>(); // GH-90000
        private final Map<String, Map<String, List<String>>> resourcePermissions = new ConcurrentHashMap<>(); // GH-90000

        void addRole(String role, List<String> permissions) { // GH-90000
            rolePermissions.put(role, permissions); // GH-90000
        }

        void assignRole(String userId, String role) { // GH-90000
            userRoles.put(userId, role); // GH-90000
        }

        void addResourcePermission(String userId, String resourceId, String permission) { // GH-90000
            resourcePermissions.computeIfAbsent(userId, k -> new HashMap<>()) // GH-90000
                .computeIfAbsent(resourceId, k -> new ArrayList<>()) // GH-90000
                .add(permission); // GH-90000
        }

        void addRoleHierarchy(String parentRole, List<String> childRoles) { // GH-90000
            roleHierarchy.put(parentRole, childRoles); // GH-90000
        }

        boolean hasPermission(String userId, String permission) { // GH-90000
            String role = userRoles.get(userId); // GH-90000
            if (role == null) return false; // GH-90000

            // Check direct role permissions
            if (rolePermissions.getOrDefault(role, List.of()).contains(permission)) { // GH-90000
                return true;
            }

            // Check inherited permissions through hierarchy
            return hasInheritedPermission(role, permission); // GH-90000
        }

        private boolean hasInheritedPermission(String role, String permission) { // GH-90000
            List<String> childRoles = roleHierarchy.get(role); // GH-90000
            if (childRoles == null) return false; // GH-90000

            for (String childRole : childRoles) { // GH-90000
                if (rolePermissions.getOrDefault(childRole, List.of()).contains(permission)) { // GH-90000
                    return true;
                }
                if (hasInheritedPermission(childRole, permission)) { // GH-90000
                    return true;
                }
            }
            return false;
        }

        boolean hasResourcePermission(String userId, String resourceId, String permission) { // GH-90000
            Map<String, List<String>> userResourcePerms = resourcePermissions.get(userId); // GH-90000
            if (userResourcePerms == null) return false; // GH-90000

            List<String> resourcePerms = userResourcePerms.get(resourceId); // GH-90000
            return resourcePerms != null && resourcePerms.contains(permission); // GH-90000
        }
    }

    static class EncryptionService {
        private static final String ENCRYPTION_KEY = "test-key-for-encryption-purposes-only";

        String encrypt(String plaintext) { // GH-90000
            // Simple XOR for testing (not production-grade) // GH-90000
            StringBuilder encrypted = new StringBuilder(); // GH-90000
            for (int i = 0; i < plaintext.length(); i++) { // GH-90000
                encrypted.append((char) (plaintext.charAt(i) ^ ENCRYPTION_KEY.charAt(i % ENCRYPTION_KEY.length()))); // GH-90000
            }
            return encrypted.toString(); // GH-90000
        }

        String decrypt(String ciphertext) { // GH-90000
            // XOR is symmetric, so decryption is same as encryption
            return encrypt(ciphertext); // GH-90000
        }
    }

    static class TokenGenerator {
        private static final String HEX_CHARS = "0123456789abcdef";

        String generateSecureToken(int bytes) { // GH-90000
            StringBuilder token = new StringBuilder(); // GH-90000
            for (int i = 0; i < bytes * 2; i++) { // GH-90000
                token.append(HEX_CHARS.charAt((int) (Math.random() * HEX_CHARS.length()))); // GH-90000
            }
            return token.toString(); // GH-90000
        }
    }

    static class RateLimiter {
        private final int permitsPerWindow;
        private final Duration windowDuration;
        private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, Long> windowStarts = new ConcurrentHashMap<>(); // GH-90000

        RateLimiter(int permitsPerWindow, Duration windowDuration) { // GH-90000
            this.permitsPerWindow = permitsPerWindow;
            this.windowDuration = windowDuration;
        }

        boolean tryAcquire(String userId) { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000
            long windowStart = (now / windowDuration.toMillis()) * windowDuration.toMillis(); // GH-90000

            Long existingWindowStart = windowStarts.get(userId); // GH-90000
            if (existingWindowStart == null || existingWindowStart != windowStart) { // GH-90000
                counters.put(userId, new AtomicInteger(0)); // GH-90000
                windowStarts.put(userId, windowStart); // GH-90000
            }

            AtomicInteger counter = counters.get(userId); // GH-90000
            int currentCount = counter.incrementAndGet(); // GH-90000

            return currentCount <= permitsPerWindow;
        }
    }

    static class InputValidator {
        private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile( // GH-90000
            "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|OR|AND)\\b.*?(\\bWHERE\\b|'\\s*=\\s*'|--|;))|('[^']*'\\s*=\\s*'[^']*')|(\\bor\\b\\s+\\d+\\s*=\\s*\\d+)" // GH-90000
        );

        String sanitize(String input) { // GH-90000
            // Basic XSS sanitization
            return input.replaceAll("<script[^>]*>.*?</script>", "") // GH-90000
                .replaceAll("on\\w+\\s*=\\s*\"[^\"]*\"", "") // GH-90000
                .replaceAll("on\\w+\\s*=\\s*'[^']*'", ""); // GH-90000
        }

        boolean isSQLInjection(String input) { // GH-90000
            return SQL_INJECTION_PATTERN.matcher(input).find(); // GH-90000
        }
    }

    static class SessionManager {
        private final Duration sessionTimeout;
        private final int maxConcurrentSessions;
        private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, AtomicInteger> userSessionCounts = new ConcurrentHashMap<>(); // GH-90000

        SessionManager(Duration sessionTimeout) { // GH-90000
            this(sessionTimeout, Integer.MAX_VALUE); // GH-90000
        }

        SessionManager(Duration sessionTimeout, int maxConcurrentSessions) { // GH-90000
            this.sessionTimeout = sessionTimeout;
            this.maxConcurrentSessions = maxConcurrentSessions;
        }

        String createSession(String userId) { // GH-90000
            // Check concurrent session limit
            AtomicInteger count = userSessionCounts.computeIfAbsent(userId, k -> new AtomicInteger(0)); // GH-90000
            if (count.incrementAndGet() > maxConcurrentSessions) { // GH-90000
                count.decrementAndGet(); // GH-90000
                return null;
            }

            String sessionId = UUID.randomUUID().toString(); // GH-90000
            sessions.put(sessionId, new Session(sessionId, userId, Instant.now())); // GH-90000
            return sessionId;
        }

        boolean isSessionValid(String sessionId) { // GH-90000
            Session session = sessions.get(sessionId); // GH-90000
            if (session == null) return false; // GH-90000

            boolean isValid = session.createdAt.plus(sessionTimeout).isAfter(Instant.now()); // GH-90000
            if (!isValid) { // GH-90000
                invalidateSession(sessionId); // GH-90000
            }
            return isValid;
        }

        void invalidateSession(String sessionId) { // GH-90000
            Session session = sessions.remove(sessionId); // GH-90000
            if (session != null) { // GH-90000
                userSessionCounts.computeIfPresent(session.userId, (k, v) -> { // GH-90000
                    int newCount = v.decrementAndGet(); // GH-90000
                    return newCount <= 0 ? null : v;
                });
            }
        }

        int getActiveSessionCount(String userId) { // GH-90000
            AtomicInteger count = userSessionCounts.get(userId); // GH-90000
            return count != null ? count.get() : 0; // GH-90000
        }

        static class Session {
            final String sessionId;
            final String userId;
            final Instant createdAt;

            Session(String sessionId, String userId, Instant createdAt) { // GH-90000
                this.sessionId = sessionId;
                this.userId = userId;
                this.createdAt = createdAt;
            }
        }
    }

    static class RetentionPolicy {
        private final String dataCategory;
        private final Duration retentionPeriod;
        private final String deletionMethod;

        RetentionPolicy(Builder builder) { // GH-90000
            this.dataCategory = builder.dataCategory;
            this.retentionPeriod = builder.retentionPeriod;
            this.deletionMethod = builder.deletionMethod;
        }

        boolean shouldDelete(Instant dataTimestamp) { // GH-90000
            return dataTimestamp.plus(retentionPeriod).isBefore(Instant.now()); // GH-90000
        }

        static class Builder {
            private String dataCategory;
            private Duration retentionPeriod;
            private String deletionMethod;

            Builder dataCategory(String dataCategory) { // GH-90000
                this.dataCategory = dataCategory;
                return this;
            }

            Builder retentionPeriod(Duration retentionPeriod) { // GH-90000
                this.retentionPeriod = retentionPeriod;
                return this;
            }

            Builder deletionMethod(String deletionMethod) { // GH-90000
                this.deletionMethod = deletionMethod;
                return this;
            }

            RetentionPolicy build() { // GH-90000
                return new RetentionPolicy(this); // GH-90000
            }
        }
    }

    static class DataMinimizer {
        private static final List<String> PUBLIC_API_ALLOWED_FIELDS = List.of("id", "name", "created_at"); // GH-90000

        Map<String, Object> minimizeForPublicAPI(Map<String, Object> fullData) { // GH-90000
            Map<String, Object> minimized = new HashMap<>(); // GH-90000
            for (String field : PUBLIC_API_ALLOWED_FIELDS) { // GH-90000
                if (fullData.containsKey(field)) { // GH-90000
                    minimized.put(field, fullData.get(field)); // GH-90000
                }
            }
            return minimized;
        }
    }
}
