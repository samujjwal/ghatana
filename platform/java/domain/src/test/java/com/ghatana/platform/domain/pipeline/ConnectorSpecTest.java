package com.ghatana.platform.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorSpecTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    @Test
    void shouldSerializeAndDeserializeEventCloudSource() throws Exception { // GH-90000
        // Given
        ConnectorSpec original = ConnectorSpec.builder() // GH-90000
            .id("event-cloud-source-1 [GH-90000]")
            .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE) // GH-90000
            .endpoint("eventcloud-cluster-1 [GH-90000]")
            .topicOrStream("events-stream [GH-90000]")
            .tenantId("tenant-test [GH-90000]")
            .durable(true) // GH-90000
            .ordered(true) // GH-90000
            .maxInFlight(128) // GH-90000
            .properties(Map.of("compression", "snappy")) // GH-90000
            .tags(List.of("eventcloud", "source")) // GH-90000
            .build(); // GH-90000

        // When
        String yaml = yamlMapper.writeValueAsString(original); // GH-90000
        ConnectorSpec deserialized = yamlMapper.readValue(yaml, ConnectorSpec.class); // GH-90000

        // Then
        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getId()).isEqualTo("event-cloud-source-1 [GH-90000]");
        assertThat(deserialized.getType()).isEqualTo(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE); // GH-90000
        assertThat(deserialized.getEndpoint()).isEqualTo("eventcloud-cluster-1 [GH-90000]");
        assertThat(deserialized.getTopicOrStream()).isEqualTo("events-stream [GH-90000]");
        assertThat(deserialized.getTenantId()).isEqualTo("tenant-test [GH-90000]");
        assertThat(deserialized.getDurable()).isTrue(); // GH-90000
        assertThat(deserialized.getOrdered()).isTrue(); // GH-90000
        assertThat(deserialized.getMaxInFlight()).isEqualTo(128); // GH-90000
        assertThat(deserialized.getProperties()).containsEntry("compression", "snappy"); // GH-90000
        assertThat(deserialized.getTags()).containsExactly("eventcloud", "source"); // GH-90000
    }

    @Test
    void shouldHandleNullValues() { // GH-90000
        ConnectorSpec spec = new ConnectorSpec(); // GH-90000

        assertThat(spec.getId()).isNull(); // GH-90000
        assertThat(spec.getType()).isNull(); // GH-90000
        assertThat(spec.getEndpoint()).isNull(); // GH-90000
        assertThat(spec.getTopicOrStream()).isNull(); // GH-90000
        assertThat(spec.getTenantId()).isNull(); // GH-90000
        assertThat(spec.getDurable()).isNull(); // GH-90000
        assertThat(spec.getOrdered()).isNull(); // GH-90000
        assertThat(spec.getMaxInFlight()).isNull(); // GH-90000
        assertThat(spec.getProperties()).isNull(); // GH-90000
        assertThat(spec.getTags()).isNull(); // GH-90000
    }
}
