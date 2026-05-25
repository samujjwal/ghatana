/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Release-blocking governance policy proof for audit, retention, redaction, encryption, and legal hold posture
 * @doc.layer product
 * @doc.pattern Contract Test
 */
@DisplayName("Governance policy release proof")
class GovernancePolicyTest {

    @Test
    void evaluatesReleaseBlockingPolicySetAgainstCompliantRecord() {
        PolicyEvaluator evaluator = new PolicyEvaluator();
        Map<String, Object> record = Map.of(
            "email", "***@***.***",
            "created_at", Instant.parse("2026-05-23T00:00:00Z").toString(),
            "updated_by", "governance-service",
            "retention_expires_at", Instant.parse("2027-05-23T00:00:00Z").toString(),
            "encryption_status", "ENCRYPTED",
            "legal_hold", true);

        List<PolicyEvaluator.PolicyViolation> violations = evaluator.evaluateAll(
            releasePolicies(),
            record,
            new PolicyEvaluator.EvaluationContext("tenant-a", "governance-service", "analytics", "EU"));

        assertThat(violations).isEmpty();
    }

    @Test
    void reportsMissingAuditRetentionRedactionAndEncryptionControls() {
        PolicyEvaluator evaluator = new PolicyEvaluator();
        Map<String, Object> record = Map.of("email", "plain@example.com");

        List<PolicyEvaluator.PolicyViolation> violations = evaluator.evaluateAll(
            releasePolicies(),
            record,
            new PolicyEvaluator.EvaluationContext("tenant-a", "governance-service", "analytics", "EU"));

        assertThat(violations).extracting(PolicyEvaluator.PolicyViolation::type)
            .contains(
                PolicyService.PolicyType.PII_MASKING,
                PolicyService.PolicyType.AUDIT_LOGGING,
                PolicyService.PolicyType.DATA_RETENTION,
                PolicyService.PolicyType.ENCRYPTION);
    }

    private static List<PolicyService.Policy> releasePolicies() {
        return List.of(
            policy(PolicyService.PolicyType.PII_MASKING, List.of()),
            policy(PolicyService.PolicyType.AUDIT_LOGGING, List.of()),
            policy(PolicyService.PolicyType.DATA_RETENTION, List.of()),
            policy(PolicyService.PolicyType.ENCRYPTION, List.of()),
            policy(PolicyService.PolicyType.CROSS_BORDER_TRANSFER, List.of("EU", "UK")),
            policy(PolicyService.PolicyType.PURPOSE_LIMITATION, List.of("analytics")));
    }

    private static PolicyService.Policy policy(PolicyService.PolicyType type, List<String> rules) {
        return new PolicyService.Policy(
            "release-" + type.name().toLowerCase(),
            type.name(),
            "Release-blocking governance policy",
            "tenant-a",
            type,
            rules,
            true,
            1,
            Instant.parse("2026-05-23T00:00:00Z"),
            Instant.parse("2026-05-23T00:00:00Z"));
    }
}
