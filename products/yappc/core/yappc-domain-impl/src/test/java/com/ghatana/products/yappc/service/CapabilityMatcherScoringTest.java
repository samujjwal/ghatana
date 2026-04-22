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
@DisplayName("CapabilityMatcher Tests [GH-90000]")
class CapabilityMatcherTest {

  private CapabilityMatcher matcher;

  @BeforeEach
  void setUp() { // GH-90000
    matcher = new CapabilityMatcher(); // GH-90000
  }

  @Nested
  @DisplayName("Agent Matching [GH-90000]")
  class AgentMatching {

    @Test
    @DisplayName("Should find agents with all required capabilities [GH-90000]")
    void shouldFindFullMatch() { // GH-90000
      AgentMetadata agent = AgentMetadata.builder() // GH-90000
          .name(AgentName.CODE_GENERATOR_AGENT) // GH-90000
          .version("1.0 [GH-90000]")
          .description("Code gen [GH-90000]")
          .capabilities(List.of("java", "code-generation", "refactoring")) // GH-90000
          .supportedModels(List.of("gpt-4 [GH-90000]"))
          .latencySLA(5000) // GH-90000
          .build(); // GH-90000

      List<AgentMetadata> results = matcher.findCapableAgents( // GH-90000
          List.of("java", "code-generation"), // GH-90000
          List.of(agent)); // GH-90000

      assertThat(results).hasSize(1); // GH-90000
      assertThat(results.get(0).name()).isEqualTo(AgentName.CODE_GENERATOR_AGENT); // GH-90000
    }

    @Test
    @DisplayName("Should exclude agents missing required capabilities [GH-90000]")
    void shouldExcludePartialMatch() { // GH-90000
      AgentMetadata agent = AgentMetadata.builder() // GH-90000
          .name(AgentName.SENTIMENT_AGENT) // GH-90000
          .version("1.0 [GH-90000]")
          .description("Sentiment [GH-90000]")
          .capabilities(List.of("sentiment", "nlp")) // GH-90000
          .supportedModels(List.of("gpt-4 [GH-90000]"))
          .latencySLA(3000) // GH-90000
          .build(); // GH-90000

      List<AgentMetadata> results = matcher.findCapableAgents( // GH-90000
          List.of("java", "code-generation"), // GH-90000
          List.of(agent)); // GH-90000

      assertThat(results).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should return empty for no available agents [GH-90000]")
    void shouldReturnEmptyForNoAgents() { // GH-90000
      List<AgentMetadata> results = matcher.findCapableAgents( // GH-90000
          List.of("java [GH-90000]"), List.of());
      assertThat(results).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should sort agents by composite score [GH-90000]")
    void shouldSortByScore() { // GH-90000
      // Low-latency specialist
      AgentMetadata specialist = AgentMetadata.builder() // GH-90000
          .name(AgentName.CODE_GENERATOR_AGENT) // GH-90000
          .version("1.0 [GH-90000]")
          .description("Specialist [GH-90000]")
          .capabilities(List.of("java", "code-generation")) // GH-90000
          .supportedModels(List.of("gpt-4 [GH-90000]"))
          .latencySLA(1000) // GH-90000
          .build(); // GH-90000

      // High-latency generalist
      AgentMetadata generalist = AgentMetadata.builder() // GH-90000
          .name(AgentName.COPILOT_AGENT) // GH-90000
          .version("1.0 [GH-90000]")
          .description("Generalist [GH-90000]")
          .capabilities(List.of("java", "code-generation", "python", "typescript", "review")) // GH-90000
          .supportedModels(List.of("gpt-4 [GH-90000]"))
          .latencySLA(10000) // GH-90000
          .build(); // GH-90000

      List<AgentMetadata> results = matcher.findCapableAgents( // GH-90000
          List.of("java", "code-generation"), // GH-90000
          List.of(generalist, specialist)); // GH-90000

      assertThat(results).hasSize(2); // GH-90000
      // Specialist should rank higher (better score: fewer extra caps + lower latency) // GH-90000
      assertThat(results.get(0).name()).isEqualTo(AgentName.CODE_GENERATOR_AGENT); // GH-90000
    }
  }
}
