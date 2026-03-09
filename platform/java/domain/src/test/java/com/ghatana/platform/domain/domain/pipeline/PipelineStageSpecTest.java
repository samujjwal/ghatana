package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineStageSpecTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception {
        // Given
        PipelineStageSpec original = PipelineStageSpec.builder()
                .name("test-stage")
                .type(PipelineStageSpec.StageType.PATTERN)
                .connectorId("event-cloud-source-1")
                .workflow(List.of(
                    AgentSpec.builder()
                        .id("agent-1")
                        .agent("test-agent")
                        .build()
                ))
                .build();

        // When
        String yaml = yamlMapper.writeValueAsString(original);
        PipelineStageSpec deserialized = yamlMapper.readValue(yaml, PipelineStageSpec.class);

        // Then
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getName()).isEqualTo("test-stage");
        assertThat(deserialized.getType()).isEqualTo(PipelineStageSpec.StageType.PATTERN);
        assertThat(deserialized.getConnectorId()).isEqualTo("event-cloud-source-1");
        assertThat(deserialized.getWorkflow()).hasSize(1);
        assertThat(deserialized.getWorkflow().get(0).getId()).isEqualTo("agent-1");
    }

    @Test
    void shouldHandleNullValues() {
        // Given
        PipelineStageSpec stageSpec = new PipelineStageSpec();
        
        // Then
        assertThat(stageSpec.getName()).isNull();
        assertThat(stageSpec.getWorkflow()).isNull();
    }
}
