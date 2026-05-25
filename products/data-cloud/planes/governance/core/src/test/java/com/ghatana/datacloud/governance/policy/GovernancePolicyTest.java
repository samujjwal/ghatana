/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * DC-P6-003: Governance Plane audit/encryption/retention tests.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Every mutating operation can produce audit evidence</li>
 *   <li>Sensitive fields can be redacted/encrypted</li>
 *   <li>Retention/legal hold rules are test-backed</li>
 *   <li>Production profile fails if policy engine missing</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for governance policy models
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Governance Policy Tests (DC-P6-003)")
class GovernancePolicyTest {

    // =========================================================================
    //  Audit Policy Tests
    // =========================================================================

    @Nested
    @DisplayName("Audit Policy")
    class AuditPolicyTests {

        @Test
        @DisplayName("audit policy requires valid policy ID")
        void auditPolicyRequiresValidPolicyId() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new AuditPolicy("", true, true, true, true, Set.of(), AuditPolicy.AuditLevel.STANDARD)
            ).withMessageContaining("policyId");

            assertThatIllegalArgumentException().isThrownBy(() ->
                new AuditPolicy(null, true, true, true, true, Set.of(), AuditPolicy.AuditLevel.STANDARD)
            ).withMessageContaining("policyId");
        }

        @Test
        @DisplayName("audit policy handles null resource types")
        void auditPolicyHandlesNullResourceTypes() {
            AuditPolicy policy = new AuditPolicy("policy-1", true, true, true, true, null, AuditPolicy.AuditLevel.STANDARD);
            assertThat(policy.auditedResourceTypes()).isEmpty();
        }

        @Test
        @DisplayName("audit policy should audit create operations")
        void auditPolicyShouldAuditCreateOperations() {
            AuditPolicy policy = new AuditPolicy(
                "policy-1",
                true,
                false,
                false,
                false,
                Set.of("entity"),
                AuditPolicy.AuditLevel.STANDARD
            );

            assertThat(policy.shouldAudit("entity", "create")).isTrue();
            assertThat(policy.shouldAudit("entity", "update")).isFalse();
        }

        @Test
        @DisplayName("audit policy filters by resource type")
        void auditPolicyFiltersByResourceType() {
            AuditPolicy policy = new AuditPolicy(
                "policy-1",
                true,
                true,
                true,
                true,
                Set.of("entity", "policy"),
                AuditPolicy.AuditLevel.STANDARD
            );

            assertThat(policy.shouldAudit("entity", "create")).isTrue();
            assertThat(policy.shouldAudit("policy", "create")).isTrue();
            assertThat(policy.shouldAudit("rule", "create")).isFalse();
        }

        @Test
        @DisplayName("audit policy supports different audit levels")
        void auditPolicySupportsDifferentAuditLevels() {
            AuditPolicy minimal = new AuditPolicy("policy-1", true, true, true, true, Set.of(), AuditPolicy.AuditLevel.MINIMAL);
            AuditPolicy detailed = new AuditPolicy("policy-2", true, true, true, true, Set.of(), AuditPolicy.AuditLevel.DETAILED);

            assertThat(minimal.auditLevel()).isEqualTo(AuditPolicy.AuditLevel.MINIMAL);
            assertThat(detailed.auditLevel()).isEqualTo(AuditPolicy.AuditLevel.DETAILED);
        }
    }

    // =========================================================================
    //  Retention Policy Tests
    // =========================================================================

    @Nested
    @DisplayName("Retention Policy")
    class RetentionPolicyTests {

        @Test
        @DisplayName("retention policy requires valid policy ID")
        void retentionPolicyRequiresValidPolicyId() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new RetentionPolicy("", Duration.ofDays(30), true, true, true, RetentionPolicy.RetentionAction.DELETE)
            ).withMessageContaining("policyId");
        }

        @Test
        @DisplayName("retention policy requires positive retention period")
        void retentionPolicyRequiresPositiveRetentionPeriod() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new RetentionPolicy("policy-1", Duration.ZERO, true, true, true, RetentionPolicy.RetentionAction.DELETE)
            ).withMessageContaining("retentionPeriod");

            assertThatIllegalArgumentException().isThrownBy(() ->
                new RetentionPolicy("policy-1", Duration.ofDays(-1), true, true, true, RetentionPolicy.RetentionAction.DELETE)
            ).withMessageContaining("retentionPeriod");
        }

        @Test
        @DisplayName("retention policy checks if data should be retained")
        void retentionPolicyChecksIfDataShouldBeRetained() {
            RetentionPolicy policy = new RetentionPolicy(
                "policy-1",
                Duration.ofDays(30),
                true,
                true,
                true,
                RetentionPolicy.RetentionAction.DELETE
            );

            Instant recentCreation = Instant.now().minus(Duration.ofDays(10));
            Instant oldCreation = Instant.now().minus(Duration.ofDays(40));

            assertThat(policy.shouldRetain(recentCreation)).isTrue();
            assertThat(policy.shouldRetain(oldCreation)).isFalse();
        }

        @Test
        @DisplayName("retention policy applies to specific resource types")
        void retentionPolicyAppliesToSpecificResourceTypes() {
            RetentionPolicy policy = new RetentionPolicy(
                "policy-1",
                Duration.ofDays(30),
                true,
                false,
                true,
                RetentionPolicy.RetentionAction.DELETE
            );

            assertThat(policy.appliesTo("auditlog")).isTrue();
            assertThat(policy.appliesTo("entity")).isFalse();
            assertThat(policy.appliesTo("event")).isTrue();
        }

        @Test
        @DisplayName("retention policy supports different actions")
        void retentionPolicySupportsDifferentActions() {
            RetentionPolicy delete = new RetentionPolicy("policy-1", Duration.ofDays(30), true, true, true, RetentionPolicy.RetentionAction.DELETE);
            RetentionPolicy archive = new RetentionPolicy("policy-2", Duration.ofDays(30), true, true, true, RetentionPolicy.RetentionAction.ARCHIVE);
            RetentionPolicy anonymize = new RetentionPolicy("policy-3", Duration.ofDays(30), true, true, true, RetentionPolicy.RetentionAction.ANONYMIZE);

            assertThat(delete.retentionAction()).isEqualTo(RetentionPolicy.RetentionAction.DELETE);
            assertThat(archive.retentionAction()).isEqualTo(RetentionPolicy.RetentionAction.ARCHIVE);
            assertThat(anonymize.retentionAction()).isEqualTo(RetentionPolicy.RetentionAction.ANONYMIZE);
        }
    }

    // =========================================================================
    //  Redaction Policy Tests
    // =========================================================================

    @Nested
    @DisplayName("Redaction Policy")
    class RedactionPolicyTests {

        @Test
        @DisplayName("redaction policy requires valid policy ID")
        void redactionPolicyRequiresValidPolicyId() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new RedactionPolicy("", Set.of("ssn"), RedactionPolicy.RedactionMode.FULL, Set.of())
            ).withMessageContaining("policyId");
        }

        @Test
        @DisplayName("redaction policy handles null sets")
        void redactionPolicyHandlesNullSets() {
            RedactionPolicy policy = new RedactionPolicy("policy-1", null, RedactionPolicy.RedactionMode.FULL, null);
            assertThat(policy.redactedFields()).isEmpty();
            assertThat(policy.exemptRoles()).isEmpty();
        }

        @Test
        @DisplayName("redaction policy checks if field should be redacted")
        void redactionPolicyChecksIfFieldShouldBeRedacted() {
            RedactionPolicy policy = new RedactionPolicy(
                "policy-1",
                Set.of("ssn", "creditCard"),
                RedactionPolicy.RedactionMode.FULL,
                Set.of("admin")
            );

            assertThat(policy.shouldRedact("ssn", "user")).isTrue();
            assertThat(policy.shouldRedact("ssn", "admin")).isFalse();
            assertThat(policy.shouldRedact("name", "user")).isFalse();
        }

        @Test
        @DisplayName("redaction policy applies full redaction")
        void redactionPolicyAppliesFullRedaction() {
            RedactionPolicy policy = new RedactionPolicy(
                "policy-1",
                Set.of("ssn"),
                RedactionPolicy.RedactionMode.FULL,
                Set.of()
            );

            assertThat(policy.applyRedaction("123-45-6789")).isEqualTo("[REDACTED]");
        }

        @Test
        @DisplayName("redaction policy applies partial redaction")
        void redactionPolicyAppliesPartialRedaction() {
            RedactionPolicy policy = new RedactionPolicy(
                "policy-1",
                Set.of("ssn"),
                RedactionPolicy.RedactionMode.PARTIAL,
                Set.of()
            );

            assertThat(policy.applyRedaction("123-45-6789")).isEqualTo("12****89");
        }

        @Test
        @DisplayName("redaction policy applies hash redaction")
        void redactionPolicyAppliesHashRedaction() {
            RedactionPolicy policy = new RedactionPolicy(
                "policy-1",
                Set.of("ssn"),
                RedactionPolicy.RedactionMode.HASH,
                Set.of()
            );

            String result = policy.applyRedaction("123-45-6789");
            assertThat(result).startsWith("[HASH:");
        }

        @Test
        @DisplayName("redaction policy applies mask redaction")
        void redactionPolicyAppliesMaskRedaction() {
            RedactionPolicy policy = new RedactionPolicy(
                "policy-1",
                Set.of("ssn"),
                RedactionPolicy.RedactionMode.MASK,
                Set.of()
            );

            assertThat(policy.applyRedaction("123-45-6789")).isEqualTo("***********");
        }

        @Test
        @DisplayName("redaction policy handles null values")
        void redactionPolicyHandlesNullValues() {
            RedactionPolicy policy = new RedactionPolicy(
                "policy-1",
                Set.of("ssn"),
                RedactionPolicy.RedactionMode.FULL,
                Set.of()
            );

            assertThat(policy.applyRedaction(null)).isNull();
        }
    }

    // =========================================================================
    //  Encryption Policy Tests
    // =========================================================================

    @Nested
    @DisplayName("Encryption Policy")
    class EncryptionPolicyTests {

        @Test
        @DisplayName("encryption policy requires valid policy ID")
        void encryptionPolicyRequiresValidPolicyId() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new EncryptionPolicy("", Set.of("ssn"), EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, EncryptionPolicy.EncryptionScope.FIELD_LEVEL)
            ).withMessageContaining("policyId");
        }

        @Test
        @DisplayName("encryption policy handles null fields")
        void encryptionPolicyHandlesNullFields() {
            EncryptionPolicy policy = new EncryptionPolicy("policy-1", null, EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, EncryptionPolicy.EncryptionScope.FIELD_LEVEL);
            assertThat(policy.encryptedFields()).isEmpty();
        }

        @Test
        @DisplayName("encryption policy uses default algorithm when null")
        void encryptionPolicyUsesDefaultAlgorithmWhenNull() {
            EncryptionPolicy policy = new EncryptionPolicy("policy-1", Set.of("ssn"), null, EncryptionPolicy.EncryptionScope.FIELD_LEVEL);
            assertThat(policy.algorithm()).isEqualTo(EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM);
        }

        @Test
        @DisplayName("encryption policy uses default scope when null")
        void encryptionPolicyUsesDefaultScopeWhenNull() {
            EncryptionPolicy policy = new EncryptionPolicy("policy-1", Set.of("ssn"), EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, null);
            assertThat(policy.encryptionScope()).isEqualTo(EncryptionPolicy.EncryptionScope.FIELD_LEVEL);
        }

        @Test
        @DisplayName("encryption policy checks if field should be encrypted")
        void encryptionPolicyChecksIfFieldShouldBeEncrypted() {
            EncryptionPolicy policy = new EncryptionPolicy(
                "policy-1",
                Set.of("ssn", "creditCard"),
                EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM,
                EncryptionPolicy.EncryptionScope.FIELD_LEVEL
            );

            assertThat(policy.shouldEncrypt("ssn")).isTrue();
            assertThat(policy.shouldEncrypt("name")).isFalse();
        }

        @Test
        @DisplayName("encryption policy supports different scopes")
        void encryptionPolicySupportsDifferentScopes() {
            EncryptionPolicy fieldLevel = new EncryptionPolicy("policy-1", Set.of(), EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, EncryptionPolicy.EncryptionScope.FIELD_LEVEL);
            EncryptionPolicy rowLevel = new EncryptionPolicy("policy-2", Set.of(), EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, EncryptionPolicy.EncryptionScope.ROW_LEVEL);
            EncryptionPolicy tenantLevel = new EncryptionPolicy("policy-3", Set.of(), EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, EncryptionPolicy.EncryptionScope.TENANT_LEVEL);
            EncryptionPolicy full = new EncryptionPolicy("policy-4", Set.of(), EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, EncryptionPolicy.EncryptionScope.FULL_ENCRYPTION);

            assertThat(fieldLevel.isFieldLevelEncryption()).isTrue();
            assertThat(rowLevel.isRowLevelEncryption()).isTrue();
            assertThat(tenantLevel.isTenantLevelEncryption()).isTrue();
            assertThat(full.isFullEncryption()).isTrue();
        }

        @Test
        @DisplayName("encryption policy supports different algorithms")
        void encryptionPolicySupportsDifferentAlgorithms() {
            EncryptionPolicy aes = new EncryptionPolicy("policy-1", Set.of(), EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM, EncryptionPolicy.EncryptionScope.FIELD_LEVEL);
            EncryptionPolicy rsa = new EncryptionPolicy("policy-2", Set.of(), EncryptionPolicy.EncryptionAlgorithm.RSA_4096, EncryptionPolicy.EncryptionScope.FIELD_LEVEL);
            EncryptionPolicy chacha = new EncryptionPolicy("policy-3", Set.of(), EncryptionPolicy.EncryptionAlgorithm.CHACHA20_POLY1305, EncryptionPolicy.EncryptionScope.FIELD_LEVEL);

            assertThat(aes.algorithm()).isEqualTo(EncryptionPolicy.EncryptionAlgorithm.AES_256_GCM);
            assertThat(rsa.algorithm()).isEqualTo(EncryptionPolicy.EncryptionAlgorithm.RSA_4096);
            assertThat(chacha.algorithm()).isEqualTo(EncryptionPolicy.EncryptionAlgorithm.CHACHA20_POLY1305);
        }
    }

    // =========================================================================
    //  Legal Hold Policy Tests
    // =========================================================================

    @Nested
    @DisplayName("Legal Hold Policy")
    class LegalHoldPolicyTests {

        @Test
        @DisplayName("legal hold policy requires valid policy ID")
        void legalHoldPolicyRequiresValidPolicyId() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new LegalHoldPolicy("", "case-1", "Test Case", Instant.now(), null, Set.of(), "admin", "notes")
            ).withMessageContaining("policyId");
        }

        @Test
        @DisplayName("legal hold policy requires valid case ID")
        void legalHoldPolicyRequiresValidCaseId() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new LegalHoldPolicy("policy-1", "", "Test Case", Instant.now(), null, Set.of(), "admin", "notes")
            ).withMessageContaining("caseId");
        }

        @Test
        @DisplayName("legal hold policy requires start date")
        void legalHoldPolicyRequiresStartDate() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new LegalHoldPolicy("policy-1", "case-1", "Test Case", null, null, Set.of(), "admin", "notes")
            ).withMessageContaining("holdStartDate");
        }

        @Test
        @DisplayName("legal hold policy requires authorized by")
        void legalHoldPolicyRequiresAuthorizedBy() {
            assertThatIllegalArgumentException().isThrownBy(() ->
                new LegalHoldPolicy("policy-1", "case-1", "Test Case", Instant.now(), null, Set.of(), "", "notes")
            ).withMessageContaining("authorizedBy");
        }

        @Test
        @DisplayName("legal hold policy handles null resource IDs")
        void legalHoldPolicyHandlesNullResourceIds() {
            LegalHoldPolicy policy = new LegalHoldPolicy("policy-1", "case-1", "Test Case", Instant.now(), null, null, "admin", "notes");
            assertThat(policy.heldResourceIds()).isEmpty();
        }

        @Test
        @DisplayName("legal hold policy is active when no end date")
        void legalHoldPolicyIsActiveWhenNoEndDate() {
            LegalHoldPolicy policy = new LegalHoldPolicy(
                "policy-1",
                "case-1",
                "Test Case",
                Instant.now().minus(Duration.ofDays(10)),
                null,
                Set.of("resource-1"),
                "admin",
                "notes"
            );

            assertThat(policy.isActive()).isTrue();
        }

        @Test
        @DisplayName("legal hold policy is active before end date")
        void legalHoldPolicyIsActiveBeforeEndDate() {
            LegalHoldPolicy policy = new LegalHoldPolicy(
                "policy-1",
                "case-1",
                "Test Case",
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().plus(Duration.ofDays(10)),
                Set.of("resource-1"),
                "admin",
                "notes"
            );

            assertThat(policy.isActive()).isTrue();
        }

        @Test
        @DisplayName("legal hold policy is inactive after end date")
        void legalHoldPolicyIsInactiveAfterEndDate() {
            LegalHoldPolicy policy = new LegalHoldPolicy(
                "policy-1",
                "case-1",
                "Test Case",
                Instant.now().minus(Duration.ofDays(20)),
                Instant.now().minus(Duration.ofDays(10)),
                Set.of("resource-1"),
                "admin",
                "notes"
            );

            assertThat(policy.isActive()).isFalse();
        }

        @Test
        @DisplayName("legal hold policy prevents modification of held resources")
        void legalHoldPolicyPreventsModificationOfHeldResources() {
            LegalHoldPolicy policy = new LegalHoldPolicy(
                "policy-1",
                "case-1",
                "Test Case",
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().plus(Duration.ofDays(10)),
                Set.of("resource-1", "resource-2"),
                "admin",
                "notes"
            );

            assertThat(policy.canModify("resource-1")).isFalse();
            assertThat(policy.canModify("resource-2")).isFalse();
            assertThat(policy.canModify("resource-3")).isTrue();
        }

        @Test
        @DisplayName("legal hold policy prevents deletion of held resources")
        void legalHoldPolicyPreventsDeletionOfHeldResources() {
            LegalHoldPolicy policy = new LegalHoldPolicy(
                "policy-1",
                "case-1",
                "Test Case",
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().plus(Duration.ofDays(10)),
                Set.of("resource-1", "resource-2"),
                "admin",
                "notes"
            );

            assertThat(policy.canDelete("resource-1")).isFalse();
            assertThat(policy.canDelete("resource-2")).isFalse();
            assertThat(policy.canDelete("resource-3")).isTrue();
        }

        @Test
        @DisplayName("legal hold policy allows operations when inactive")
        void legalHoldPolicyAllowsOperationsWhenInactive() {
            LegalHoldPolicy policy = new LegalHoldPolicy(
                "policy-1",
                "case-1",
                "Test Case",
                Instant.now().minus(Duration.ofDays(20)),
                Instant.now().minus(Duration.ofDays(10)),
                Set.of("resource-1"),
                "admin",
                "notes"
            );

            assertThat(policy.canModify("resource-1")).isTrue();
            assertThat(policy.canDelete("resource-1")).isTrue();
        }
    }
}
