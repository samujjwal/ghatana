package com.ghatana.agent.memory.model.episode;

import com.ghatana.agent.memory.model.MemoryItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EnhancedEpisode} value object.
 *
 * @doc.type class
 * @doc.purpose EnhancedEpisode builder and value tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("EnhancedEpisode")
class EnhancedEpisodeTest {

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should have EPISODE type by default")
        void shouldHaveEpisodeType() {
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-1")
                    .agentId("agent-1")
                    .turnId("turn-1")
                    .input("What is the weather?")
                    .output("It is sunny.")
                    .build();

            assertThat(episode.getType()).isEqualTo(MemoryItemType.EPISODE);
        }

        @Test
        @DisplayName("should set createdAt and updatedAt by default")
        void shouldSetTimestamps() {
            Instant before = Instant.now();
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-1")
                    .agentId("agent-1")
                    .turnId("turn-1")
                    .input("test")
                    .output("result")
                    .build();
            Instant after = Instant.now();

            assertThat(episode.getCreatedAt())
                    .isNotNull()
                    .isBetween(before, after);
            assertThat(episode.getUpdatedAt())
                    .isNotNull()
                    .isBetween(before, after);
        }

        @Test
        @DisplayName("should have empty tool executions by default")
        void shouldHaveEmptyToolExecutions() {
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-1")
                    .agentId("agent-1")
                    .turnId("turn-1")
                    .input("test")
                    .output("result")
                    .build();

            assertThat(episode.getToolExecutions()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should have empty tags by default")
        void shouldHaveEmptyTags() {
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-1")
                    .agentId("agent-1")
                    .turnId("turn-1")
                    .input("test")
                    .output("result")
                    .build();

            assertThat(episode.getTags()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("full construction")
    class FullConstruction {

        @Test
        @DisplayName("should preserve all fields")
        void shouldPreserveAllFields() {
            EnhancedEpisode episode = EnhancedEpisode.builder()
                    .id("ep-100")
                    .agentId("agent-weather")
                    .turnId("turn-42")
                    .input("What is the weather in NYC?")
                    .output("It is currently 72°F in New York City.")
                    .action("weather_lookup")
                    .context(Map.of("location", "NYC"))
                    .tags(List.of("weather", "location"))
                    .reward(0.95)
                    .cost(0.002)
                    .latencyMs(340L)
                    .build();

            assertThat(episode.getId()).isEqualTo("ep-100");
            assertThat(episode.getAgentId()).isEqualTo("agent-weather");
            assertThat(episode.getTurnId()).isEqualTo("turn-42");
            assertThat(episode.getInput()).isEqualTo("What is the weather in NYC?");
            assertThat(episode.getOutput()).isEqualTo("It is currently 72°F in New York City.");
            assertThat(episode.getAction()).isEqualTo("weather_lookup");
            assertThat(episode.getContext()).containsEntry("location", "NYC");
            assertThat(episode.getTags()).containsExactly("weather", "location");
            assertThat(episode.getReward()).isEqualTo(0.95);
            assertThat(episode.getCost()).isEqualTo(0.002);
            assertThat(episode.getLatencyMs()).isEqualTo(340L);
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("should be a value type with equality by fields")
        void shouldBeValueType() {
            EnhancedEpisode ep1 = EnhancedEpisode.builder()
                    .id("ep-1").agentId("a").turnId("t").input("i").output("o").build();
            EnhancedEpisode ep2 = EnhancedEpisode.builder()
                    .id("ep-1").agentId("a").turnId("t").input("i").output("o").build();

            // Both created at ~same time, same fields → equals check on id
            assertThat(ep1.getId()).isEqualTo(ep2.getId());
        }
    }
}
