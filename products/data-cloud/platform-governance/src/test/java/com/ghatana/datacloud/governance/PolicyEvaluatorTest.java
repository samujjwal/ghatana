/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PolicyEvaluator}.
 *
 * <p>Exercises every supported {@link PolicyService.PolicyType} for both compliant and
 * violating records, plus null-argument guards and disabled-policy short-circuit.
 */
@DisplayName("PolicyEvaluator")
class PolicyEvaluatorTest {

    private PolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PolicyEvaluator();
    }

    // ─── Null guards ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null argument guards")
    class NullGuards {

        @Test
        @DisplayName("null policy throws NullPointerException")
        void nullPolicyThrows() {
            assertThatThrownBy(() ->
                    evaluator.evaluate(null, Map.of(), ctx()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null record throws NullPointerException")
        void nullRecordThrows() {
            assertThatThrownBy(() ->
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.PII_MASKING), null, ctx()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null context throws NullPointerException")
        void nullContextThrows() {
            assertThatThrownBy(() ->
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.PII_MASKING), Map.of(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─── Disabled policy short-circuits ──────────────────────────────────────

    @Nested
    @DisplayName("Disabled policy")
    class DisabledPolicy {

        @Test
        @DisplayName("disabled policy never produces violations")
        void disabledPolicyProducesNoViolations() {
            PolicyService.Policy disabled = new PolicyService.Policy(
                    "p-disabled", "Disabled", "desc", "tenant-1",
                    PolicyService.PolicyType.PII_MASKING,
                    List.of(), /* enabled= */ false, 1, Instant.now(), Instant.now());

            Map<String, Object> record = Map.of("email", "user@example.com");
            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(disabled, record, ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── PII_MASKING ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PII_MASKING")
    class PiiMasking {

        @Test
        @DisplayName("unmasked email field produces violation")
        void unmaskedEmailProducesViolation() {
            Map<String, Object> record = Map.of("email", "user@example.com");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.PII_MASKING), record, ctx());

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).field()).isEqualTo("email");
            assertThat(violations.get(0).type()).isEqualTo(PolicyService.PolicyType.PII_MASKING);
        }

        @Test
        @DisplayName("masked email (***@example.com) is compliant")
        void maskedEmailIsCompliant() {
            Map<String, Object> record = Map.of("email", "***@***.***");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.PII_MASKING), record, ctx());

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("[REDACTED] value is compliant")
        void redactedValueIsCompliant() {
            Map<String, Object> record = Map.of("phone", "[REDACTED]");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.PII_MASKING), record, ctx());

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("non-PII fields are not flagged")
        void nonPiiFieldsAreIgnored() {
            Map<String, Object> record = Map.of("account_balance", 100.0, "status", "active");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.PII_MASKING), record, ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── ACCESS_CONTROL ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("ACCESS_CONTROL")
    class AccessControl {

        @Test
        @DisplayName("explicitly denied principal produces violation")
        void deniedPrincipalProducesViolation() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-ac", "AC", "desc", "tenant-1",
                    PolicyService.PolicyType.ACCESS_CONTROL,
                    List.of("DENY:banned-user"), true, 5, Instant.now(), Instant.now());

            PolicyEvaluator.EvaluationContext bannedCtx = new PolicyEvaluator.EvaluationContext(
                    "tenant-1", "banned-user", "analytics", "EU");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, Map.of("data", "value"), bannedCtx);

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).reason()).contains("banned-user");
        }

        @Test
        @DisplayName("allowed principal produces no violation")
        void allowedPrincipalProducesNoViolation() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-ac", "AC", "desc", "tenant-1",
                    PolicyService.PolicyType.ACCESS_CONTROL,
                    List.of("DENY:banned-user"), true, 5, Instant.now(), Instant.now());

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, Map.of("data", "value"), ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── AUDIT_LOGGING ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AUDIT_LOGGING")
    class AuditLogging {

        @Test
        @DisplayName("record missing created_at produces violation")
        void missingCreatedAtProducesViolation() {
            Map<String, Object> record = Map.of("updated_by", "alice");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.AUDIT_LOGGING), record, ctx());

            assertThat(violations)
                    .extracting(PolicyEvaluator.PolicyViolation::field)
                    .containsExactly("created_at");
        }

        @Test
        @DisplayName("record with both audit fields is compliant")
        void auditCompliantRecord() {
            Map<String, Object> record = Map.of(
                    "created_at", Instant.now().toString(),
                    "updated_by", "alice");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.AUDIT_LOGGING), record, ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── CONSENT_MANAGEMENT ──────────────────────────────────────────────────

    @Nested
    @DisplayName("CONSENT_MANAGEMENT")
    class ConsentManagement {

        @Test
        @DisplayName("missing consent_status produces violation")
        void missingConsentStatusProducesViolation() {
            Map<String, Object> record = Map.of("name", "Alice");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.CONSENT_MANAGEMENT), record, ctx());

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).field()).isEqualTo("consent_status");
        }

        @Test
        @DisplayName("consent_status=DENIED produces violation")
        void deniedConsentProducesViolation() {
            Map<String, Object> record = Map.of("consent_status", "DENIED");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.CONSENT_MANAGEMENT), record, ctx());

            assertThat(violations).hasSize(1);
        }

        @Test
        @DisplayName("consent_status=GRANTED is compliant")
        void grantedConsentIsCompliant() {
            Map<String, Object> record = Map.of("consent_status", "GRANTED");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.CONSENT_MANAGEMENT), record, ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── DATA_MINIMIZATION ───────────────────────────────────────────────────

    @Nested
    @DisplayName("DATA_MINIMIZATION")
    class DataMinimization {

        @Test
        @DisplayName("field not in allowed list produces violation")
        void extraFieldProducesViolation() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-dm", "DM", "desc", "tenant-1",
                    PolicyService.PolicyType.DATA_MINIMIZATION,
                    List.of("id", "name"), true, 3, Instant.now(), Instant.now());

            Map<String, Object> record = Map.of("id", "1", "name", "Alice", "ssn", "123-45-6789");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, record, ctx());

            assertThat(violations)
                    .extracting(PolicyEvaluator.PolicyViolation::field)
                    .containsExactly("ssn");
        }

        @Test
        @DisplayName("all fields in allowed list is compliant")
        void allFieldsAllowedIsCompliant() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-dm", "DM", "desc", "tenant-1",
                    PolicyService.PolicyType.DATA_MINIMIZATION,
                    List.of("id", "name"), true, 3, Instant.now(), Instant.now());

            Map<String, Object> record = Map.of("id", "1", "name", "Alice");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, record, ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── PURPOSE_LIMITATION ──────────────────────────────────────────────────

    @Nested
    @DisplayName("PURPOSE_LIMITATION")
    class PurposeLimitation {

        @Test
        @DisplayName("disallowed purpose produces violation")
        void disallowedPurposeProducesViolation() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-pl", "PL", "desc", "tenant-1",
                    PolicyService.PolicyType.PURPOSE_LIMITATION,
                    List.of("research", "analytics"), true, 4, Instant.now(), Instant.now());

            PolicyEvaluator.EvaluationContext adCtx = new PolicyEvaluator.EvaluationContext(
                    "tenant-1", "user-1", "advertising", "EU");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, Map.of("field", "value"), adCtx);

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).reason()).contains("advertising");
        }

        @Test
        @DisplayName("allowed purpose is compliant")
        void allowedPurposeIsCompliant() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-pl", "PL", "desc", "tenant-1",
                    PolicyService.PolicyType.PURPOSE_LIMITATION,
                    List.of("research", "analytics"), true, 4, Instant.now(), Instant.now());

            PolicyEvaluator.EvaluationContext researchCtx = new PolicyEvaluator.EvaluationContext(
                    "tenant-1", "user-1", "research", "EU");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, Map.of("field", "value"), researchCtx);

            assertThat(violations).isEmpty();
        }
    }

    // ─── CROSS_BORDER_TRANSFER ───────────────────────────────────────────────

    @Nested
    @DisplayName("CROSS_BORDER_TRANSFER")
    class CrossBorderTransfer {

        @Test
        @DisplayName("disallowed region produces violation")
        void disallowedRegionProducesViolation() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-cb", "CB", "desc", "tenant-1",
                    PolicyService.PolicyType.CROSS_BORDER_TRANSFER,
                    List.of("EU", "UK"), true, 2, Instant.now(), Instant.now());

            PolicyEvaluator.EvaluationContext usCtx = new PolicyEvaluator.EvaluationContext(
                    "tenant-1", "svc", "analytics", "US");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, Map.of("data", "x"), usCtx);

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).reason()).contains("US");
        }

        @Test
        @DisplayName("allowed region is compliant")
        void allowedRegionIsCompliant() {
            PolicyService.Policy policy = new PolicyService.Policy(
                    "p-cb", "CB", "desc", "tenant-1",
                    PolicyService.PolicyType.CROSS_BORDER_TRANSFER,
                    List.of("EU", "UK"), true, 2, Instant.now(), Instant.now());

            PolicyEvaluator.EvaluationContext euCtx = new PolicyEvaluator.EvaluationContext(
                    "tenant-1", "svc", "analytics", "EU");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policy, Map.of("data", "x"), euCtx);

            assertThat(violations).isEmpty();
        }
    }

    // ─── ENCRYPTION ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ENCRYPTION")
    class Encryption {

        @Test
        @DisplayName("missing encryption_status produces violation")
        void missingEncryptionStatusProducesViolation() {
            Map<String, Object> record = Map.of("payload", "data");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.ENCRYPTION), record, ctx());

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).field()).isEqualTo("encryption_status");
        }

        @Test
        @DisplayName("encryption_status=ENCRYPTED is compliant")
        void encryptedRecordIsCompliant() {
            Map<String, Object> record = Map.of("payload", "data", "encryption_status", "ENCRYPTED");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.ENCRYPTION), record, ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── DATA_RETENTION ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("DATA_RETENTION")
    class DataRetention {

        @Test
        @DisplayName("record missing retention_expires_at produces violation")
        void missingRetentionExpiresAtProducesViolation() {
            Map<String, Object> record = Map.of("payload", "value");

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.DATA_RETENTION), record, ctx());

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).field()).isEqualTo("retention_expires_at");
        }

        @Test
        @DisplayName("record with retention_expires_at is compliant")
        void recordWithExpiryIsCompliant() {
            Map<String, Object> record = new HashMap<>();
            record.put("payload", "value");
            record.put("retention_expires_at", Instant.now().plusSeconds(86400).toString());

            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluate(policyOf(PolicyService.PolicyType.DATA_RETENTION), record, ctx());

            assertThat(violations).isEmpty();
        }
    }

    // ─── evaluateAll ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("evaluateAll")
    class EvaluateAll {

        @Test
        @DisplayName("skips policies belonging to a different tenant")
        void skipsDifferentTenantPolicies() {
            PolicyService.Policy otherTenantPolicy = new PolicyService.Policy(
                    "p-ot", "OtherTenant", "desc", "tenant-other",
                    PolicyService.PolicyType.PII_MASKING,
                    List.of(), true, 1, Instant.now(), Instant.now());

            Map<String, Object> record = Map.of("email", "plain@example.com");
            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluateAll(List.of(otherTenantPolicy), record, ctx());

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("aggregates violations across multiple matching policies")
        void aggregatesViolationsAcrossMultiplePolicies() {
            List<PolicyService.Policy> policies = List.of(
                    policyOf(PolicyService.PolicyType.PII_MASKING),
                    policyOf(PolicyService.PolicyType.AUDIT_LOGGING));

            // Record has unmasked email AND is missing audit fields
            Map<String, Object> record = Map.of("email", "user@example.com");
            List<PolicyEvaluator.PolicyViolation> violations =
                    evaluator.evaluateAll(policies, record, ctx());

            assertThat(violations.stream().map(PolicyEvaluator.PolicyViolation::type))
                    .containsExactlyInAnyOrder(
                            PolicyService.PolicyType.PII_MASKING,
                            PolicyService.PolicyType.AUDIT_LOGGING,
                            PolicyService.PolicyType.AUDIT_LOGGING);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static PolicyService.Policy policyOf(PolicyService.PolicyType type) {
        return new PolicyService.Policy(
                "p-" + type.name().toLowerCase(),
                type.name(),
                "Auto-generated test policy",
                "tenant-1",
                type,
                List.of(),
                /* enabled= */ true,
                1,
                Instant.now(),
                Instant.now());
    }

    private static PolicyEvaluator.EvaluationContext ctx() {
        return new PolicyEvaluator.EvaluationContext("tenant-1", "test-principal", "analytics", "EU");
    }
}
