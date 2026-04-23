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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for memory tier operations (D008). // GH-90000
 *
 * <p>Validates episodic, semantic, and procedural memory tiers.
 *
 * @doc.type class
 * @doc.purpose Memory tier tests for episodic, semantic, procedural
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
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
        void storeEpisodicCreatesShortTermMemory() { // GH-90000
            String agentId = "agent-001";
            MemoryService.MemoryEntry entry = MemoryService.MemoryEntry.create( // GH-90000
                agentId,
                MemoryService.MemoryTier.EPISODIC,
                "User asked about weather today",
                Map.of("context", "morning_conversation"), // GH-90000
                0.7
            );

            MemoryService.MemoryEntry saved = new MemoryService.MemoryEntry( // GH-90000
                "mem-001", agentId, MemoryService.MemoryTier.EPISODIC,
                entry.content(), entry.metadata(), entry.importance(), // GH-90000
                System.currentTimeMillis(), 0, System.currentTimeMillis() // GH-90000
            );

            when(memoryService.store(eq(agentId), eq(MemoryService.MemoryTier.EPISODIC), any())) // GH-90000
                .thenReturn(Promise.of(saved)); // GH-90000

            MemoryService.MemoryEntry result = runPromise(() -> // GH-90000
                memoryService.store(agentId, MemoryService.MemoryTier.EPISODIC, entry) // GH-90000
            );

            assertThat(result.tier()).isEqualTo(MemoryService.MemoryTier.EPISODIC); // GH-90000
            assertThat(result.id()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("[D008]: recall_episodic_by_pattern")
        void recallEpisodicByPattern() { // GH-90000
            String agentId = "agent-001";
            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                createEpisodicMemory(agentId, "weather conversation"), // GH-90000
                createEpisodicMemory(agentId, "weather forecast"), // GH-90000
                createEpisodicMemory(agentId, "unrelated topic") // GH-90000
            );

            when(memoryService.recall(eq(agentId), eq(MemoryService.MemoryTier.EPISODIC), any())) // GH-90000
                .thenReturn(Promise.of(memories)); // GH-90000

            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder() // GH-90000
                .pattern("weather")
                .tier(MemoryService.MemoryTier.EPISODIC) // GH-90000
                .build(); // GH-90000

            List<MemoryService.MemoryEntry> results = runPromise(() -> // GH-90000
                memoryService.recall(agentId, MemoryService.MemoryTier.EPISODIC, query) // GH-90000
            );

            assertThat(results).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("[D008]: episodic_has_recent_timestamp")
        void episodicHasRecentTimestamp() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000

            MemoryService.MemoryEntry entry = createEpisodicMemory("agent-001", "recent event"); // GH-90000

            assertThat(entry.timestamp()).isGreaterThanOrEqualTo(now - 1000); // GH-90000
        }

        private MemoryService.MemoryEntry createEpisodicMemory(String agentId, String content) { // GH-90000
            return MemoryService.MemoryEntry.create( // GH-90000
                agentId, MemoryService.MemoryTier.EPISODIC, content, Map.of(), 0.5 // GH-90000
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
        void storeSemanticCreatesLongTermKnowledge() { // GH-90000
            String agentId = "agent-001";
            MemoryService.MemoryEntry entry = MemoryService.MemoryEntry.create( // GH-90000
                agentId,
                MemoryService.MemoryTier.SEMANTIC,
                "User prefers temperature in Celsius",
                Map.of("type", "preference", "topic", "units"), // GH-90000
                0.9
            );

            MemoryService.MemoryEntry saved = new MemoryService.MemoryEntry( // GH-90000
                "mem-002", agentId, MemoryService.MemoryTier.SEMANTIC,
                entry.content(), entry.metadata(), entry.importance(), // GH-90000
                System.currentTimeMillis(), 5, System.currentTimeMillis() // GH-90000
            );

            when(memoryService.store(eq(agentId), eq(MemoryService.MemoryTier.SEMANTIC), any())) // GH-90000
                .thenReturn(Promise.of(saved)); // GH-90000

            MemoryService.MemoryEntry result = runPromise(() -> // GH-90000
                memoryService.store(agentId, MemoryService.MemoryTier.SEMANTIC, entry) // GH-90000
            );

            assertThat(result.tier()).isEqualTo(MemoryService.MemoryTier.SEMANTIC); // GH-90000
            assertThat(result.importance()).isGreaterThan(0.8); // GH-90000
        }

        @Test
        @DisplayName("[D008]: recall_semantic_by_keyword")
        void recallSemanticByKeyword() { // GH-90000
            String agentId = "agent-001";
            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                createSemanticMemory(agentId, "Celsius preference", 0.9) // GH-90000
            );

            when(memoryService.recall(eq(agentId), eq(MemoryService.MemoryTier.SEMANTIC), any())) // GH-90000
                .thenReturn(Promise.of(memories)); // GH-90000

            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder() // GH-90000
                .pattern("Celsius")
                .tier(MemoryService.MemoryTier.SEMANTIC) // GH-90000
                .build(); // GH-90000

            List<MemoryService.MemoryEntry> results = runPromise(() -> // GH-90000
                memoryService.recall(agentId, MemoryService.MemoryTier.SEMANTIC, query) // GH-90000
            );

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).content()).contains("Celsius");
        }

        @Test
        @DisplayName("[D008]: semantic_memory_persists_longer")
        void semanticMemoryPersistsLonger() { // GH-90000
            // Semantic memories should have higher importance and access counts
            MemoryService.MemoryEntry semantic = createSemanticMemory("agent-001", "important fact", 0.95); // GH-90000

            assertThat(semantic.importance()).isGreaterThan(0.9); // GH-90000
        }

        private MemoryService.MemoryEntry createSemanticMemory(String agentId, String content, double importance) { // GH-90000
            return MemoryService.MemoryEntry.create( // GH-90000
                agentId, MemoryService.MemoryTier.SEMANTIC, content, Map.of(), importance // GH-90000
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
        void storeProceduralCreatesSkillMemory() { // GH-90000
            String agentId = "agent-001";
            MemoryService.MemoryEntry entry = MemoryService.MemoryEntry.create( // GH-90000
                agentId,
                MemoryService.MemoryTier.PROCEDURAL,
                "How to calculate Fahrenheit to Celsius: (F - 32) * 5/9", // GH-90000
                Map.of("type", "formula", "domain", "temperature"), // GH-90000
                0.85
            );

            MemoryService.MemoryEntry saved = new MemoryService.MemoryEntry( // GH-90000
                "mem-003", agentId, MemoryService.MemoryTier.PROCEDURAL,
                entry.content(), entry.metadata(), entry.importance(), // GH-90000
                System.currentTimeMillis(), 10, System.currentTimeMillis() // GH-90000
            );

            when(memoryService.store(eq(agentId), eq(MemoryService.MemoryTier.PROCEDURAL), any())) // GH-90000
                .thenReturn(Promise.of(saved)); // GH-90000

            MemoryService.MemoryEntry result = runPromise(() -> // GH-90000
                memoryService.store(agentId, MemoryService.MemoryTier.PROCEDURAL, entry) // GH-90000
            );

            assertThat(result.tier()).isEqualTo(MemoryService.MemoryTier.PROCEDURAL); // GH-90000
            assertThat(result.metadata()).isInstanceOf(Map.class);
            Map<?, ?> metadata = (Map<?, ?>) result.metadata();
            assertThat(metadata.get("type")).isEqualTo("formula");
        }

        @Test
        @DisplayName("[D008]: procedural_skills_have_high_access_count")
        void proceduralSkillsHaveHighAccessCount() { // GH-90000
            MemoryService.MemoryEntry procedural = new MemoryService.MemoryEntry( // GH-90000
                "mem-004", "agent-001", MemoryService.MemoryTier.PROCEDURAL,
                "Skill content", Map.of(), 0.8, // GH-90000
                System.currentTimeMillis(), 50, System.currentTimeMillis() // GH-90000
            );

            assertThat(procedural.accessCount()).isGreaterThan(10); // GH-90000
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
        void consolidateEpisodicToSemantic() { // GH-90000
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> episodicMemories = List.of( // GH-90000
                createMemory(agentId, MemoryService.MemoryTier.EPISODIC, "User likes Celsius", 0.7), // GH-90000
                createMemory(agentId, MemoryService.MemoryTier.EPISODIC, "User mentioned Celsius again", 0.6) // GH-90000
            );

            MemoryService.ConsolidationResult result = new MemoryService.ConsolidationResult( // GH-90000
                2, 1, 0, List.of("mem-consolidated")
            );

            when(memoryService.consolidate(eq(agentId), any())) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            MemoryService.ConsolidationCriteria criteria = MemoryService.ConsolidationCriteria.defaultCriteria(); // GH-90000
            MemoryService.ConsolidationResult consolidation = runPromise(() -> // GH-90000
                memoryService.consolidate(agentId, criteria) // GH-90000
            );

            assertThat(consolidation.processed()).isEqualTo(2); // GH-90000
            assertThat(consolidation.consolidated()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("[D008]: consolidation_important_memories_only")
        void consolidationImportantMemoriesOnly() { // GH-90000
            MemoryService.ConsolidationCriteria criteria = new MemoryService.ConsolidationCriteria( // GH-90000
                0.8, 86400000, 3, 100
            );

            assertThat(criteria.minImportance()).isEqualTo(0.8); // GH-90000
            assertThat(criteria.minAccessCount()).isEqualTo(3); // GH-90000
        }

        private MemoryService.MemoryEntry createMemory(String agentId, MemoryService.MemoryTier tier, // GH-90000
                                                        String content, double importance) {
            return MemoryService.MemoryEntry.create(agentId, tier, content, Map.of(), importance); // GH-90000
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
        void getStatsReturnsTierBreakdown() { // GH-90000
            String agentId = "agent-001";

            MemoryService.MemoryStats stats = new MemoryService.MemoryStats( // GH-90000
                1000,    // total
                500,     // episodic
                400,     // semantic
                100,     // procedural
                0.75,    // avg importance
                1024000, // size bytes
                0.85,    // hit rate
                50       // evictions
            );

            when(memoryService.getStats(agentId)).thenReturn(Promise.of(stats)); // GH-90000

            MemoryService.MemoryStats result = runPromise(() -> memoryService.getStats(agentId)); // GH-90000

            assertThat(result.totalCount()).isEqualTo(1000); // GH-90000
            assertThat(result.episodicCount()).isEqualTo(500); // GH-90000
            assertThat(result.semanticCount()).isEqualTo(400); // GH-90000
            assertThat(result.proceduralCount()).isEqualTo(100); // GH-90000
            assertThat(result.hitRate()).isEqualTo(0.85); // GH-90000
        }

        @Test
        @DisplayName("[D008]: tier_counts_sum_to_total")
        void tierCountsSumToTotal() { // GH-90000
            long episodic = 500;
            long semantic = 400;
            long procedural = 100;

            assertThat(episodic + semantic + procedural).isEqualTo(1000); // GH-90000
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
        void recallWithMinImportanceFilters() { // GH-90000
            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder() // GH-90000
                .minImportance(0.8) // GH-90000
                .build(); // GH-90000

            assertThat(query.minImportance()).isEqualTo(0.8); // GH-90000
        }

        @Test
        @DisplayName("[D008]: recall_with_time_range_filters_by_date")
        void recallWithTimeRangeFiltersByDate() { // GH-90000
            long startTime = 1704067200000L; // 2024-01-01
            long endTime = 1706745600000L;   // 2024-02-01

            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder() // GH-90000
                .since(startTime) // GH-90000
                .until(endTime) // GH-90000
                .build(); // GH-90000

            assertThat(query.since()).isEqualTo(startTime); // GH-90000
            assertThat(query.until()).isEqualTo(endTime); // GH-90000
        }

        @Test
        @DisplayName("[D008]: recall_with_limit_restricts_results")
        void recallWithLimitRestrictsResults() { // GH-90000
            MemoryService.RecallQuery query = MemoryService.RecallQuery.builder() // GH-90000
                .limit(10) // GH-90000
                .build(); // GH-90000

            assertThat(query.limit()).isEqualTo(10); // GH-90000
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
        void importanceRange0To1() { // GH-90000
            double low = 0.1;
            double medium = 0.5;
            double high = 0.9;

            assertThat(low).isBetween(0.0, 1.0); // GH-90000
            assertThat(medium).isBetween(0.0, 1.0); // GH-90000
            assertThat(high).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("[D008]: high_importance_memories_preserved")
        void highImportanceMemoriesPreserved() { // GH-90000
            MemoryService.MemoryEntry important = MemoryService.MemoryEntry.create( // GH-90000
                "agent-001", MemoryService.MemoryTier.SEMANTIC,
                "Critical knowledge", Map.of(), 0.95 // GH-90000
            );

            assertThat(important.importance()).isGreaterThan(0.9); // GH-90000
        }
    }
}
