package com.ghatana.platform.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSpecTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    @Test
    void shouldSerializeAndDeserializeFromYaml() throws Exception { // GH-90000
        AgentSpec original = AgentSpec.builder() // GH-90000
                .id("test-agent")
                .agent("test-agent-type")
                .role("test-role")
                .build(); // GH-90000

        String yaml = yamlMapper.writeValueAsString(original); // GH-90000
        AgentSpec deserialized = yamlMapper.readValue(yaml, AgentSpec.class); // GH-90000

        assertThat(deserialized).isEqualTo(original); // GH-90000
        assertThat(deserialized.getId()).isEqualTo("test-agent");
        assertThat(deserialized.getAgent()).isEqualTo("test-agent-type");
        assertThat(deserialized.getRole()).isEqualTo("test-role");
    }

    @Test
    void shouldHandleNullValues() { // GH-90000
        AgentSpec agentSpec = new AgentSpec(); // GH-90000

        assertThat(agentSpec.getId()).isNull(); // GH-90000
        assertThat(agentSpec.getAgent()).isNull(); // GH-90000
        assertThat(agentSpec.getRole()).isNull(); // GH-90000
    }

    @Test
    void shouldImplementEqualsAndHashCode() { // GH-90000
        AgentSpec spec1 = AgentSpec.builder() // GH-90000
                .id("test-1")
                .agent("test-agent")
                .build(); // GH-90000

        AgentSpec spec2 = AgentSpec.builder() // GH-90000
                .id("test-1")
                .agent("test-agent")
                .build(); // GH-90000

        AgentSpec spec3 = AgentSpec.builder() // GH-90000
                .id("test-2")
                .agent("test-agent")
                .build(); // GH-90000

        assertThat(spec1).isEqualTo(spec2); // GH-90000
        assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode()); // GH-90000
        assertThat(spec1).isNotEqualTo(spec3); // GH-90000
    }
}
