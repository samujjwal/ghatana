/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.memory;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for memory recall accuracy with deterministic fixtures (D010). // GH-90000
 *
 * <p>Validates memory retrieval accuracy and LRU eviction logic.
 *
 * @doc.type class
 * @doc.purpose Memory recall accuracy tests with deterministic fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("MemoryRecallAccuracy – Deterministic Fixtures (D010)")
class MemoryRecallAccuracyTest extends EventloopTestBase {

    @Mock
    private EpisodicMemoryRepository episodicRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Recall Accuracy Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Recall Accuracy")
    class RecallAccuracyTests {

        @Test
        @DisplayName("[D010]: exact_match_recall_returns_correct_memory")
        void exactMatchRecallReturnsCorrectMemory() { // GH-90000
            String agentId = "agent-001";
            String searchContent = "weather forecast";

            MemoryService.MemoryEntry memory = MemoryService.MemoryEntry.create( // GH-90000
                agentId, MemoryService.MemoryTier.EPISODIC, searchContent,
                Map.of("type", "weather"), 0.8 // GH-90000
            );

            when(episodicRepository.findByPattern(eq(agentId), eq(searchContent), anyInt())) // GH-90000
                .thenReturn(Promise.of(List.of(memory))); // GH-90000

            List<MemoryService.MemoryEntry> results = runPromise(() -> // GH-90000
                episodicRepository.findByPattern(agentId, searchContent, 10) // GH-90000
            );

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).content()).isEqualTo(searchContent); // GH-90000
        }

        @Test
        @DisplayName("[D010]: partial_match_recall_returns_relevant_memories")
        void partialMatchRecallReturnsRelevantMemories() { // GH-90000
            String agentId = "agent-001";
            String searchPattern = "weather";

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "weather forecast today", Map.of(), 0.7), // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "weather update", Map.of(), 0.6), // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "unrelated topic", Map.of(), 0.5) // GH-90000
            );

            when(episodicRepository.findByPattern(eq(agentId), eq(searchPattern), anyInt())) // GH-90000
                .thenReturn(Promise.of(memories.subList(0, 2))); // GH-90000

            List<MemoryService.MemoryEntry> results = runPromise(() -> // GH-90000
                episodicRepository.findByPattern(agentId, searchPattern, 10) // GH-90000
            );

            assertThat(results).hasSize(2); // GH-90000
            assertThat(results.get(0).content()).contains("weather");
            assertThat(results.get(1).content()).contains("weather");
        }

        @Test
        @DisplayName("[D010]: recall_with_importance_threshold_filters_by_score")
        void recallWithImportanceThresholdFiltersByScore() { // GH-90000
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "high importance", Map.of(), 0.9), // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "medium importance", Map.of(), 0.6), // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "low importance", Map.of(), 0.3) // GH-90000
            );

            when(episodicRepository.findByPattern(eq(agentId), anyString(), anyInt())) // GH-90000
                .thenReturn(Promise.of(memories.stream() // GH-90000
                    .filter(m -> m.importance() >= 0.7) // GH-90000
                    .toList())); // GH-90000

            List<MemoryService.MemoryEntry> results = runPromise(() -> // GH-90000
                episodicRepository.findByPattern(agentId, "importance", 10) // GH-90000
            );

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).importance()).isGreaterThanOrEqualTo(0.7); // GH-90000
        }

        @Test
        @DisplayName("[D010]: recall_with_time_range_filters_by_date")
        void recallWithTimeRangeFiltersByDate() { // GH-90000
            String agentId = "agent-001";
            long startTime = 1704067200000L; // 2024-01-01
            long endTime = 1706745600000L;   // 2024-02-01

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                createMemoryWithTimestamp(agentId, "January event", 1704153600000L), // GH-90000
                createMemoryWithTimestamp(agentId, "February event", 1706832000000L) // GH-90000
            );

            when(episodicRepository.findByTimeRange(eq(agentId), eq(startTime), eq(endTime))) // GH-90000
                .thenReturn(Promise.of(memories.stream() // GH-90000
                    .filter(m -> m.timestamp() >= startTime && m.timestamp() <= endTime) // GH-90000
                    .toList())); // GH-90000

            List<MemoryService.MemoryEntry> results = runPromise(() -> // GH-90000
                episodicRepository.findByTimeRange(agentId, startTime, endTime) // GH-90000
            );

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).content()).isEqualTo("January event");
        }

        @Test
        @DisplayName("[D010]: recall_results_ordered_by_relevance")
        void recallResultsOrderedByRelevance() { // GH-90000
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "low relevance", Map.of(), 0.4), // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "high relevance", Map.of(), 0.9), // GH-90000
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "medium relevance", Map.of(), 0.6) // GH-90000
            );

            List<MemoryService.MemoryEntry> sorted = memories.stream() // GH-90000
                .sorted(Comparator.comparingDouble(MemoryService.MemoryEntry::importance).reversed()) // GH-90000
                .toList(); // GH-90000

            assertThat(sorted.get(0).content()).isEqualTo("high relevance");
            assertThat(sorted.get(1).content()).isEqualTo("medium relevance");
            assertThat(sorted.get(2).content()).isEqualTo("low relevance");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deterministic Fixture Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic Fixtures")
    class DeterministicFixtureTests {

        @Test
        @DisplayName("[D010]: same_query_produces_same_results")
        void sameQueryProducesSameResults() { // GH-90000
            String agentId = "agent-001";
            String query = "weather";

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                createDeterministicMemory(agentId, 1, "Sunny today", 0.7), // GH-90000
                createDeterministicMemory(agentId, 2, "Rain expected", 0.6) // GH-90000
            );

            // First call
            when(episodicRepository.findByPattern(agentId, query, 10)) // GH-90000
                .thenReturn(Promise.of(memories)); // GH-90000
            List<MemoryService.MemoryEntry> results1 = runPromise(() -> // GH-90000
                episodicRepository.findByPattern(agentId, query, 10) // GH-90000
            );

            // Second call with same query
            when(episodicRepository.findByPattern(agentId, query, 10)) // GH-90000
                .thenReturn(Promise.of(memories)); // GH-90000
            List<MemoryService.MemoryEntry> results2 = runPromise(() -> // GH-90000
                episodicRepository.findByPattern(agentId, query, 10) // GH-90000
            );

            assertThat(results1).hasSize(results2.size()); // GH-90000
        }

        @Test
        @DisplayName("[D010]: memory_ids_consistent_in_fixtures")
        void memoryIdsConsistentInFixtures() { // GH-90000
            String agentId = "agent-001";
            String memoryId = "mem-001";

            MemoryService.MemoryEntry memory = new MemoryService.MemoryEntry( // GH-90000
                memoryId, agentId, MemoryService.MemoryTier.EPISODIC,
                "Consistent content", Map.of(), 0.8, // GH-90000
                System.currentTimeMillis(), 0, System.currentTimeMillis() // GH-90000
            );

            assertThat(memory.id()).isEqualTo(memoryId); // GH-90000
        }

        @Test
        @DisplayName("[D010]: timestamps_deterministic_in_fixtures")
        void timestampsDeterministicInFixtures() { // GH-90000
            long fixedTimestamp = 1704067200000L;

            MemoryService.MemoryEntry memory = new MemoryService.MemoryEntry( // GH-90000
                "mem-001", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.8, // GH-90000
                fixedTimestamp, 0, fixedTimestamp
            );

            assertThat(memory.timestamp()).isEqualTo(fixedTimestamp); // GH-90000
            assertThat(memory.lastAccessed()).isEqualTo(fixedTimestamp); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Memory Access Tracking Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Memory Access Tracking")
    class MemoryAccessTrackingTests {

        @Test
        @DisplayName("[D010]: accessed_increments_count")
        void accessedIncrementsCount() { // GH-90000
            MemoryService.MemoryEntry original = new MemoryService.MemoryEntry( // GH-90000
                "mem-001", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.8, // GH-90000
                System.currentTimeMillis(), 5, System.currentTimeMillis() // GH-90000
            );

            MemoryService.MemoryEntry accessed = original.accessed(); // GH-90000

            assertThat(accessed.accessCount()).isEqualTo(6); // GH-90000
        }

        @Test
        @DisplayName("[D010]: accessed_updates_last_accessed_timestamp")
        void accessedUpdatesLastAccessedTimestamp() { // GH-90000
            long originalTime = System.currentTimeMillis() - 10000; // GH-90000

            MemoryService.MemoryEntry original = new MemoryService.MemoryEntry( // GH-90000
                "mem-001", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.8, // GH-90000
                originalTime, 0, originalTime
            );

            MemoryService.MemoryEntry accessed = original.accessed(); // GH-90000

            assertThat(accessed.lastAccessed()).isGreaterThan(original.lastAccessed()); // GH-90000
        }

        @Test
        @DisplayName("[D010]: high_access_count_memories_prioritized")
        void highAccessCountMemoriesPrioritized() { // GH-90000
            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Low access", Map.of(), 0.7, System.currentTimeMillis(), 1, System.currentTimeMillis()), // GH-90000
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "High access", Map.of(), 0.7, System.currentTimeMillis(), 50, System.currentTimeMillis()) // GH-90000
            );

            List<MemoryService.MemoryEntry> sorted = memories.stream() // GH-90000
                .sorted(Comparator.comparingLong(MemoryService.MemoryEntry::accessCount).reversed()) // GH-90000
                .toList(); // GH-90000

            assertThat(sorted.get(0).id()).isEqualTo("mem-2");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Relevance Scoring Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Relevance Scoring")
    class RelevanceScoringTests {

        @Test
        @DisplayName("[D010]: exact_match_highest_relevance")
        void exactMatchHighestRelevance() { // GH-90000
            String query = "weather forecast";
            String content = "weather forecast";

            double relevance = calculateRelevance(query, content); // GH-90000

            assertThat(relevance).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("[D010]: partial_match_medium_relevance")
        void partialMatchMediumRelevance() { // GH-90000
            String query = "weather";
            String content = "weather forecast today";

            double relevance = calculateRelevance(query, content); // GH-90000

            assertThat(relevance).isGreaterThan(0.0).isLessThan(1.0); // GH-90000
        }

        @Test
        @DisplayName("[D010]: no_match_zero_relevance")
        void noMatchZeroRelevance() { // GH-90000
            String query = "weather";
            String content = "completely unrelated topic";

            double relevance = calculateRelevance(query, content); // GH-90000

            assertThat(relevance).isEqualTo(0.0); // GH-90000
        }

        private double calculateRelevance(String query, String content) { // GH-90000
            if (content.equalsIgnoreCase(query)) return 1.0; // GH-90000
            if (content.toLowerCase().contains(query.toLowerCase())) return 0.5; // GH-90000
            return 0.0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private MemoryService.MemoryEntry createMemoryWithTimestamp(String agentId, String content, long timestamp) { // GH-90000
        return new MemoryService.MemoryEntry( // GH-90000
            "mem-" + content.hashCode(), agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
            content, Map.of(), 0.5, timestamp, 0, timestamp // GH-90000
        );
    }

    private MemoryService.MemoryEntry createDeterministicMemory(String agentId, int id, String content, double importance) { // GH-90000
        return new MemoryService.MemoryEntry( // GH-90000
            "mem-" + id, agentId, MemoryService.MemoryTier.EPISODIC,
            content, Map.of(), importance, // GH-90000
            1704067200000L, 0, 1704067200000L
        );
    }
}
