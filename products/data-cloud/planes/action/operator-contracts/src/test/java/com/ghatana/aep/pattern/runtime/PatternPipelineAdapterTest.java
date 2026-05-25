package com.ghatana.aep.pattern.runtime;

import com.ghatana.aep.agent.capability.AgentCapability;
import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.CapabilityKind;
import com.ghatana.aep.agent.capability.CapabilityResolver;
import com.ghatana.aep.pattern.spec.CompiledPattern;
import com.ghatana.aep.pattern.spec.PatternSpecCompiler;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.core.pipeline.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PatternPipelineAdapterTest {

    @Test
    void adaptsCompiledPatternToPipelineWithEventCloudSourceSinkAndAgentStage() {
        CompiledPattern compiled = PatternSpecCompiler.compile(validSpec(Map.of(
            "operator", "SEQ",
            "within", "PT30M",
            "operands", List.of(
                Map.of("event", "deploy.started"),
                Map.of(
                    "operator", "AGENT_PREDICATE",
                    "agentRef", "agents/sre-risk-assessor@1.0.0",
                    "capabilityRef", "agents/sre-risk-assessor@1.0.0/capability",
                    "outputSchema", "RiskDecision")))));

        Pipeline pipeline = PatternPipelineAdapter.toPipeline(compiled);

        assertThat(pipeline.getId()).isEqualTo("pattern-runtime-sre-risk-sequence");
        assertThat(pipeline.getMetadata()).containsEntry("tenantId", "tenant-a");
        assertThat(pipeline.getStages()).extracting("stageId")
            .containsExactly("eventcloud-source", "root", "root-0", "root-1", "eventcloud-sink");
        assertThat(pipeline.getStages()).filteredOn(stage -> stage.stageId().equals("root-1")).first().satisfies(stage -> {
            assertThat(stage.operatorId().getType()).isEqualTo("agent-capability");
            assertThat(stage.config()).containsEntry("operatorKind", "AGENT_PREDICATE");
            assertThat(stage.config()).containsEntry(
                "capabilityRef",
                "agents/sre-risk-assessor@1.0.0/capability");
        });
        assertThat(pipeline.getEdges()).anySatisfy(edge -> {
            assertThat(edge.from()).isEqualTo("eventcloud-source");
            assertThat(edge.to()).isEqualTo("root-0");
        });
        assertThat(pipeline.getEdges()).anySatisfy(edge -> {
            assertThat(edge.from()).isEqualTo("root-1");
            assertThat(edge.to()).isEqualTo("root");
        });
        assertThat(pipeline.getEdges()).anySatisfy(edge -> {
            assertThat(edge.from()).isEqualTo("root");
            assertThat(edge.to()).isEqualTo("eventcloud-sink");
        });
        assertThat(pipeline.validate().isValid()).isTrue();
    }

    @Test
    void resolvesLocalAndExternalCapabilityRefsIntoPipelineStageMetadata() {
        String localRef = "agents/local-risk@1.0.0/capabilities/agent_predicate";
        String externalRef = "external/verifier@2.0.0/capabilities/agent_review";
        CompiledPattern compiled = PatternSpecCompiler.compile(validSpec(Map.of(
            "operator", "AND",
            "operands", List.of(
                Map.of(
                    "operator", "AGENT_PREDICATE",
                    "capabilityRef", localRef,
                    "outputSchema", "RiskDecision"),
                Map.of(
                    "operator", "AGENT_REVIEW",
                    "capabilityRef", externalRef,
                    "outputSchema", "ReviewDecision")))));
        CapabilityResolver resolver = new MapCapabilityResolver(Map.of(
            localRef, descriptor(localRef, CapabilityKind.EVENT_OPERATOR, "agents/local-risk@1.0.0", "local"),
            externalRef, descriptor(externalRef, CapabilityKind.EXTERNAL, "external/verifier@2.0.0", "external")));

        Pipeline pipeline = PatternPipelineAdapter.toPipeline(compiled, resolver);

        assertThat(pipeline.getStages()).anySatisfy(stage -> {
            assertThat(stage.stageId()).isEqualTo("root-0");
            assertThat(stage.config())
                .containsEntry("capabilityRef", localRef)
                .containsEntry("capabilityBinding", "resolved")
                .containsEntry("capabilityKind", "EVENT_OPERATOR")
                .containsEntry("capabilityAgentRef", "agents/local-risk@1.0.0")
                .containsEntry("capabilityOutputSchema", "RiskDecision")
                .containsEntry("capabilitySideEffectProfile", "PURE_INFERENCE");
            assertThat(stage.config().get("capabilityMetadata"))
                .isEqualTo(Map.of("provider", "local"));
        });
        assertThat(pipeline.getStages()).anySatisfy(stage -> {
            assertThat(stage.stageId()).isEqualTo("root-1");
            assertThat(stage.config())
                .containsEntry("capabilityRef", externalRef)
                .containsEntry("capabilityBinding", "resolved")
                .containsEntry("capabilityKind", "EXTERNAL")
                .containsEntry("capabilityAgentRef", "external/verifier@2.0.0");
            assertThat(stage.config().get("capabilityMetadata"))
                .isEqualTo(Map.of("provider", "external"));
        });
        assertThat(pipeline.validate().isValid()).isTrue();
    }

    @Test
    void rejectsUnknownCapabilityRefWhenResolverIsProvided() {
        CompiledPattern compiled = PatternSpecCompiler.compile(validSpec(Map.of(
            "operator", "AGENT_PREDICATE",
            "capabilityRef", "agents/missing@1.0.0/capabilities/agent_predicate",
            "outputSchema", "RiskDecision")));
        CapabilityResolver resolver = new MapCapabilityResolver(Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PatternPipelineAdapter.toPipeline(compiled, resolver))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown capabilityRef");
    }

    private static Map<String, Object> validSpec(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "sre-risk-sequence", "namespace", "sre", "version", "1.0.0", "tenantId", "tenant-a", "owner", "sre"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true, "tracing", true)));
    }

    private static CapabilityDescriptor descriptor(
            String id,
            CapabilityKind kind,
            String agentRef,
            String provider) {
        return new CapabilityDescriptor(
            CapabilityId.of(id),
            kind,
            agentRef,
            Optional.empty(),
            "EventContext",
            id.contains("review") ? "ReviewDecision" : "RiskDecision",
            AgentSideEffectProfile.PURE_INFERENCE,
            List.of("agent.capability"),
            Map.of("replayPolicy", Map.of("mode", "deterministic")),
            Map.of("provider", provider));
    }

    private record MapCapabilityResolver(Map<String, CapabilityDescriptor> descriptors) implements CapabilityResolver {
        @Override
        public Optional<CapabilityDescriptor> describe(CapabilityId capabilityId) {
            return Optional.ofNullable(descriptors.get(capabilityId.value()));
        }

        @Override
        public <I, O> Optional<AgentCapability<I, O>> resolve(
                CapabilityId capabilityId,
                Class<I> inputType,
                Class<O> outputType) {
            return Optional.empty();
        }
    }
}
