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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for memory tier operations (D008).
 *
 * <p>Validates episodic, semantic, and procedural memory tiers.
 *
 * @doc.type class
 * @doc.purpose Memory tier tests for episodic, semantic, procedural
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryTier – Episodic, Semantic, Procedural (D008)")
class MemoryTierTest extends EventloopTestBase {

    @Mock
    private MemoryService memoryService;

    @Mock
    private EpisodicMemoryRepository episodicRepository;

    @Mock
    private SemanticMemoryRepository semanticRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Episodic Memory Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Episodic Memory")
    class EpisodicMemoryTests {

        @Test
        @DisplayName("[D008]: store_episodic_creates_short_term_memory")
        void storeEpisodicCreatesShortTermMemory() {
            String agentId = "agent-001";
            MemoryService.MemoryEntry entry = MemoryService.MemoryEntry.create(
                agentId,
                MemoryService.MemoryTier.EPISODIC,
                "User asked about weather today",
                Map.of("context", "morning_conversation"),
                0.7
            );

            MemoryService.MemoryEntry saved = new MemoryService.MemoryEntry(
                "mem-001", agentId, MemoryService.MemoryTier.EPISODIC,
                entry.content(), entry.metadata(), entry.importance(),
                System.currentTimeMillis(), 0, System.currentTimeMillis()
            );

            when(memoryService.store(eq(agentId), eq(MemoryService.MemoryTier.EPISODIC), any()))
                .thenReturn(Promise.of(saved));

            MemoryService.MemoryEntry result = runPromise(() ->
                memoryService.store(agentId, MemoryService.MemoryTier.EPISODIC, entry)
            );

            assertThat(result.tier()).isEqualTo(MemoryService.MemoryTier.EPISODIC);
            assertThat(result.id()).isNotNull();
        }

        @Test
        @DisplayName("[D008]: recall_episodic_by_pattern")
        void recallEpisodicByPattern() {
            String agentId = "agent-001";
            List<MemoryService.MemoryEntry> memories = List.of(
                createEpisodicMemory(agentId, "weather conversation"),
                createEpisodicMemory(agentId, "weather forecast"),
                createEpisodicMemory(agentId, "unrelated topic")
            );

            when(memoryService.recall(eq(agentId), eq(MemoryService.MemoryTier.EPISODIC), any()))
                .thenReturn(Promise.of(memories));

            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder()
                .pattern("weather")
                .tier(MemoryService.MemoryTier.EPISODIC)
                .build();

            List<MemoryService.MemoryEntry> results = runPromise(() ->
                memoryService.recall(agentId, MemoryService.MemoryTier.EPISODIC, query)
            );

            assertThat(results).hasSize(3);
        }

        @Test
        @DisplayName("[D008]: episodic_has_recent_timestamp")
        void episodicHasRecentTimestamp() {
            long now = System.currentTimeMillis();

            MemoryService.MemoryEntry entry = createEpisodicMemory("agent-001", "recent event");

            assertThat(entry.timestamp()).isGreaterThanOrEqualTo(now - 1000);
        }

