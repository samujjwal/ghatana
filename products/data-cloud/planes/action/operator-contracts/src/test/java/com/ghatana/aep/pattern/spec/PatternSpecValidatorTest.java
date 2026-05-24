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
            "agentRef", "agents/enricher@1.0.0")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
    }

    @Test
    void rejectsAgentActionWithoutGovernance() {
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
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
            "outputSchema", "ActionResult")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("toolPolicy"));
    }

    @Test
    void acceptsGovernedAgentAction() {
        PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
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
