package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSpecTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception {
        AgentSpec original = AgentSpec.builder()
                .id("test-agent")
                .agent("test-agent-type")
                .role("test-role")
                .build();

        String yaml = yamlMapper.writeValueAsString(original);
        AgentSpec deserialized = yamlMapper.readValue(yaml, AgentSpec.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getId()).isEqualTo("test-agent");
        assertThat(deserialized.getAgent()).isEqualTo("test-agent-type");
        assertThat(deserialized.getRole()).isEqualTo("test-role");
    }

    @Test
    void shouldHandleNullValues() {
        AgentSpec agentSpec = new AgentSpec();

        assertThat(agentSpec.getId()).isNull();
        assertThat(agentSpec.getAgent()).isNull();
        assertThat(agentSpec.getRole()).isNull();
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        AgentSpec spec1 = AgentSpec.builder()
                .id("test-1")
                .agent("test-agent")
                .build();

        AgentSpec spec2 = AgentSpec.builder()
                .id("test-1")
                .agent("test-agent")
                .build();

        AgentSpec spec3 = AgentSpec.builder()
                .id("test-2")
                .agent("test-agent")
                .build();

        assertThat(spec1).isEqualTo(spec2);
        assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode());
        assertThat(spec1).isNotEqualTo(spec3);
    }
}
