package com.ghatana.aep.pattern.runtime;

import com.ghatana.aep.pattern.spec.CompiledPattern;
import com.ghatana.aep.pattern.spec.PatternSpecCompiler;
import com.ghatana.core.pipeline.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertThat(pipeline.getStages()).anySatisfy(stage -> {
            assertThat(stage.stageId()).isEqualTo("root-1");
            assertThat(stage.operatorId().getType()).isEqualTo("agent-capability");
            assertThat(stage.config()).containsEntry("operatorKind", "AGENT_PREDICATE");
            assertThat(stage.config()).containsEntry("agentRef", "agents/sre-risk-assessor@1.0.0");
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
