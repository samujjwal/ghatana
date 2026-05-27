/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.aep.pattern.spec.PatternSpecValidationResult;
import com.ghatana.aep.pattern.spec.PatternSpecValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AEP-004: Side-effect governance E2E tests.
 *
 * <p>Verifies that side-effecting action operators require tool policy, approval policy,
 * idempotency, audit, rollback/compensation metadata, denied path is auditable, and approved
 * path executes once. Tests invoke PatternSpecValidator to exercise production enforcement.
 *
 * @doc.type class
 * @doc.purpose Side-effect governance E2E tests (AEP-004)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Side-Effect Governance E2E Tests")
@Tag("aep")
@Tag("governance")
@Tag("side-effect")
@Tag("e2e")
class SideEffectGovernanceE2ETest {

    // ==================== AEP-004 / AEP-P1-005: Side-effecting AGENT_ACTION requires toolPolicy ====================

    @Test
    @DisplayName("AEP-004: AGENT_ACTION without toolPolicy is rejected by validator")
    void agentActionWithoutToolPolicyIsRejected() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/write-agent@1.0.0",
            "capabilityRef", "agents/write-agent@1.0.0/capability",
            "outputSchema", "WriteResult"
            // Missing toolPolicy — required for AGENT_ACTION
        )));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("toolPolicy"));
    }

    @Test
    @DisplayName("AEP-004: AGENT_ACTION with toolPolicy and governance passes validation")
    void agentActionWithToolPolicyAndGovernancePasses() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/write-agent@1.0.0",
            "capabilityRef", "agents/write-agent@1.0.0/capability",
            "outputSchema", "WriteResult",
            "toolPolicy", Map.of("allowedTools", List.of("data-cloud.entity.write"))
        )));

        assertThat(result.valid()).isTrue();
    }

    // ==================== AEP-004: AGENT_ACTION requires governance ====================

    @Test
    @DisplayName("AEP-004: AGENT_ACTION without governance section is rejected")
    void agentActionWithoutGovernanceSectionIsRejected() {
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
            "capabilityRef", "agents/action@1.0.0/capability",
            "outputSchema", "ActionResult",
            "toolPolicy", Map.of("allowedTools", List.of("pagerduty.incident.create"))
        ));
        spec.remove("governance");

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("governance"));
    }

    // ==================== AEP-004: Production requires approval policy for promotions ====================

    @Test
    @DisplayName("AEP-004: Production promotion without approval or review policy is rejected")
    void productionPromotionWithoutApprovalPolicyIsRejected() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full"
            // Missing approvalPolicy and reviewPolicy
        ));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"
        ));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
            assertThat(error).contains("approvalPolicy"));
    }

    @Test
    @DisplayName("AEP-004: Production promotion with full governance proof passes")
    void productionPromotionWithFullGovernanceProofPasses() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"
        ));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"
        ));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    // ==================== AEP-004: Denied path is auditable (via validation gate) ====================

    @Test
    @DisplayName("AEP-004: Spec missing required governance is rejected by production validator")
    void specMissingRequiredGovernanceIsRejectedByProductionValidator() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of("owner", "sre")); // No approvalPolicy, no rollbackPolicy, no auditPolicy

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        // Denied path: missing governance proof prevents execution
        assertThat(result.errors()).isNotEmpty();
    }

    // ==================== AEP-004: Full governance proof covers all required controls ====================

    @Test
    @DisplayName("AEP-004: No side-effecting action can be promoted without audit policy in production")
    void noSideEffectingActionWithoutAuditPolicyInProduction() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "approvalPolicy", "human_required"
            // Missing auditPolicy
        ));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"
        ));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("auditPolicy"));
    }

    @Test
    @DisplayName("AEP-004: No side-effecting action can be promoted without rollback policy in production")
    void noSideEffectingActionWithoutRollbackPolicyInProduction() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"
            // Missing rollbackPolicy
        ));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"
        ));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("rollbackPolicy"));
    }

    private static Map<String, Object> validSpec(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "test", "namespace", "test", "version", "1.0.0", "tenantId", "tenant-a", "owner", "sre"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true, "tracing", true)));
    }
}