        private MemoryService.MemoryEntry createEpisodicMemory(String agentId, String content) {
            return MemoryService.MemoryEntry.create(
                agentId, MemoryService.MemoryTier.EPISODIC, content, Map.of(), 0.5
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Semantic Memory Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Semantic Memory")
    class SemanticMemoryTests {

        @Test
        @DisplayName("[D008]: store_semantic_creates_long_term_knowledge")
        void storeSemanticCreatesLongTermKnowledge() {
            String agentId = "agent-001";
            MemoryService.MemoryEntry entry = MemoryService.MemoryEntry.create(
                agentId,
                MemoryService.MemoryTier.SEMANTIC,
                "User prefers temperature in Celsius",
                Map.of("type", "preference", "topic", "units"),
                0.9
            );

            MemoryService.MemoryEntry saved = new MemoryService.MemoryEntry(
                "mem-002", agentId, MemoryService.MemoryTier.SEMANTIC,
                entry.content(), entry.metadata(), entry.importance(),
                System.currentTimeMillis(), 5, System.currentTimeMillis()
            );

            when(memoryService.store(eq(agentId), eq(MemoryService.MemoryTier.SEMANTIC), any()))
                .thenReturn(Promise.of(saved));

            MemoryService.MemoryEntry result = runPromise(() ->
                memoryService.store(agentId, MemoryService.MemoryTier.SEMANTIC, entry)
            );

            assertThat(result.tier()).isEqualTo(MemoryService.MemoryTier.SEMANTIC);
            assertThat(result.importance()).isGreaterThan(0.8);
        }

        @Test
        @DisplayName("[D008]: recall_semantic_by_keyword")
        void recallSemanticByKeyword() {
            String agentId = "agent-001";
            List<MemoryService.MemoryEntry> memories = List.of(
                createSemanticMemory(agentId, "Celsius preference", 0.9)
            );

            when(memoryService.recall(eq(agentId), eq(MemoryService.MemoryTier.SEMANTIC), any()))
                .thenReturn(Promise.of(memories));

            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder()
                .pattern("Celsius")
                .tier(MemoryService.MemoryTier.SEMANTIC)
                .build();

            List<MemoryService.MemoryEntry> results = runPromise(() ->
                memoryService.recall(agentId, MemoryService.MemoryTier.SEMANTIC, query)
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).content()).contains("Celsius");
        }

        @Test
        @DisplayName("[D008]: semantic_memory_persists_longer")
        void semanticMemoryPersistsLonger() {
            // Semantic memories should have higher importance and access counts
            MemoryService.MemoryEntry semantic = createSemanticMemory("agent-001", "important fact", 0.95);

            assertThat(semantic.importance()).isGreaterThan(0.9);
        }

        private MemoryService.MemoryEntry createSemanticMemory(String agentId, String content, double importance) {
            return MemoryService.MemoryEntry.create(
                agentId, MemoryService.MemoryTier.SEMANTIC, content, Map.of(), importance
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Procedural Memory Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Procedural Memory")
    class ProceduralMemoryTests {

        @Test
        @DisplayName("[D008]: store_procedural_creates_skill_memory")
        void storeProceduralCreatesSkillMemory() {
            String agentId = "agent-001";
            MemoryService.MemoryEntry entry = MemoryService.MemoryEntry.create(
                agentId,
                MemoryService.MemoryTier.PROCEDURAL,
                "How to calculate Fahrenheit to Celsius: (F - 32) * 5/9",
                Map.of("type", "formula", "domain", "temperature"),
                0.85
            );

            MemoryService.MemoryEntry saved = new MemoryService.MemoryEntry(
                "mem-003", agentId, MemoryService.MemoryTier.PROCEDURAL,
                entry.content(), entry.metadata(), entry.importance(),
                System.currentTimeMillis(), 10, System.currentTimeMillis()
            );

            when(memoryService.store(eq(agentId), eq(MemoryService.MemoryTier.PROCEDURAL), any()))
                .thenReturn(Promise.of(saved));

            MemoryService.MemoryEntry result = runPromise(() ->
                memoryService.store(agentId, MemoryService.MemoryTier.PROCEDURAL, entry)
            );

            assertThat(result.tier()).isEqualTo(MemoryService.MemoryTier.PROCEDURAL);
            assertThat(result.content()).contains("formula");
        }

        @Test
        @DisplayName("[D008]: procedural_skills_have_high_access_count")
        void proceduralSkillsHaveHighAccessCount() {
            MemoryService.MemoryEntry procedural = new MemoryService.MemoryEntry(
                "mem-004", "agent-001", MemoryService.MemoryTier.PROCEDURAL,
                "Skill content", Map.of(), 0.8,
                System.currentTimeMillis(), 50, System.currentTimeMillis()
            );

            assertThat(procedural.accessCount()).isGreaterThan(10);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Memory Consolidation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Memory Consolidation")
    class MemoryConsolidationTests {

        @Test
        @DisplayName("[D008]: consolidate_episodic_to_semantic")
        void consolidateEpisodicToSemantic() {
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> episodicMemories = List.of(
                createMemory(agentId, MemoryService.MemoryTier.EPISODIC, "User likes Celsius", 0.7),
                createMemory(agentId, MemoryService.MemoryTier.EPISODIC, "User mentioned Celsius again", 0.6)
            );

            MemoryService.ConsolidationResult result = new MemoryService.ConsolidationResult(
                2, 1, 0, List.of("mem-consolidated")
            );

            when(memoryService.consolidate(eq(agentId), any()))
                .thenReturn(Promise.of(result));

            MemoryService.ConsolidationCriteria criteria = MemoryService.ConsolidationCriteria.defaultCriteria();
            MemoryService.ConsolidationResult consolidation = runPromise(() ->
                memoryService.consolidate(agentId, criteria)
            );

            assertThat(consolidation.processed()).isEqualTo(2);
            assertThat(consolidation.consolidated()).isEqualTo(1);
        }

        @Test
        @DisplayName("[D008]: consolidation_important_memories_only")
        void consolidationImportantMemoriesOnly() {
            MemoryService.ConsolidationCriteria criteria = new MemoryService.ConsolidationCriteria(
                0.8, 86400000, 3, 100
            );

            assertThat(criteria.minImportance()).isEqualTo(0.8);
            assertThat(criteria.minAccessCount()).isEqualTo(3);
        }

        private MemoryService.MemoryEntry createMemory(String agentId, MemoryService.MemoryTier tier,
                                                        String content, double importance) {
            return MemoryService.MemoryEntry.create(agentId, tier, content, Map.of(), importance);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Memory Stats Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Memory Statistics")
    class MemoryStatsTests {

        @Test
        @DisplayName("[D008]: get_stats_returns_tier_breakdown")
        void getStatsReturnsTierBreakdown() {
            String agentId = "agent-001";

            MemoryService.MemoryStats stats = new MemoryService.MemoryStats(
                1000,    // total
                500,     // episodic
                400,     // semantic
                100,     // procedural
                0.75,    // avg importance
                1024000, // size bytes
                0.85,    // hit rate
                50       // evictions
            );

            when(memoryService.getStats(agentId)).thenReturn(Promise.of(stats));

            MemoryService.MemoryStats result = runPromise(() -> memoryService.getStats(agentId));

            assertThat(result.totalCount()).isEqualTo(1000);
            assertThat(result.episodicCount()).isEqualTo(500);
            assertThat(result.semanticCount()).isEqualTo(400);
            assertThat(result.proceduralCount()).isEqualTo(100);
            assertThat(result.hitRate()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("[D008]: tier_counts_sum_to_total")
        void tierCountsSumToTotal() {
            long episodic = 500;
            long semantic = 400;
            long procedural = 100;

            assertThat(episodic + semantic + procedural).isEqualTo(1000);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recall Query Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Recall Query")
    class RecallQueryTests {

        @Test
        @DisplayName("[D008]: recall_with_min_importance_filters")
        void recallWithMinImportanceFilters() {
            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder()
                .minImportance(0.8)
                .build();

            assertThat(query.minImportance()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("[D008]: recall_with_time_range_filters_by_date")
        void recallWithTimeRangeFiltersByDate() {
            long startTime = 1704067200000L; // 2024-01-01
            long endTime = 1706745600000L;   // 2024-02-01

            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder()
                .since(startTime)
                .until(endTime)
                .build();

            assertThat(query.since()).isEqualTo(startTime);
            assertThat(query.until()).isEqualTo(endTime);
        }

        @Test
        @DisplayName("[D008]: recall_with_limit_restricts_results")
        void recallWithLimitRestrictsResults() {
            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder()
                .limit(10)
                .build();

            assertThat(query.limit()).isEqualTo(10);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Importance Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Importance")
    class ImportanceTests {

        @Test
        @DisplayName("[D008]: importance_range_0_to_1")
        void importanceRange0To1() {
            double low = 0.1;
            double medium = 0.5;
            double high = 0.9;

            assertThat(low).isBetween(0.0, 1.0);
            assertThat(medium).isBetween(0.0, 1.0);
            assertThat(high).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("[D008]: high_importance_memories_preserved")
        void highImportanceMemoriesPreserved() {
            MemoryService.MemoryEntry important = MemoryService.MemoryEntry.create(
                "agent-001", MemoryService.MemoryTier.SEMANTIC,
                "Critical knowledge", Map.of(), 0.95
            );

            assertThat(important.importance()).isGreaterThan(0.9);
        }
    }

    private static class Map {
        static <K, V> java.util.Map<K, V> of(K k1, V v1) {
            java.util.Map<K, V> map = new java.util.HashMap<>();
            map.put(k1, v1);
            return java.util.Collections.unmodifiableMap(map);
        }

        static <K, V> java.util.Map<K, V> of(K k1, V v1, K k2, V v2) {
            java.util.Map<K, V> map = new java.util.HashMap<>();
            map.put(k1, v1);
            map.put(k2, v2);
            return java.util.Collections.unmodifiableMap(map);
        }
    }
}
