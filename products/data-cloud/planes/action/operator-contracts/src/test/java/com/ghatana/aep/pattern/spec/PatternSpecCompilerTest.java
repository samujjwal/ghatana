package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.CapabilityKind;
import com.ghatana.aep.agent.capability.ExternalAgentCapabilityRegistry;
import com.ghatana.aep.agent.capability.ExternalAgentProvider;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                Map.of("event", "service.error_rate_elevated"),
                Map.of(
                    "operator", "AGENT_PREDICATE",
                    "capabilityRef", "agents/sre-risk-assessor@1.0.0/capability",
                    "outputSchema", "RiskDecision")))));

        assertThat(compiled.patternId()).isEqualTo("sre-risk-sequence");
        assertThat(compiled.runtimePlanId()).isEqualTo("pattern-runtime-sre-risk-sequence");
        assertThat(compiled.nodeOrder()).containsExactly("root", "root-0", "root-1", "root-2");
        assertThat(compiled.root().operatorKind()).isEqualTo(OperatorKind.SEQ);
        assertThat(compiled.root().children().get(2).operatorKind()).isEqualTo(OperatorKind.AGENT_PREDICATE);
        // DC-P5-001: Typed model uses capabilityRef instead of agentRef
        assertThat(compiled.root().children().get(2).capabilityRef())
            .contains("agents/sre-risk-assessor@1.0.0/capability");
    }

    @Test
    void rejectsInvalidSpecBeforeRuntimePlanGeneration() {
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_ACTION",
            "agentRef", "agents/action@1.0.0",
            "capabilityRef", "agents/action@1.0.0/capability",
            "outputSchema", "ActionResult"));

        assertThatThrownBy(() -> PatternSpecCompiler.compile(spec))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("toolPolicy");
    }

    @Test
    void rejectsMismatchedCapabilityInputSchemaBeforeRuntimePlanGeneration() {
        String capabilityRef = "agents/sre-risk-assessor@1.0.0/capability";
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_PREDICATE",
            "capabilityRef", capabilityRef,
            "inputSchema", "WrongContext",
            "outputSchema", "RiskDecision"));

        assertThatThrownBy(() -> PatternSpecCompiler.compile(spec, registry(descriptor(capabilityRef, "PREDICATE"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("input schema");
    }

    @Test
    void rejectsCapabilityRoleMismatchBeforeRuntimePlanGeneration() {
        String capabilityRef = "agents/sre-risk-assessor@1.0.0/capability";
        Map<String, Object> spec = validSpec(Map.of(
            "operator", "AGENT_PREDICATE",
            "capabilityRef", capabilityRef,
            "inputSchema", "EventContext",
            "outputSchema", "RiskDecision"));

        assertThatThrownBy(() -> PatternSpecCompiler.compile(spec, registry(descriptor(capabilityRef, "REVIEW"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match");
    }

    private static Map<String, Object> validSpec(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "sre-risk-sequence", "namespace", "tenant-a", "version", "1.0.0"),
            "semantics", Map.of("timePolicy", "event_time", "uncertaintyPolicy", "propagate", "replayPolicy", "recorded_output"),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metricsPolicy", "enabled", "loggingPolicy", "enabled")));
    }

    private static CapabilityDescriptor descriptor(String capabilityRef, String role) {
        return new CapabilityDescriptor(
            CapabilityId.of(capabilityRef),
            CapabilityKind.EVENT_OPERATOR,
            "agents/sre-risk-assessor@1.0.0",
            Optional.empty(),
            "EventContext",
            "RiskDecision",
            AgentSideEffectProfile.PURE_INFERENCE,
            List.of("agent-capability"),
            Map.of("policyRef", "required"),
            Map.of("owner", "aep", "role", role));
    }

    private static ExternalAgentCapabilityRegistry registry(CapabilityDescriptor descriptor) {
        return new ExternalAgentCapabilityRegistry() {
            @Override
            public void register(ExternalAgentProvider provider) {
                throw new UnsupportedOperationException("not needed in compiler tests");
            }

            @Override
            public Optional<CapabilityDescriptor> find(CapabilityId capabilityId) {
                return descriptor.id().equals(capabilityId) ? Optional.of(descriptor) : Optional.empty();
            }

            @Override
            public List<CapabilityDescriptor> list() {
                return List.of(descriptor);
            }
        };
    }
}
