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
    void eventOperatorCapabilityExposesCapabilityId() {
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
            CapabilityId.of("agents/predicate@1.0.0/capabilities/main"),
            CapabilityKind.DETERMINISTIC,
            "agents/predicate@1.0.0",
            Optional.of(AgentDescriptor.builder()
                .agentId("predicate")
                .type(AgentType.DETERMINISTIC)
                .build()),
            "schema://capability/input",
            "schema://capability/output",
            AgentSideEffectProfile.PURE_INFERENCE,
            List.of("agent-capability"),
            Map.of("policyRef", "required"),
            Map.of("owner", "aep"));

        assertThat(descriptor.id()).isNotNull();
        assertThat(descriptor.id().value()).isEqualTo("agents/predicate@1.0.0/capabilities/main");
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
        assertThat(eventOperator(new AgentEnrichmentOperator(
            OperatorId.of("tenant-a", "agent", "enrich", "1.0.0"),
            "agents/enrich@1.0.0",
            "EnrichInput",
            "EnrichOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_ENRICH);
        assertThat(eventOperator(new AgentExtractOperator(
            OperatorId.of("tenant-a", "agent", "extract", "1.0.0"),
            "agents/extract@1.0.0",
            "ExtractInput",
            "ExtractOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_EXTRACT);
        assertThat(eventOperator(new AgentPatternSynthesisOperator(
            OperatorId.of("tenant-a", "agent", "synthesis", "1.0.0"),
            "agents/synthesis@1.0.0",
            "SynthesisInput",
            "PatternSuggestion",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_PATTERN_SYNTHESIS);
        assertThat(eventOperator(new AgentExplanationOperator(
            OperatorId.of("tenant-a", "agent", "explain", "1.0.0"),
            "agents/explain@1.0.0",
            "ExplanationInput",
            "ExplanationOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_EXPLANATION);
        assertThat(eventOperator(new AgentReviewOperator(
            OperatorId.of("tenant-a", "agent", "review", "1.0.0"),
            "agents/review@1.0.0",
            "ReviewInput",
            "ReviewOutput",
            request -> Promise.of(Map.of()))))
            .isEqualTo(OperatorKind.AGENT_REVIEW);
        assertThat(action.kind()).isEqualTo(OperatorKind.AGENT_ACTION);
        assertThat(eventOperator(new AgentReflectionOperator(
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

    @Test
    void agentActionRequiresAllRequiredPolicies() {
        AgentActionOperator action = new AgentActionOperator(
            OperatorId.of("tenant-a", "agent", "action", "1.0.0"),
            "agents/action@1.0.0",
            "ActionInput",
            "ActionOutput",
            request -> Promise.of(Map.of()));

        // Missing tool policy
        assertThat(action.validate(spec(Map.of(
            "approvalPolicy", Map.of("mode", "human_required"),
            "auditPolicy", Map.of("enabled", true),
            "idempotencyPolicy", Map.of("keyRequired", true))), null).errors())
            .contains("AGENT_ACTION requires toolPolicy");

        // Missing approval policy
        assertThat(action.validate(spec(Map.of(
            "toolPolicy", Map.of("allowedTools", List.of("ticket.create")),
            "auditPolicy", Map.of("enabled", true),
            "idempotencyPolicy", Map.of("keyRequired", true))), null).errors())
            .contains("AGENT_ACTION requires approvalPolicy");

        // Missing audit policy
        assertThat(action.validate(spec(Map.of(
            "toolPolicy", Map.of("allowedTools", List.of("ticket.create")),
            "approvalPolicy", Map.of("mode", "human_required"),
            "idempotencyPolicy", Map.of("keyRequired", true))), null).errors())
            .contains("AGENT_ACTION requires auditPolicy");

        // Missing idempotency policy
        assertThat(action.validate(spec(Map.of(
            "toolPolicy", Map.of("allowedTools", List.of("ticket.create")),
            "approvalPolicy", Map.of("mode", "human_required"),
            "auditPolicy", Map.of("enabled", true))), null).errors())
            .contains("AGENT_ACTION requires idempotencyPolicy");
    }

    @Test
    void agentPredicateMustNotDeclareMutatingToolPolicy() {
        AgentPredicateOperator predicate = new AgentPredicateOperator(
            OperatorId.of("tenant-a", "agent", "predicate", "1.0.0"),
            "agents/predicate@1.0.0",
            "PredicateInput",
            "PredicateOutput",
            request -> Promise.of(Map.of()));

        assertThat(predicate.validate(spec(OperatorKind.AGENT_PREDICATE, Map.of(
            "toolPolicy", Map.of("allowedTools", List.of("some.tool")))), null).errors())
            .contains("AGENT_PREDICATE must not declare mutating toolPolicy");
    }

    @Test
    void agentReviewMustNotSelfApproveHighRiskProductionChanges() {
        AgentReviewOperator review = new AgentReviewOperator(
            OperatorId.of("tenant-a", "agent", "review", "1.0.0"),
            "agents/review@1.0.0",
            "ReviewInput",
            "ReviewOutput",
            request -> Promise.of(Map.of()));

        // High-risk production changes require external approval
        assertThat(review.validate(spec(OperatorKind.AGENT_REVIEW, Map.of(
            "riskLevel", "HIGH",
            "mode", "production",
            "selfApprovalAllowed", false)), null).errors())
            .contains("AGENT_REVIEW cannot self-approve high-risk production changes");
    }

    @Test
    void agentPatternSynthesisEmitsSuggestionNotActiveDeployment() {
        AgentPatternSynthesisOperator synthesis = new AgentPatternSynthesisOperator(
            OperatorId.of("tenant-a", "agent", "synthesis", "1.0.0"),
            "agents/synthesis@1.0.0",
            "SynthesisInput",
            "PatternSuggestion",
            request -> Promise.of(Map.of()));

        // Pattern synthesis must emit suggestions, not deploy directly
        assertThat(synthesis.validate(spec(OperatorKind.AGENT_PATTERN_SYNTHESIS, Map.of(
            "deploymentMode", "direct")), null).errors())
            .contains("AGENT_PATTERN_SYNTHESIS must emit pattern suggestion, not active deployment");
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

    private static OperatorSpec spec(OperatorKind kind, Map<String, Object> policies) {
        return new OperatorSpec(
            "tenant-a:agent:" + kind.name().toLowerCase(java.util.Locale.ROOT) + ":1.0.0",
            kind,
            kind.name() + "Input",
            kind.name() + "Output",
            Map.of(),
            policies);
    }

    private static OperatorKind eventOperator(EventOperator<Map<String, Object>, Map<String, Object>> operator) {
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
