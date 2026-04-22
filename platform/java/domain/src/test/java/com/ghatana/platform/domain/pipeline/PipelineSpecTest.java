package com.ghatana.platform.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSpecTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception { // GH-90000
        PipelineSpec original = PipelineSpec.builder() // GH-90000
                .stages(List.of( // GH-90000
                        PipelineStageSpec.builder() // GH-90000
                                .name("stage-1 [GH-90000]")
                                .type(PipelineStageSpec.StageType.STREAM) // GH-90000
                                .workflow(List.of( // GH-90000
                                        AgentSpec.builder() // GH-90000
                                                .id("agent-1 [GH-90000]")
                                                .agent("test-agent [GH-90000]")
                                                .build() // GH-90000
                                ))
                                .build() // GH-90000
                ))
                .tenantId("tenant-test [GH-90000]")
                .environment("test [GH-90000]")
                .tags(List.of("tag1 [GH-90000]"))
                .agentHints(Map.of("consumer", "ai-org")) // GH-90000
                .edges(List.of( // GH-90000
                    PipelineEdgeSpec.builder() // GH-90000
                        .fromStageId("stage-1 [GH-90000]")
                        .toStageId("stage-1 [GH-90000]") // Self-loop allowed in spec; runtime validation will enforce DAG rules
                        .label("primary [GH-90000]")
                        .build() // GH-90000
                ))
                .connectors(List.of( // GH-90000
                    ConnectorSpec.builder() // GH-90000
                        .id("event-cloud-source-1 [GH-90000]")
                        .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE) // GH-90000
                        .endpoint("eventcloud-cluster-1 [GH-90000]")
                        .topicOrStream("events-stream [GH-90000]")
                        .tenantId("tenant-test [GH-90000]")
                        .build() // GH-90000
                ))
                .build(); // GH-90000

        String yaml = yamlMapper.writeValueAsString(original); // GH-90000
        PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class); // GH-90000

        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getStages()).hasSize(1); // GH-90000
        assertThat(deserialized.getStages().get(0).getName()).isEqualTo("stage-1 [GH-90000]");
        assertThat(deserialized.getStages().get(0).getType()).isEqualTo(PipelineStageSpec.StageType.STREAM); // GH-90000
        assertThat(deserialized.getTenantId()).isEqualTo("tenant-test [GH-90000]");
        assertThat(deserialized.getEnvironment()).isEqualTo("test [GH-90000]");
        assertThat(deserialized.getTags()).containsExactly("tag1 [GH-90000]");
        assertThat(deserialized.getAgentHints()).containsEntry("consumer", "ai-org"); // GH-90000
        assertThat(deserialized.getEdges()).hasSize(1); // GH-90000
        assertThat(deserialized.getEdges().get(0).getFromStageId()).isEqualTo("stage-1 [GH-90000]");
        assertThat(deserialized.getConnectors()).hasSize(1); // GH-90000
        assertThat(deserialized.getConnectors().get(0).getId()).isEqualTo("event-cloud-source-1 [GH-90000]");
    }

    @Test
    void shouldHandleNullValues() { // GH-90000
        PipelineSpec pipelineSpec = new PipelineSpec(); // GH-90000

        assertThat(pipelineSpec.getStages()).isNull(); // GH-90000
    }

    @Test
    void shouldImplementEqualsAndHashCode() { // GH-90000
        PipelineSpec spec1 = PipelineSpec.builder() // GH-90000
                .stages(List.of( // GH-90000
                        PipelineStageSpec.builder().name("stage-1 [GH-90000]").build()
                ))
                .build(); // GH-90000

        PipelineSpec spec2 = PipelineSpec.builder() // GH-90000
                .stages(List.of( // GH-90000
                        PipelineStageSpec.builder().name("stage-1 [GH-90000]").build()
                ))
                .build(); // GH-90000

        PipelineSpec spec3 = PipelineSpec.builder() // GH-90000
                .stages(List.of( // GH-90000
                        PipelineStageSpec.builder().name("stage-2 [GH-90000]").build()
                ))
                .build(); // GH-90000

        assertThat(spec1).isEqualTo(spec2); // GH-90000
        assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode()); // GH-90000
        assertThat(spec1).isNotEqualTo(spec3); // GH-90000
    }
}
