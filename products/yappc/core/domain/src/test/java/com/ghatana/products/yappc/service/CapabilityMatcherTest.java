package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.agent.AgentName;
import com.ghatana.products.yappc.domain.agent.AgentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CapabilityMatcher.
 */
@DisplayName("CapabilityMatcher Tests")
/**
 * @doc.type class
 * @doc.purpose Handles capability matcher test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class CapabilityMatcherTest {

    private CapabilityMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new CapabilityMatcher();
    }

    @Test
    @DisplayName("Should find agents with all required capabilities")
    void shouldFindCapableAgents() {
        // GIVEN
        List<String> requiredCapabilities = List.of("java", "testing");

        AgentMetadata agent1 = AgentMetadata.builder()
                .name(AgentName.COPILOT_AGENT)
                .version("1.0")
                .description("agent1")
                .capabilities(List.of("java", "testing", "junit"))
                .supportedModels(List.of("test-model"))
                .latencySLA(1000)
                .costPerRequest(null)
                .build();

        AgentMetadata agent2 = AgentMetadata.builder()
                .name(AgentName.SEARCH_AGENT)
                .version("1.0")
                .description("agent2")
                .capabilities(List.of("java", "python"))
                .supportedModels(List.of("test-model"))
                .latencySLA(1000)
                .costPerRequest(null)
                .build();

        AgentMetadata agent3 = AgentMetadata.builder()
                .name(AgentName.CODE_GENERATOR_AGENT)
                .version("1.0")
                .description("agent3")
                .capabilities(List.of("java", "testing"))
                .supportedModels(List.of("test-model"))
                .latencySLA(1000)
                .costPerRequest(null)
                .build();

        List<AgentMetadata> availableAgents = List.of(agent1, agent2, agent3);

        // WHEN
        List<AgentMetadata> capableAgents = matcher.findCapableAgents(
                requiredCapabilities,
                availableAgents
        );

        // THEN
        assertThat(capableAgents).hasSize(2);
        assertThat(capableAgents).extracting(AgentMetadata::name)
                .containsExactlyInAnyOrder(AgentName.COPILOT_AGENT, AgentName.CODE_GENERATOR_AGENT);
    }

    @Test
    @DisplayName("Should return empty list when no agents match")
    void shouldReturnEmptyWhenNoMatch() {
        // GIVEN
        List<String> requiredCapabilities = List.of("rust", "webassembly");

        AgentMetadata agent1 = AgentMetadata.builder()
                .name(AgentName.COPILOT_AGENT)
                .version("1.0")
                .description("agent1")
                .capabilities(List.of("java", "testing"))
                .supportedModels(List.of("test-model"))
                .latencySLA(1000)
                .costPerRequest(null)
                .build();

        List<AgentMetadata> availableAgents = List.of(agent1);

        // WHEN
        List<AgentMetadata> capableAgents = matcher.findCapableAgents(
                requiredCapabilities,
                availableAgents
        );

        // THEN
        assertThat(capableAgents).isEmpty();
    }

    @Test
    @DisplayName("Should sort agents by capability score")
    void shouldSortByScore() {
        // GIVEN
        List<String> requiredCapabilities = List.of("java", "testing");

        // Specialist agent (only required capabilities)
        AgentMetadata specialist = AgentMetadata.builder()
                .name(AgentName.SEARCH_AGENT)
                .version("1.0")
                .description("specialist")
                .capabilities(List.of("java", "testing"))
                .supportedModels(List.of("test-model"))
                .latencySLA(100) // Low latency
                .costPerRequest(0.01) // Low cost
                .build();

        // Generalist agent (many extra capabilities)
        AgentMetadata generalist = AgentMetadata.builder()
                .name(AgentName.COPILOT_AGENT)
                .version("1.0")
                .description("generalist")
                .capabilities(List.of("java", "testing", "python", "go", "rust", "kotlin"))
                .supportedModels(List.of("test-model"))
                .latencySLA(500) // Higher latency
                .costPerRequest(0.05) // Higher cost
                .build();

        List<AgentMetadata> availableAgents = List.of(generalist, specialist);

        // WHEN
        List<AgentMetadata> capableAgents = matcher.findCapableAgents(
                requiredCapabilities,
                availableAgents
        );

        // THEN - specialist should be ranked first
        assertThat(capableAgents).hasSize(2);
                assertThat(capableAgents.get(0).name()).isEqualTo(AgentName.SEARCH_AGENT);
                assertThat(capableAgents.get(1).name()).isEqualTo(AgentName.COPILOT_AGENT);
    }

    @Test
    @DisplayName("Should handle single capability requirement")
    void shouldHandleSingleCapability() {
        // GIVEN
        List<String> requiredCapabilities = List.of("java");

        AgentMetadata agent = AgentMetadata.builder()
                .name(AgentName.COPILOT_AGENT)
                .version("1.0")
                .description("java-agent")
                .capabilities(List.of("java", "kotlin", "scala"))
                .supportedModels(List.of("test-model"))
                .latencySLA(1000)
                .costPerRequest(null)
                .build();

        List<AgentMetadata> availableAgents = List.of(agent);

        // WHEN
        List<AgentMetadata> capableAgents = matcher.findCapableAgents(
                requiredCapabilities,
                availableAgents
        );

        // THEN
        assertThat(capableAgents).hasSize(1);
                assertThat(capableAgents.get(0).name()).isEqualTo(AgentName.COPILOT_AGENT);
    }
}
