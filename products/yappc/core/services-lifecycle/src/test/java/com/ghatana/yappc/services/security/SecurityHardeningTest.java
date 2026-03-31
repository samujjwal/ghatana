/*
 * Copyright (c) 2026 Ghatana Technologies
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
 *   <li>Encryption of sensitive data at rest (AES-256-GCM)</li>
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
@DisplayName("YAPPC Security Hardening")
class SecurityHardeningTest extends EventloopTestBase {

    private EncryptionService    encryptionService;
    private SecurityAuditLogger  auditLogger;
    private FeatureFlagService   featureFlagService;
    private CapturingAuditDelegate auditDelegate;

    @BeforeEach
    void setUp() {
        String base64Key    = EncryptionService.generateKey();
        encryptionService   = new EncryptionService(Base64.getDecoder().decode(base64Key));
        auditDelegate       = new CapturingAuditDelegate();
        auditLogger         = new SecurityAuditLogger(auditDelegate);
        featureFlagService  = new FeatureFlagService();
        FeatureFlags.clearOverrides();
    }

    @AfterEach
    void tearDown() {
        FeatureFlags.clearOverrides();
    }

    // ── Encryption at rest (SEC-001) ─────────────────────────────────────────

    @Nested
    @DisplayName("SEC-001: Encryption at rest")
    class EncryptionAtRest {

        @Test
        @DisplayName("sensitive API key is stored as ciphertext, not plaintext")
        void apiKeyStoredAsCiphertext() {
            String apiKey     = "sk-prod-supersecret12345";
            String ciphertext = encryptionService.encrypt(apiKey);

            assertThat(ciphertext).doesNotContain(apiKey);
            assertThat(ciphertext).doesNotContain("sk-prod");
        }

        @Test
        @DisplayName("ciphertext is Base64 encoded (safe for column storage)")
        void ciphertextIsBase64() {
            String ciphertext = encryptionService.encrypt("sensitive-value");
            // Validate Base64 by decoding — no exception means it is valid Base64
            byte[] raw = Base64.getDecoder().decode(ciphertext);
            assertThat(raw).hasSizeGreaterThan(12); // IV (12) + at least 1 byte of data
        }

        @Test
        @DisplayName("round-trip of API key preserves original value")
        void roundTripPreservesApiKey() {
            String apiKey   = "sk-prod-supersecret12345";
            String decrypted = encryptionService.decrypt(encryptionService.encrypt(apiKey));
            assertThat(decrypted).isEqualTo(apiKey);
        }

        @Test
        @DisplayName("tampered ciphertext raises EncryptionException (no silent failure)")
        void tamperedCiphertextRaisesException() {
            String ciphertext = encryptionService.encrypt("secret");
            // Flip the last character to corrupt the GCM tag
            String tampered = ciphertext.substring(0, ciphertext.length() - 1) + "X";
            assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                    .isInstanceOf(EncryptionService.EncryptionException.class);
        }
    }

    // ── Audit trail completeness (SEC-002) ───────────────────────────────────

    @Nested
    @DisplayName("SEC-002: Audit trail completeness")
    class AuditTrail {

        @Test
        @DisplayName("every login attempt — success or failure — is audited")
        void loginAttemptsAreAudited() {
            auditLogger.loginSuccess("alice", "tenant-1", "10.0.0.1");
            auditLogger.loginFailure("eve",   "tenant-1", "bad-key");

            assertThat(auditDelegate.events).hasSize(2);
            assertThat(auditDelegate.events.get(0))
                    .containsEntry("event_type", "AUTH_LOGIN_SUCCESS");
            assertThat(auditDelegate.events.get(1))
                    .containsEntry("event_type", "AUTH_LOGIN_FAILURE");
        }

        @Test
        @DisplayName("RBAC deny decision produces an AUTHZ_DENY audit event")
        void rbacDenyIsAudited() {
            auditLogger.authorizationDecision(false, "viewer", "tenant-1", "admin-api", "delete");

            assertThat(auditDelegate.events).hasSize(1);
            assertThat(auditDelegate.events.get(0))
                    .containsEntry("event_type", "AUTHZ_DENY")
                    .containsEntry("outcome", "DENY");
        }

        @Test
        @DisplayName("sensitive data read is audited with SENSITIVE_DATA_ACCESSED")
        void sensitiveDataReadIsAudited() {
            auditLogger.sensitiveDataAccess("alice", "tenant-1", "encryption-key", "READ");

            assertThat(auditDelegate.events.get(0))
                    .containsEntry("event_type", "SENSITIVE_DATA_ACCESSED");
        }
    }

    // ── Tenant isolation (SEC-003) ────────────────────────────────────────────

    @Nested
    @DisplayName("SEC-003: Tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("cross-tenant access attempt is audited as a TENANT_ISOLATION_VIOLATION")
        void crossTenantViolationIsAudited() {
            auditLogger.tenantIsolationViolation("alice", "tenant-A", "tenant-B", "/projects/secret");

            Map<String, Object> event = auditDelegate.events.get(0);
            assertThat(event)
                    .containsEntry("event_type", "TENANT_ISOLATION_VIOLATION")
                    .containsEntry("outcome", "DENY")
                    .containsEntry("tenant_id", "tenant-A")
                    .containsEntry("target_tenant", "tenant-B");
        }
    }

    // ── Feature flag security guards (SEC-004) ────────────────────────────────

    @Nested
    @DisplayName("SEC-004: Feature flag security controls")
    class FeatureFlagSecurityControls {

        @Test
        @DisplayName("AEP_INTEGRATION flag defaults to disabled — prevents accidental exposure")
        void aepIntegrationDefaultsToDisabled() {
            assertThat(featureFlagService.isDisabled(FeatureFlag.AEP_INTEGRATION)).isTrue();
        }

        @Test
        @DisplayName("snapshot lists all flags — no hidden flags")
        void snapshotListsAllFlags() {
            Map<FeatureFlag, Boolean> snapshot = featureFlagService.snapshot();
            // All enum constants must be represented in the snapshot
            for (FeatureFlag flag : FeatureFlag.values()) {
                assertThat(snapshot).containsKey(flag);
            }
        }

        @Test
        @DisplayName("enableing a flag in test overrides does not affect production path when cleared")
        void testOverrideDoesNotPersist() {
            FeatureFlags.override(FeatureFlag.AI_CANVAS_GENERATION, true);
            assertThat(featureFlagService.isEnabled(FeatureFlag.AI_CANVAS_GENERATION)).isTrue();

            FeatureFlags.clearOverrides();
            assertThat(featureFlagService.isDisabled(FeatureFlag.AI_CANVAS_GENERATION)).isTrue();
        }
    }

    // ── Input validation at security boundary (SEC-005) ───────────────────────

    @Nested
    @DisplayName("SEC-005: Input validation at security boundary")
    class InputValidation {

        @Test
        @DisplayName("encrypting empty string does not crash")
        void encryptingEmptyStringDoesNotCrash() {
            assertThat(encryptionService.encrypt("")).isNotBlank();
        }

        @Test
        @DisplayName("null login principal is replaced with 'anonymous' — no crash, no PII leak")
        void nullPrincipalReplacedWithAnonymous() {
            auditLogger.loginFailure(null, "tenant-1", "bad-key");

            assertThat(auditDelegate.events.get(0))
                    .containsEntry("principal", "anonymous");
        }

        @Test
        @DisplayName("encrypting null plaintext throws NullPointerException (fail-fast)")
        void encryptingNullThrowsNPE() {
            assertThatThrownBy(() -> encryptionService.encrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── Test double ───────────────────────────────────────────────────────────

    private static final class CapturingAuditDelegate implements com.ghatana.audit.AuditLogger {
        final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public Promise<Void> log(Map<String, Object> event) {
            events.add(Map.copyOf(event));
            return Promise.complete();
        }
    }
}
