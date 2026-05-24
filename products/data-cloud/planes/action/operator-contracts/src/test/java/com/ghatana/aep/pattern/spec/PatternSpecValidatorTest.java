package com.ghatana.aep.pattern.spec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
    void rejectsAgentOperatorWithoutOutputSchema() {
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
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
                    "capabilityRef", "agents/action@1.0.0/capability",
            "outputSchema", "ActionResult",
            "toolPolicy", Map.of("allowedTools", List.of("pagerduty.incident.create")))));

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
            "evidenceStore", "eventcloud"));
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
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("evidencePolicy is required in production"));
    }

    @Test
    void productionRequiresEvidenceStore() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("lifecycle", Map.of("state", "ACTIVE", "evidencePolicy", Map.of("retentionDays", 90)));
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
            "evidenceStore", "eventcloud"));
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
        spec.put("governance", Map.of("owner", "sre", "approvalPolicy", "human_required"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void productionAcceptsReviewPolicy() {
        Map<String, Object> spec = validSpec(Map.of("event", "deploy.started"));
        spec.put("governance", Map.of("owner", "sre", "reviewPolicy", "human_required"));
        spec.put("lifecycle", Map.of(
            "state", "ACTIVE",
            "evidencePolicy", Map.of("retentionDays", 90),
            "evidenceStore", "eventcloud"));
        PatternSpecValidationResult result = PatternSpecValidator.validate(
            spec, "7f84bc08e9e4e6d7e209cb49a855f199f7c90347", "production");

        assertThat(result.valid()).isTrue();
    }

    private static Map<String, Object> validSpec(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "test", "tenantId", "tenant-a", "owner", "sre"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true, "tracing", true)));
    }
}
