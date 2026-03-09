package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorSpecTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldSerializeAndDeserializeEventCloudSource() throws Exception {
        // Given
        ConnectorSpec original = ConnectorSpec.builder()
            .id("event-cloud-source-1")
            .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE)
            .endpoint("eventcloud-cluster-1")
            .topicOrStream("events-stream")
            .tenantId("tenant-test")
            .durable(true)
            .ordered(true)
            .maxInFlight(128)
            .properties(Map.of("compression", "snappy"))
            .tags(List.of("eventcloud", "source"))
            .build();

        // When
        String yaml = yamlMapper.writeValueAsString(original);
        ConnectorSpec deserialized = yamlMapper.readValue(yaml, ConnectorSpec.class);

        // Then
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getId()).isEqualTo("event-cloud-source-1");
        assertThat(deserialized.getType()).isEqualTo(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE);
        assertThat(deserialized.getEndpoint()).isEqualTo("eventcloud-cluster-1");
        assertThat(deserialized.getTopicOrStream()).isEqualTo("events-stream");
        assertThat(deserialized.getTenantId()).isEqualTo("tenant-test");
        assertThat(deserialized.getDurable()).isTrue();
        assertThat(deserialized.getOrdered()).isTrue();
        assertThat(deserialized.getMaxInFlight()).isEqualTo(128);
        assertThat(deserialized.getProperties()).containsEntry("compression", "snappy");
        assertThat(deserialized.getTags()).containsExactly("eventcloud", "source");
    }

    @Test
    void shouldHandleNullValues() {
        ConnectorSpec spec = new ConnectorSpec();

        assertThat(spec.getId()).isNull();
        assertThat(spec.getType()).isNull();
        assertThat(spec.getEndpoint()).isNull();
        assertThat(spec.getTopicOrStream()).isNull();
        assertThat(spec.getTenantId()).isNull();
        assertThat(spec.getDurable()).isNull();
        assertThat(spec.getOrdered()).isNull();
        assertThat(spec.getMaxInFlight()).isNull();
        assertThat(spec.getProperties()).isNull();
        assertThat(spec.getTags()).isNull();
    }
}
