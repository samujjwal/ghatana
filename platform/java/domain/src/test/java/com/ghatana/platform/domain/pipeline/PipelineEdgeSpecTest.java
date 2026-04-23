package com.ghatana.platform.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineEdgeSpecTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception { // GH-90000
        // Given
        PipelineEdgeSpec original = PipelineEdgeSpec.builder() // GH-90000
            .fromStageId("stage-1")
            .toStageId("stage-2")
            .label("primary")
            .build(); // GH-90000

        // When
        String yaml = yamlMapper.writeValueAsString(original); // GH-90000
        PipelineEdgeSpec deserialized = yamlMapper.readValue(yaml, PipelineEdgeSpec.class); // GH-90000

        // Then
        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getFromStageId()).isEqualTo("stage-1");
        assertThat(deserialized.getToStageId()).isEqualTo("stage-2");
        assertThat(deserialized.getLabel()).isEqualTo("primary");
    }

    @Test
    void shouldHandleNullValues() { // GH-90000
        PipelineEdgeSpec edgeSpec = new PipelineEdgeSpec(); // GH-90000

        assertThat(edgeSpec.getFromStageId()).isNull(); // GH-90000
        assertThat(edgeSpec.getToStageId()).isNull(); // GH-90000
        assertThat(edgeSpec.getLabel()).isNull(); // GH-90000
    }
}
