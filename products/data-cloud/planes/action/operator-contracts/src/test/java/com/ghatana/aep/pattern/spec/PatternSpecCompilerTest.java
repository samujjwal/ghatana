package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.operator.contract.OperatorKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternSpecCompilerTest {

    @Test
    void compilesPatternSpecIntoDeterministicRuntimeGraph() {
        CompiledPattern compiled = PatternSpecCompiler.compile(validSpec(Map.of(
            "operator", "SEQ",
            "within", "PT30M",
            "operands", List.of(
                Map.of("event", "deploy.started"),
                Map.of(
                    "operator", "WINDOW",
                    "window", "PT10M",
                    "pattern", Map.of("event", "service.error_rate_elevated")),
                Map.of(
                    "operator", "AGENT_PREDICATE",
                    "agentRef", "agents/sre-risk-assessor@1.0.0",
                    "outputSchema", "RiskDecision")))));

        assertThat(compiled.patternId()).isEqualTo("sre-risk-sequence");
        assertThat(compiled.runtimePlanId()).isEqualTo("pattern-runtime-sre-risk-sequence");
        assertThat(compiled.nodeOrder()).containsExactly("root", "root-0", "root-1", "root-1-pattern", "root-2");
        assertThat(compiled.root().operatorKind()).isEqualTo(OperatorKind.SEQ);
        assertThat(compiled.root().children().get(2).operatorKind()).isEqualTo(OperatorKind.AGENT_PREDICATE);
        assertThat(compiled.root().children().get(2).agentRef()).contains("agents/sre-risk-assessor@1.0.0");
    }

    @Test
    void rejectsInvalidSpecBeforeRuntimePlanGeneration() {
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
            "outputSchema", "ActionResult"));

        assertThatThrownBy(() -> PatternSpecCompiler.compile(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("toolPolicy");
    }

    private static Map<String, Object> validSpec(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "sre-risk-sequence", "tenantId", "tenant-a", "owner", "sre"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true, "tracing", true)));
    }
}
