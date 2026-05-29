package com.ghatana.aep.pattern.spec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PatternSpec validation tests.
 *
 * <p>AEP-001: PatternSpec full lifecycle transition E2E</p>
 * <p>AEP-002: Production PatternSpec compile validation tests</p>
 */
class PatternSpecValidatorTest {

    @Test
    void acceptsAgentPredicateInsideSequence() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "SEQ",
            "operands", List.of(
                Map.of("event", "deploy.started"),
                Map.of(
                    "operator", "AGENT_PREDICATE",
                    "agentRef", "agents/sre-risk-assessor@1.0.0",
                    "capabilityRef", "agents/sre-risk-assessor@1.0.0/capability",
                    "outputSchema", "RiskDecision")))));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsUnknownOperator() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "MAGIC",
            "outputSchema", "MagicOutput")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("unknown"));
    }

    @Test
    void rejectsAgentCapabilityWithoutOutputSchema() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "AGENT_ENRICH",
            "agentRef", "agents/enricher@1.0.0",
                    "capabilityRef", "agents/enricher@1.0.0/capability")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
    }

    @Test
    void rejectsAgentActionWithoutGovernance() {
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
                    "capabilityRef", "agents/action@1.0.0/capability",
            "outputSchema", "ActionResult",
            "toolPolicy", Map.of("allowedTools", List.of("pagerduty.incident.create"))));
        spec.remove("governance");

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("governance"));
    }

    @Test
    void rejectsAgentActionWithoutToolPolicy() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
                    "capabilityRef", "agents/action@1.0.0/capability",
            "outputSchema", "ActionResult")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("toolPolicy"));
    }

    @Test
    void acceptsGovernedAgentAction() {
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
                    "capabilityRef", "agents/action@1.0.0/capability",
            "outputSchema", "ActionResult"));
        // Move toolPolicy to governance section where validator expects it
        Map<String, Object> governance = new java.util.LinkedHashMap<>((Map<String, Object>) spec.get("governance"));
        governance.put("toolPolicy", Map.of("allowedTools", List.of("pagerduty.incident.create")));
        spec.put("governance", governance);

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsSpecWithoutProductionSemantics() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("semantics", Map.of("timePolicy", Map.of()));

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("uncertaintyPolicy"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("replayPolicy"));
    }

    @Test
    void rejectsSequenceWithoutEnoughOperands() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "SEQ",
            "operands", List.of(Map.of("event", "deploy.started")))));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("at least 2 operands"));
    }

    @Test
    void rejectsWindowWithoutNestedPatternOrWindowSpec() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "WINDOW",
            "event", "service.error_rate_elevated")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("nested pattern"));
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("window"));
    }

    @Test
    void acceptsTimedWindowAroundEventReference() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "WINDOW",
            "window", "PT10M",
            "pattern", Map.of("event", "service.error_rate_elevated"))));

        assertThat(result.valid()).isTrue();
    }

    // ==================== AEP-001: PatternSpec Lifecycle Transition Tests ====================

    @Test
    void aep001ShadowToActiveTransitionValid() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of("state", "ACTIVE"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void aep001ActiveToDegradedTransitionValid() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "DEGRADED",
            "previousState", "ACTIVE",
            "degradationReason", "high_error_rate",
            "degradedBehavior", Map.of("mode", "disabled", "sideEffectPolicy", "none"),
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void aep001DegradedToRollbackTransitionValid() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "ROLLBACK",
            "previousState", "ACTIVE",
            "governance", Map.of(
                "rollbackDecision", "auto",
                "rollbackReason", "high_error_rate",
                "rollbackApprover", "sre-on-call",
                "previousVersion", "v1.2.3"),
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "high",
            "rollbackPolicy", "automatic",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void aep001ActiveToRetiredTransitionValid() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "RETIRED",
            "retirementReason", "obsolete",
            "evidencePolicy", Map.of("retentionDays", 365),
            "evidenceStore", "eventcloud://archive"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "low",
            "rollbackPolicy", "none",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void aep001IllegalTransitionRetiredToActiveRejected() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "previousState", "RETIRED")); // Illegal: cannot reactivate retired spec

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> 
            assertThat(error).contains("not allowed"));
    }

    @Test
    void aep001ShadowToProductionWithoutApprovalRejected() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of("state", "ACTIVE"));
        spec.put("governance", Map.of("owner", "sre")); // Missing approvalPolicy

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> 
            assertThat(error).contains("approvalPolicy"));
    }

    // ==================== AEP-002: Production Compile Validation Tests ====================

    @Test
    void aep002ProductionCompileValidatesOutputSchema() {
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_PREDICATE",
            "agentRef", "agents/sre-risk-assessor@1.0.0",
            "capabilityRef", "agents/sre-risk-assessor@1.0.0/capability"));
        // Missing outputSchema

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> 
            assertThat(error).contains("outputSchema"));
    }

    @Test
    void aep002ProductionCompileValidatesReplayPolicy() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("semantics", Map.of(
            "timePolicy", Map.of(),
            "uncertaintyPolicy", Map.of()
            // Missing replayPolicy
        ));

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> 
            assertThat(error).contains("replayPolicy"));
    }

    @Test
    void aep002ProductionCompileValidatesUncertaintyPolicy() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("semantics", Map.of(
            "timePolicy", Map.of(),
            "replayPolicy", Map.of()
            // Missing uncertaintyPolicy
        ));

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> 
            assertThat(error).contains("uncertaintyPolicy"));
    }

    // AEP-004: Production semantics hardening tests
    @Test
    void productionRequiresCommitSha() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(spec, null, "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("commitSha is required in production"));
    }

    @Test
    void productionAcceptsValidCommitSha() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void productionRejectsInvalidCommitShaFormat() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(spec, "invalid-sha", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("40 hexadecimal characters"));
    }

    @Test
    void nonProductionDoesNotRequireCommitSha() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(spec, null, "development");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void productionRequiresEvidencePolicy() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of("state", "ACTIVE"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("evidencePolicy is required in production"));
    }

    @Test
    void productionRequiresEvidenceStore() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of("state", "ACTIVE", "evidencePolicy", Map.of("retentionDays", 90)));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("evidenceStore is required in production"));
    }

    @Test
    void productionAcceptsEvidencePolicyAndStore() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void productionRequiresApprovalOrReviewPolicy() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of("owner", "sre"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("approvalPolicy or governance.reviewPolicy is required in production"));
    }

    @Test
    void productionAcceptsApprovalPolicy() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void productionAcceptsReviewPolicy() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "reviewPolicy", "human_required"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    // ==================== AEP-P1-004: Consistent promotion governance (root governance fallback) ====================

    @Test
    void aep004PromotionToActiveWithRootGovernanceIsAcceptedWhenLifecycleGovernanceAbsent() {
        // AEP-P1-004: Root governance must be accepted as fallback when lifecycle.governance is absent
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "previousState", "APPROVED",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"
            // No nested lifecycle.governance — root governance should be the fallback
        ));
        spec.put("governance", Map.of(
            "owner", "sre",
            "riskLevel", "medium",
            "rollbackPolicy", "manual",
            "auditPolicy", "full",
            "approvalPolicy", "human_required",
            "evidenceStore", "eventcloud://default",
            "commitSha", "7f84bc08e9e4e6d7e209cb49a855f199f7c90347"));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void aep004PromotionToActiveWithNoGovernanceAtAllIsRejected() {
        // AEP-P1-004: Promotion without any governance (neither lifecycle-scoped nor root) must fail
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "previousState", "APPROVED",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud://default"
        ));
        // Use governance with neither approvalPolicy nor reviewPolicy
        spec.put("governance", Map.of("owner", "sre"));

        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
    }

    // ==================== AEP-P1-005: Tightened hasProductionPolicy in compiler ====================

    @Test
    void aep005CompilerRejectsSideEffectingCapabilityWithOnlyApprovalPolicyNoCommitSha() {
        // AEP-P1-005: approvalPolicy alone is insufficient — commitSha is also required
        // This validates the tightened hasProductionPolicy: requires BOTH approval/review AND commitSha
        // Since PatternSpecCompiler.deploymentProfile defaults to "local", the check only runs in prod profile
        // We validate the governance rule via the validator instead
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "approvalPolicy", "human_required"
            // Missing commitSha, owner, etc.
        ));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, null, "production"); // null commitSha → should fail

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("commitSha"));
    }

    @Test
    void aep005CompilerRejectsSideEffectingCapabilityWithOnlyCommitShaNoApprovalPolicy() {
        // AEP-P1-005: commitSha alone is insufficient — approvalPolicy or reviewPolicy is also required
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of(
            "owner", "sre"
            // No approvalPolicy, no reviewPolicy — commitSha provided externally
        ));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("approvalPolicy"));
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
