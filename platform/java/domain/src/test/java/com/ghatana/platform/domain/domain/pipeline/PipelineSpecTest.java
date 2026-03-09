package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSpecTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception {
        PipelineSpec original = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("stage-1")
                                .type(PipelineStageSpec.StageType.STREAM)
                                .workflow(List.of(
                                        AgentSpec.builder()
                                                .id("agent-1")
                                                .agent("test-agent")
                                                .build()
                                ))
                                .build()
                ))
                .tenantId("tenant-test")
                .environment("test")
                .tags(List.of("tag1"))
                .agentHints(Map.of("consumer", "ai-org"))
                .edges(List.of(
                    PipelineEdgeSpec.builder()
                        .fromStageId("stage-1")
                        .toStageId("stage-1") // Self-loop allowed in spec; runtime validation will enforce DAG rules
                        .label("primary")
                        .build()
                ))
                .connectors(List.of(
                    ConnectorSpec.builder()
                        .id("event-cloud-source-1")
                        .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE)
                        .endpoint("eventcloud-cluster-1")
                        .topicOrStream("events-stream")
                        .tenantId("tenant-test")
                        .build()
                ))
                .build();

        String yaml = yamlMapper.writeValueAsString(original);
        PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getStages()).hasSize(1);
        assertThat(deserialized.getStages().get(0).getName()).isEqualTo("stage-1");
        assertThat(deserialized.getStages().get(0).getType()).isEqualTo(PipelineStageSpec.StageType.STREAM);
        assertThat(deserialized.getTenantId()).isEqualTo("tenant-test");
        assertThat(deserialized.getEnvironment()).isEqualTo("test");
        assertThat(deserialized.getTags()).containsExactly("tag1");
        assertThat(deserialized.getAgentHints()).containsEntry("consumer", "ai-org");
        assertThat(deserialized.getEdges()).hasSize(1);
        assertThat(deserialized.getEdges().get(0).getFromStageId()).isEqualTo("stage-1");
        assertThat(deserialized.getConnectors()).hasSize(1);
        assertThat(deserialized.getConnectors().get(0).getId()).isEqualTo("event-cloud-source-1");
    }

    @Test
    void shouldHandleNullValues() {
        PipelineSpec pipelineSpec = new PipelineSpec();

        assertThat(pipelineSpec.getStages()).isNull();
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        PipelineSpec spec1 = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder().name("stage-1").build()
                ))
                .build();

        PipelineSpec spec2 = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder().name("stage-1").build()
                ))
                .build();

        PipelineSpec spec3 = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder().name("stage-2").build()
                ))
                .build();

        assertThat(spec1).isEqualTo(spec2);
        assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode());
        assertThat(spec1).isNotEqualTo(spec3);
    }
}
