/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service — Security Hardening Tests
 */
package com.ghatana.yappc.services.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.infrastructure.security.EncryptionService;
import com.ghatana.yappc.services.feature.FeatureFlagService;
import com.ghatana.yappc.framework.core.config.FeatureFlag;
import com.ghatana.yappc.framework.core.config.FeatureFlags;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security hardening tests for the YAPPC lifecycle service.
 *
 * <p>Covers:
 * <ul>
 *   <li>Encryption of sensitive data at rest (AES-256-GCM)</li> // GH-90000
 *   <li>Security audit event emission for key access patterns</li>
 *   <li>Tenant isolation enforcement</li>
 *   <li>Feature flag guard rail pathing</li>
 *   <li>Null and empty input handling at the security boundary</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Security hardening regression tests for YAPPC lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YAPPC Security Hardening [GH-90000]")
class SecurityHardeningTest extends EventloopTestBase {

    private EncryptionService    encryptionService;
    private SecurityAuditLogger  auditLogger;
    private FeatureFlagService   featureFlagService;
    private CapturingAuditDelegate auditDelegate;

    @BeforeEach
    void setUp() { // GH-90000
        String base64Key    = EncryptionService.generateKey(); // GH-90000
        encryptionService   = new EncryptionService(Base64.getDecoder().decode(base64Key)); // GH-90000
        auditDelegate       = new CapturingAuditDelegate(); // GH-90000
        auditLogger         = new SecurityAuditLogger(auditDelegate); // GH-90000
        featureFlagService  = new FeatureFlagService(); // GH-90000
        FeatureFlags.clearOverrides(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        FeatureFlags.clearOverrides(); // GH-90000
    }

    // ── Encryption at rest (SEC-001) ───────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("SEC-001: Encryption at rest [GH-90000]")
    class EncryptionAtRest {

        @Test
        @DisplayName("sensitive API key is stored as ciphertext, not plaintext [GH-90000]")
        void apiKeyStoredAsCiphertext() { // GH-90000
            String apiKey     = "sk-prod-supersecret12345";
            String ciphertext = encryptionService.encrypt(apiKey); // GH-90000

            assertThat(ciphertext).doesNotContain(apiKey); // GH-90000
            assertThat(ciphertext).doesNotContain("sk-prod [GH-90000]");
        }

        @Test
        @DisplayName("ciphertext is Base64 encoded (safe for column storage) [GH-90000]")
        void ciphertextIsBase64() { // GH-90000
            String ciphertext = encryptionService.encrypt("sensitive-value [GH-90000]");
            // Validate Base64 by decoding — no exception means it is valid Base64
            byte[] raw = Base64.getDecoder().decode(ciphertext); // GH-90000
            assertThat(raw).hasSizeGreaterThan(12); // IV (12) + at least 1 byte of data // GH-90000
        }

        @Test
        @DisplayName("round-trip of API key preserves original value [GH-90000]")
        void roundTripPreservesApiKey() { // GH-90000
            String apiKey   = "sk-prod-supersecret12345";
            String decrypted = encryptionService.decrypt(encryptionService.encrypt(apiKey)); // GH-90000
            assertThat(decrypted).isEqualTo(apiKey); // GH-90000
        }

        @Test
        @DisplayName("tampered ciphertext raises EncryptionException (no silent failure) [GH-90000]")
        void tamperedCiphertextRaisesException() { // GH-90000
            String ciphertext = encryptionService.encrypt("secret [GH-90000]");
            // Flip the last character to corrupt the GCM tag
            String tampered = ciphertext.substring(0, ciphertext.length() - 1) + "X"; // GH-90000
            assertThatThrownBy(() -> encryptionService.decrypt(tampered)) // GH-90000
                    .isInstanceOf(EncryptionService.EncryptionException.class); // GH-90000
        }
    }

    // ── Audit trail completeness (SEC-002) ─────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("SEC-002: Audit trail completeness [GH-90000]")
    class AuditTrail {

        @Test
        @DisplayName("every login attempt — success or failure — is audited [GH-90000]")
        void loginAttemptsAreAudited() { // GH-90000
            auditLogger.loginSuccess("alice", "tenant-1", "10.0.0.1"); // GH-90000
            auditLogger.loginFailure("eve",   "tenant-1", "bad-key"); // GH-90000

            assertThat(auditDelegate.events).hasSize(2); // GH-90000
            assertThat(auditDelegate.events.get(0)) // GH-90000
                    .containsEntry("event_type", "AUTH_LOGIN_SUCCESS"); // GH-90000
            assertThat(auditDelegate.events.get(1)) // GH-90000
                    .containsEntry("event_type", "AUTH_LOGIN_FAILURE"); // GH-90000
        }

        @Test
        @DisplayName("RBAC deny decision produces an AUTHZ_DENY audit event [GH-90000]")
        void rbacDenyIsAudited() { // GH-90000
            auditLogger.authorizationDecision(false, "viewer", "tenant-1", "admin-api", "delete"); // GH-90000

            assertThat(auditDelegate.events).hasSize(1); // GH-90000
            assertThat(auditDelegate.events.get(0)) // GH-90000
                    .containsEntry("event_type", "AUTHZ_DENY") // GH-90000
                    .containsEntry("outcome", "DENY"); // GH-90000
        }

        @Test
        @DisplayName("sensitive data read is audited with SENSITIVE_DATA_ACCESSED [GH-90000]")
        void sensitiveDataReadIsAudited() { // GH-90000
            auditLogger.sensitiveDataAccess("alice", "tenant-1", "encryption-key", "READ"); // GH-90000

            assertThat(auditDelegate.events.get(0)) // GH-90000
                    .containsEntry("event_type", "SENSITIVE_DATA_ACCESSED"); // GH-90000
        }
    }

    // ── Tenant isolation (SEC-003) ──────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("SEC-003: Tenant isolation [GH-90000]")
    class TenantIsolation {

        @Test
        @DisplayName("cross-tenant access attempt is audited as a TENANT_ISOLATION_VIOLATION [GH-90000]")
        void crossTenantViolationIsAudited() { // GH-90000
            auditLogger.tenantIsolationViolation("alice", "tenant-A", "tenant-B", "/projects/secret"); // GH-90000

            Map<String, Object> event = auditDelegate.events.get(0); // GH-90000
            assertThat(event) // GH-90000
                    .containsEntry("event_type", "TENANT_ISOLATION_VIOLATION") // GH-90000
                    .containsEntry("outcome", "DENY") // GH-90000
                    .containsEntry("tenant_id", "tenant-A") // GH-90000
                    .containsEntry("target_tenant", "tenant-B"); // GH-90000
        }
    }

    // ── Feature flag security guards (SEC-004) ──────────────────────────────── // GH-90000

    @Nested
    @DisplayName("SEC-004: Feature flag security controls [GH-90000]")
    class FeatureFlagSecurityControls {

        @Test
        @DisplayName("AEP_INTEGRATION flag defaults to disabled — prevents accidental exposure [GH-90000]")
        void aepIntegrationDefaultsToDisabled() { // GH-90000
            assertThat(featureFlagService.isDisabled(FeatureFlag.AEP_INTEGRATION)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("snapshot lists all flags — no hidden flags [GH-90000]")
        void snapshotListsAllFlags() { // GH-90000
            Map<FeatureFlag, Boolean> snapshot = featureFlagService.snapshot(); // GH-90000
            // All enum constants must be represented in the snapshot
            for (FeatureFlag flag : FeatureFlag.values()) { // GH-90000
                assertThat(snapshot).containsKey(flag); // GH-90000
            }
        }

        @Test
        @DisplayName("enableing a flag in test overrides does not affect production path when cleared [GH-90000]")
        void testOverrideDoesNotPersist() { // GH-90000
            FeatureFlags.override(FeatureFlag.AI_CANVAS_GENERATION, true); // GH-90000
            assertThat(featureFlagService.isEnabled(FeatureFlag.AI_CANVAS_GENERATION)).isTrue(); // GH-90000

            FeatureFlags.clearOverrides(); // GH-90000
            assertThat(featureFlagService.isDisabled(FeatureFlag.AI_CANVAS_GENERATION)).isTrue(); // GH-90000
        }
    }

    // ── Input validation at security boundary (SEC-005) ─────────────────────── // GH-90000

    @Nested
    @DisplayName("SEC-005: Input validation at security boundary [GH-90000]")
    class InputValidation {

        @Test
        @DisplayName("encrypting empty string does not crash [GH-90000]")
        void encryptingEmptyStringDoesNotCrash() { // GH-90000
            assertThat(encryptionService.encrypt(" [GH-90000]")).isNotBlank();
        }

        @Test
        @DisplayName("null login principal is replaced with 'anonymous' — no crash, no PII leak [GH-90000]")
        void nullPrincipalReplacedWithAnonymous() { // GH-90000
            auditLogger.loginFailure(null, "tenant-1", "bad-key"); // GH-90000

            assertThat(auditDelegate.events.get(0)) // GH-90000
                    .containsEntry("principal", "anonymous"); // GH-90000
        }

        @Test
        @DisplayName("encrypting null plaintext throws NullPointerException (fail-fast) [GH-90000]")
        void encryptingNullThrowsNPE() { // GH-90000
            assertThatThrownBy(() -> encryptionService.encrypt(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ── Test double ───────────────────────────────────────────────────────────

    private static final class CapturingAuditDelegate implements com.ghatana.audit.AuditLogger {
        final List<Map<String, Object>> events = new ArrayList<>(); // GH-90000

        @Override
        public Promise<Void> log(Map<String, Object> event) { // GH-90000
            events.add(Map.copyOf(event)); // GH-90000
            return Promise.complete(); // GH-90000
        }
    }
}
