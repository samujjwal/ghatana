package com.ghatana.platform.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineStageSpecTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception { // GH-90000
        // Given
        PipelineStageSpec original = PipelineStageSpec.builder() // GH-90000
                .name("test-stage [GH-90000]")
                .type(PipelineStageSpec.StageType.PATTERN) // GH-90000
                .connectorId("event-cloud-source-1 [GH-90000]")
                .workflow(List.of( // GH-90000
                    AgentSpec.builder() // GH-90000
                        .id("agent-1 [GH-90000]")
                        .agent("test-agent [GH-90000]")
                        .build() // GH-90000
                ))
                .build(); // GH-90000

        // When
        String yaml = yamlMapper.writeValueAsString(original); // GH-90000
        PipelineStageSpec deserialized = yamlMapper.readValue(yaml, PipelineStageSpec.class); // GH-90000

        // Then
        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getName()).isEqualTo("test-stage [GH-90000]");
        assertThat(deserialized.getType()).isEqualTo(PipelineStageSpec.StageType.PATTERN); // GH-90000
        assertThat(deserialized.getConnectorId()).isEqualTo("event-cloud-source-1 [GH-90000]");
        assertThat(deserialized.getWorkflow()).hasSize(1); // GH-90000
        assertThat(deserialized.getWorkflow().get(0).getId()).isEqualTo("agent-1 [GH-90000]");
    }

    @Test
    void shouldHandleNullValues() { // GH-90000
        // Given
        PipelineStageSpec stageSpec = new PipelineStageSpec(); // GH-90000

        // Then
        assertThat(stageSpec.getName()).isNull(); // GH-90000
        assertThat(stageSpec.getWorkflow()).isNull(); // GH-90000
    }
}
