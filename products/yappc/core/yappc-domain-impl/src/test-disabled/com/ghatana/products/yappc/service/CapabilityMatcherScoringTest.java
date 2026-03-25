package com.ghatana.products.yappc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.products.yappc.domain.agent.AgentMetadata;
import com.ghatana.products.yappc.domain.agent.AgentName;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for CapabilityMatcher — agent scoring and matching logic.
 *
 * @doc.type class
 * @doc.purpose Unit tests for CapabilityMatcher scoring
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CapabilityMatcher Tests")
class CapabilityMatcherTest {

  private CapabilityMatcher matcher;

  @BeforeEach
  void setUp() {
    matcher = new CapabilityMatcher();
  }

  @Nested
  @DisplayName("Agent Matching")
  class AgentMatching {

    @Test
    @DisplayName("Should find agents with all required capabilities")
    void shouldFindFullMatch() {
      AgentMetadata agent = AgentMetadata.builder()
          .name(AgentName.CODE_GENERATOR_AGENT)
          .version("1.0")
          .description("Code gen")
          .capabilities(List.of("java", "code-generation", "refactoring"))
          .supportedModels(List.of("gpt-4"))
          .latencySLA(5000)
          .build();

      List<AgentMetadata> results = matcher.findCapableAgents(
          List.of("java", "code-generation"),
          List.of(agent));

      assertThat(results).hasSize(1);
      assertThat(results.get(0).name()).isEqualTo(AgentName.CODE_GENERATOR_AGENT);
    }

    @Test
    @DisplayName("Should exclude agents missing required capabilities")
    void shouldExcludePartialMatch() {
      AgentMetadata agent = AgentMetadata.builder()
          .name(AgentName.SENTIMENT_AGENT)
          .version("1.0")
          .description("Sentiment")
          .capabilities(List.of("sentiment", "nlp"))
          .supportedModels(List.of("gpt-4"))
          .latencySLA(3000)
          .build();

      List<AgentMetadata> results = matcher.findCapableAgents(
          List.of("java", "code-generation"),
          List.of(agent));

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for no available agents")
    void shouldReturnEmptyForNoAgents() {
      List<AgentMetadata> results = matcher.findCapableAgents(
          List.of("java"), List.of());
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should sort agents by composite score")
    void shouldSortByScore() {
      // Low-latency specialist
      AgentMetadata specialist = AgentMetadata.builder()
          .name(AgentName.CODE_GENERATOR_AGENT)
          .version("1.0")
          .description("Specialist")
          .capabilities(List.of("java", "code-generation"))
          .supportedModels(List.of("gpt-4"))
          .latencySLA(1000)
          .build();

      // High-latency generalist
      AgentMetadata generalist = AgentMetadata.builder()
          .name(AgentName.COPILOT_AGENT)
          .version("1.0")
          .description("Generalist")
          .capabilities(List.of("java", "code-generation", "python", "typescript", "review"))
          .supportedModels(List.of("gpt-4"))
          .latencySLA(10000)
          .build();

      List<AgentMetadata> results = matcher.findCapableAgents(
          List.of("java", "code-generation"),
          List.of(generalist, specialist));

      assertThat(results).hasSize(2);
      // Specialist should rank higher (better score: fewer extra caps + lower latency)
      assertThat(results.get(0).name()).isEqualTo(AgentName.CODE_GENERATOR_AGENT);
    }
  }
}
