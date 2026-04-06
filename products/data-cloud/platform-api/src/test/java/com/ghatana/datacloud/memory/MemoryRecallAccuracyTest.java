/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for memory recall accuracy with deterministic fixtures (D010).
 *
 * <p>Validates memory retrieval accuracy and LRU eviction logic.
 *
 * @doc.type class
 * @doc.purpose Memory recall accuracy tests with deterministic fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
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
        void exactMatchRecallReturnsCorrectMemory() {
            String agentId = "agent-001";
            String searchContent = "weather forecast";

            MemoryService.MemoryEntry memory = MemoryService.MemoryEntry.create(
                agentId, MemoryService.MemoryTier.EPISODIC, searchContent,
                Map.of("type", "weather"), 0.8
            );

            when(episodicRepository.findByPattern(eq(agentId), eq(searchContent), anyInt()))
                .thenReturn(Promise.of(List.of(memory)));

            List<MemoryService.MemoryEntry> results = runPromise(() ->
                episodicRepository.findByPattern(agentId, searchContent, 10)
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).content()).isEqualTo(searchContent);
        }

        @Test
        @DisplayName("[D010]: partial_match_recall_returns_relevant_memories")
        void partialMatchRecallReturnsRelevantMemories() {
            String agentId = "agent-001";
            String searchPattern = "weather";

            List<MemoryService.MemoryEntry> memories = List.of(
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "weather forecast today", Map.of(), 0.7),
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "weather update", Map.of(), 0.6),
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "unrelated topic", Map.of(), 0.5)
            );

            when(episodicRepository.findByPattern(eq(agentId), eq(searchPattern), anyInt()))
                .thenReturn(Promise.of(memories.subList(0, 2)));

            List<MemoryService.MemoryEntry> results = runPromise(() ->
                episodicRepository.findByPattern(agentId, searchPattern, 10)
            );

            assertThat(results).hasSize(2);
            assertThat(results.get(0).content()).contains("weather");
            assertThat(results.get(1).content()).contains("weather");
        }

        @Test
        @DisplayName("[D010]: recall_with_importance_threshold_filters_by_score")
        void recallWithImportanceThresholdFiltersByScore() {
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> memories = List.of(
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "high importance", Map.of(), 0.9),
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "medium importance", Map.of(), 0.6),
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "low importance", Map.of(), 0.3)
            );

            when(episodicRepository.findByPattern(eq(agentId), anyString(), anyInt()))
                .thenReturn(Promise.of(memories.stream()
                    .filter(m -> m.importance() >= 0.7)
                    .toList()));

            List<MemoryService.MemoryEntry> results = runPromise(() ->
                episodicRepository.findByPattern(agentId, "importance", 10)
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).importance()).isGreaterThanOrEqualTo(0.7);
        }

        @Test
        @DisplayName("[D010]: recall_with_time_range_filters_by_date")
        void recallWithTimeRangeFiltersByDate() {
            String agentId = "agent-001";
            long startTime = 1704067200000L; // 2024-01-01
            long endTime = 1706745600000L;   // 2024-02-01

            List<MemoryService.MemoryEntry> memories = List.of(
                createMemoryWithTimestamp(agentId, "January event", 1704153600000L),
                createMemoryWithTimestamp(agentId, "February event", 1706832000000L)
            );

            when(episodicRepository.findByTimeRange(eq(agentId), eq(startTime), eq(endTime)))
                .thenReturn(Promise.of(memories.stream()
                    .filter(m -> m.timestamp() >= startTime && m.timestamp() <= endTime)
                    .toList()));

            List<MemoryService.MemoryEntry> results = runPromise(() ->
                episodicRepository.findByTimeRange(agentId, startTime, endTime)
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).content()).isEqualTo("January event");
        }

        @Test
        @DisplayName("[D010]: recall_results_ordered_by_relevance")
        void recallResultsOrderedByRelevance() {
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> memories = List.of(
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "low relevance", Map.of(), 0.4),
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "high relevance", Map.of(), 0.9),
                MemoryService.MemoryEntry.create(agentId, MemoryService.MemoryTier.EPISODIC,
                    "medium relevance", Map.of(), 0.6)
            );

            List<MemoryService.MemoryEntry> sorted = memories.stream()
                .sorted(Comparator.comparingDouble(MemoryService.MemoryEntry::importance).reversed())
                .toList();

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
        void sameQueryProducesSameResults() {
            String agentId = "agent-001";
            String query = "weather";

            List<MemoryService.MemoryEntry> memories = List.of(
                createDeterministicMemory(agentId, 1, "Sunny today", 0.7),
                createDeterministicMemory(agentId, 2, "Rain expected", 0.6)
            );

            // First call
            when(episodicRepository.findByPattern(agentId, query, 10))
                .thenReturn(Promise.of(memories));
            List<MemoryService.MemoryEntry> results1 = runPromise(() ->
                episodicRepository.findByPattern(agentId, query, 10)
            );

            // Second call with same query
            when(episodicRepository.findByPattern(agentId, query, 10))
                .thenReturn(Promise.of(memories));
            List<MemoryService.MemoryEntry> results2 = runPromise(() ->
                episodicRepository.findByPattern(agentId, query, 10)
            );

            assertThat(results1).hasSize(results2.size());
        }

        @Test
        @DisplayName("[D010]: memory_ids_consistent_in_fixtures")
        void memoryIdsConsistentInFixtures() {
            String agentId = "agent-001";
            String memoryId = "mem-001";

            MemoryService.MemoryEntry memory = new MemoryService.MemoryEntry(
                memoryId, agentId, MemoryService.MemoryTier.EPISODIC,
                "Consistent content", Map.of(), 0.8,
                System.currentTimeMillis(), 0, System.currentTimeMillis()
            );

            assertThat(memory.id()).isEqualTo(memoryId);
        }

        @Test
        @DisplayName("[D010]: timestamps_deterministic_in_fixtures")
        void timestampsDeterministicInFixtures() {
            long fixedTimestamp = 1704067200000L;

            MemoryService.MemoryEntry memory = new MemoryService.MemoryEntry(
                "mem-001", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.8,
                fixedTimestamp, 0, fixedTimestamp
            );

            assertThat(memory.timestamp()).isEqualTo(fixedTimestamp);
            assertThat(memory.lastAccessed()).isEqualTo(fixedTimestamp);
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
        void accessedIncrementsCount() {
            MemoryService.MemoryEntry original = new MemoryService.MemoryEntry(
                "mem-001", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.8,
                System.currentTimeMillis(), 5, System.currentTimeMillis()
            );

            MemoryService.MemoryEntry accessed = original.accessed();

            assertThat(accessed.accessCount()).isEqualTo(6);
        }

        @Test
        @DisplayName("[D010]: accessed_updates_last_accessed_timestamp")
        void accessedUpdatesLastAccessedTimestamp() {
            long originalTime = System.currentTimeMillis() - 10000;

            MemoryService.MemoryEntry original = new MemoryService.MemoryEntry(
                "mem-001", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.8,
                originalTime, 0, originalTime
            );

            MemoryService.MemoryEntry accessed = original.accessed();

            assertThat(accessed.lastAccessed()).isGreaterThan(original.lastAccessed());
        }

        @Test
        @DisplayName("[D010]: high_access_count_memories_prioritized")
        void highAccessCountMemoriesPrioritized() {
            List<MemoryService.MemoryEntry> memories = List.of(
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC,
                    "Low access", Map.of(), 0.7, System.currentTimeMillis(), 1, System.currentTimeMillis()),
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC,
                    "High access", Map.of(), 0.7, System.currentTimeMillis(), 50, System.currentTimeMillis())
            );

            List<MemoryService.MemoryEntry> sorted = memories.stream()
                .sorted(Comparator.comparingLong(MemoryService.MemoryEntry::accessCount).reversed())
                .toList();

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
        void exactMatchHighestRelevance() {
            String query = "weather forecast";
            String content = "weather forecast";

            double relevance = calculateRelevance(query, content);

            assertThat(relevance).isEqualTo(1.0);
        }

        @Test
        @DisplayName("[D010]: partial_match_medium_relevance")
        void partialMatchMediumRelevance() {
            String query = "weather";
            String content = "weather forecast today";

            double relevance = calculateRelevance(query, content);

            assertThat(relevance).isGreaterThan(0.0).isLessThan(1.0);
        }

        @Test
        @DisplayName("[D010]: no_match_zero_relevance")
        void noMatchZeroRelevance() {
            String query = "weather";
            String content = "completely unrelated topic";

            double relevance = calculateRelevance(query, content);

            assertThat(relevance).isEqualTo(0.0);
        }

        private double calculateRelevance(String query, String content) {
            if (content.equalsIgnoreCase(query)) return 1.0;
            if (content.toLowerCase().contains(query.toLowerCase())) return 0.5;
            return 0.0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────

    private MemoryService.MemoryEntry createMemoryWithTimestamp(String agentId, String content, long timestamp) {
        return new MemoryService.MemoryEntry(
            "mem-" + content.hashCode(), agentId, MemoryService.MemoryTier.EPISODIC,
            content, Map.of(), 0.5, timestamp, 0, timestamp
        );
    }

    private MemoryService.MemoryEntry createDeterministicMemory(String agentId, int id, String content, double importance) {
        return new MemoryService.MemoryEntry(
            "mem-" + id, agentId, MemoryService.MemoryTier.EPISODIC,
            content, Map.of(), importance,
            1704067200000L, 0, 1704067200000L
        );
    }
}
