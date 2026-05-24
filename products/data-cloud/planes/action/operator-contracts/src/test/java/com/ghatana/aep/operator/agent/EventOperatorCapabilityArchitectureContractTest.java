package com.ghatana.aep.operator.agent;

import com.ghatana.aep.agent.capability.AgentCapability;
import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.CapabilityKind;
import com.ghatana.aep.agent.capability.EventOperatorCapability;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentType;
import com.ghatana.aep.operator.contract.EventOperator;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EventOperatorCapabilityArchitectureContractTest {

    @Test
    void eventOperatorCapabilityIsCapabilityAndEventOperator() {
        assertThat(AgentCapability.class).isAssignableFrom(EventOperatorCapability.class);
        assertThat(EventOperator.class).isAssignableFrom(EventOperatorCapability.class);
    }

    @Test
    void capabilityDescriptorsRepresentCanonicalAgentTypes() {
        assertDescriptor(CapabilityKind.DETERMINISTIC, AgentType.DETERMINISTIC);
        assertDescriptor(CapabilityKind.PROBABILISTIC, AgentType.PROBABILISTIC);
        assertDescriptor(CapabilityKind.STREAM_PROCESSOR, AgentType.STREAM_PROCESSOR);
        assertDescriptor(CapabilityKind.PLANNING, AgentType.PLANNING);
        assertDescriptor(CapabilityKind.EXTERNAL, AgentType.CUSTOM);
        assertDescriptor(CapabilityKind.HUMAN_REVIEW, AgentType.CUSTOM);
    }

    @Test
    void concreteAgentCapabilitiesExposeCanonicalCapabilityRoles() {
        AgentPredicateOperator predicate = new AgentPredicateOperator(
            OperatorId.of("tenant-a", "agent", "predicate", "1.0.0"),
            "agents/predicate@1.0.0",
            "PredicateInput",
            "PredicateOutput",
            request -> Promise.of(Map.of()));
        AgentActionOperator action = new AgentActionOperator(
            OperatorId.of("tenant-a", "agent", "action", "1.0.0"),
            "agents/action@1.0.0",
            "ActionInput",
            "ActionOutput",
            request -> Promise.of(Map.of()));

        assertThat(predicate.kind()).isEqualTo(OperatorKind.AGENT_PREDICATE);
        assertThat(agentOperator(new AgentEnrichmentOperator(
            OperatorId.of("tenant-a", "agent", "enrich", "1.0.0"),
            "agents/enrich@1.0.0",
            "EnrichInput",
            "EnrichOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_ENRICH);
        assertThat(agentOperator(new AgentExtractOperator(
            OperatorId.of("tenant-a", "agent", "extract", "1.0.0"),
            "agents/extract@1.0.0",
            "ExtractInput",
            "ExtractOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_EXTRACT);
        assertThat(agentOperator(new AgentPatternSynthesisOperator(
            OperatorId.of("tenant-a", "agent", "synthesis", "1.0.0"),
            "agents/synthesis@1.0.0",
            "SynthesisInput",
            "PatternSuggestion",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_PATTERN_SYNTHESIS);
        assertThat(agentOperator(new AgentExplanationOperator(
            OperatorId.of("tenant-a", "agent", "explain", "1.0.0"),
            "agents/explain@1.0.0",
            "ExplanationInput",
            "ExplanationOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_EXPLANATION);
        assertThat(agentOperator(new AgentReviewOperator(
            OperatorId.of("tenant-a", "agent", "review", "1.0.0"),
            "agents/review@1.0.0",
            "ReviewInput",
            "ReviewOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_REVIEW);
        assertThat(action.kind()).isEqualTo(OperatorKind.AGENT_ACTION);
        assertThat(agentOperator(new AgentReflectionOperator(
            OperatorId.of("tenant-a", "agent", "reflection", "1.0.0"),
            "agents/reflection@1.0.0",
            "ReflectionInput",
            "ReflectionOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_REFLECTION);
    }

    @Test
    void sideEffectingAgentActionRequiresGovernancePolicies() {
        AgentActionOperator action = new AgentActionOperator(
            OperatorId.of("tenant-a", "agent", "action", "1.0.0"),
            "agents/action@1.0.0",
            "ActionInput",
            "ActionOutput",
            request -> Promise.of(Map.of()));

        assertThat(action.validate(spec(Map.of()), null).errors())
            .contains("AGENT_ACTION requires toolPolicy");
        assertThat(action.validate(spec(Map.of(
            "toolPolicy", Map.of("allowedTools", List.of("ticket.create")),
            "approvalPolicy", Map.of("mode", "human_required"))), null).errors())
            .contains("AGENT_ACTION requires auditPolicy");
    }

    private static OperatorSpec spec(Map<String, Object> policies) {
        return new OperatorSpec(
            "tenant-a:agent:action:1.0.0",
            OperatorKind.AGENT_ACTION,
            "ActionInput",
            "ActionOutput",
            Map.of(),
            policies);
    }

    private static OperatorKind agentOperator(EventOperator<Map<String, Object>, Map<String, Object>> operator) {
        return operator.kind();
    }

    private static void assertDescriptor(CapabilityKind capabilityKind, AgentType agentType) {
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
            CapabilityId.of("agents/" + agentType.name().toLowerCase(java.util.Locale.ROOT) + "@1.0.0/capabilities/main"),
            capabilityKind,
            "agents/" + agentType.name().toLowerCase(java.util.Locale.ROOT) + "@1.0.0",
            Optional.of(AgentDescriptor.builder()
                .agentId(agentType.name().toLowerCase(java.util.Locale.ROOT))
                .type(agentType)
                .build()),
            "schema://capability/input",
            "schema://capability/output",
            AgentSideEffectProfile.PURE_INFERENCE,
            List.of("agent-capability"),
            Map.of("policyRef", "required"),
            Map.of("owner", "aep"));

        assertThat(descriptor.kind()).isEqualTo(capabilityKind);
        assertThat(descriptor.agentDescriptor().orElseThrow().getType()).isEqualTo(agentType);
    }
}
