package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineEdgeSpecTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception {
        // Given
        PipelineEdgeSpec original = PipelineEdgeSpec.builder()
            .fromStageId("stage-1")
            .toStageId("stage-2")
            .label("primary")
            .build();

        // When
        String yaml = yamlMapper.writeValueAsString(original);
        PipelineEdgeSpec deserialized = yamlMapper.readValue(yaml, PipelineEdgeSpec.class);

        // Then
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getFromStageId()).isEqualTo("stage-1");
        assertThat(deserialized.getToStageId()).isEqualTo("stage-2");
        assertThat(deserialized.getLabel()).isEqualTo("primary");
    }

    @Test
    void shouldHandleNullValues() {
        PipelineEdgeSpec edgeSpec = new PipelineEdgeSpec();

        assertThat(edgeSpec.getFromStageId()).isNull();
        assertThat(edgeSpec.getToStageId()).isNull();
        assertThat(edgeSpec.getLabel()).isNull();
    }
}
