package com.ghatana.agent.memory.model.fact;

import com.ghatana.agent.memory.model.MemoryItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EnhancedFact} and {@link FactVersion} value objects.
 *
 * @doc.type class
 * @doc.purpose EnhancedFact and FactVersion unit tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("EnhancedFact")
class EnhancedFactTest {

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should have FACT type by default")
        void shouldHaveFactType() {
            EnhancedFact fact = EnhancedFact.builder()
                    .id("fact-1")
                    .subject("user-123")
                    .predicate("prefers")
                    .object("dark mode")
                    .build();

            assertThat(fact.getType()).isEqualTo(MemoryItemType.FACT);
        }

        @Test
        @DisplayName("should set timestamps by default")
        void shouldSetTimestamps() {
            Instant before = Instant.now();
            EnhancedFact fact = EnhancedFact.builder()
                    .id("fact-1")
                    .subject("s").predicate("p").object("o")
                    .build();

            assertThat(fact.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("SPO triple")
    class SpoTriple {

        @Test
        @DisplayName("should store subject-predicate-object triple")
        void shouldStoreSpoTriple() {
            EnhancedFact fact = EnhancedFact.builder()
                    .id("fact-spo")
                    .subject("Alice")
                    .predicate("is-manager-of")
                    .object("Project X")
                    .source("conversation-42")
                    .build();

            assertThat(fact.getSubject()).isEqualTo("Alice");
            assertThat(fact.getPredicate()).isEqualTo("is-manager-of");
            assertThat(fact.getObject()).isEqualTo("Project X");
            assertThat(fact.getSource()).isEqualTo("conversation-42");
        }
    }

    @Nested
    @DisplayName("version history")
    class VersionHistory {

        @Test
        @DisplayName("should support version history tracking")
        void shouldTrackVersionHistory() {
            FactVersion v1 = FactVersion.builder()
                    .version(1)
                    .content("dark mode")
                    .changedAt(Instant.now().minusSeconds(3600))
                    .changedBy("system")
                    .changeReason("Initial observation")
                    .build();

            FactVersion v2 = FactVersion.builder()
                    .version(2)
                    .content("light mode")
                    .changedAt(Instant.now())
                    .changedBy("user-feedback")
                    .changeReason("User changed preference")
                    .build();

            EnhancedFact fact = EnhancedFact.builder()
                    .id("fact-versioned")
                    .subject("user-1")
                    .predicate("prefers-theme")
                    .object("light mode")
                    .version(2)
                    .versionHistory(List.of(v1, v2))
                    .build();

            assertThat(fact.getVersion()).isEqualTo(2);
            assertThat(fact.getVersionHistory()).hasSize(2);
            assertThat(fact.getVersionHistory().get(0).getContent()).isEqualTo("dark mode");
            assertThat(fact.getVersionHistory().get(1).getContent()).isEqualTo("light mode");
        }
    }
}
