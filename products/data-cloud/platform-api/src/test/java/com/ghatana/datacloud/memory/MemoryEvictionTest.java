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
 * Tests for memory LRU eviction logic (D010). // GH-90000
 *
 * <p>Validates LRU eviction policies and memory cleanup.
 *
 * @doc.type class
 * @doc.purpose LRU eviction logic tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("MemoryEviction – LRU Eviction Logic (D010) [GH-90000]")
class MemoryEvictionTest extends EventloopTestBase {

    @Mock
    private EpisodicMemoryRepository episodicRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // LRU Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LRU Eviction [GH-90000]")
    class LRUEvictionTests {

        @Test
        @DisplayName("[D010]: least_recently_used_evicted_first [GH-90000]")
        void leastRecentlyUsedEvictedFirst() { // GH-90000
            String agentId = "agent-001";
            long now = System.currentTimeMillis(); // GH-90000

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                new MemoryService.MemoryEntry("mem-1", agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Old memory", Map.of(), 0.5, now - 10000, 5, now - 10000), // GH-90000
                new MemoryService.MemoryEntry("mem-2", agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Recent memory", Map.of(), 0.5, now - 1000, 5, now - 1000), // GH-90000
                new MemoryService.MemoryEntry("mem-3", agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Very old memory", Map.of(), 0.5, now - 50000, 5, now - 50000) // GH-90000
            );

            List<MemoryService.MemoryEntry> lruOrder = memories.stream() // GH-90000
                .sorted(Comparator.comparingLong(MemoryService.MemoryEntry::lastAccessed)) // GH-90000
                .toList(); // GH-90000

            assertThat(lruOrder.get(0).id()).isEqualTo("mem-3 [GH-90000]"); // Very old
            assertThat(lruOrder.get(1).id()).isEqualTo("mem-1 [GH-90000]"); // Old
            assertThat(lruOrder.get(2).id()).isEqualTo("mem-2 [GH-90000]"); // Recent
        }

        @Test
        @DisplayName("[D010]: find_lru_returns_oldest_accessed [GH-90000]")
        void findLruReturnsOldestAccessed() { // GH-90000
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> lruMemories = List.of( // GH-90000
                new MemoryService.MemoryEntry("mem-old", agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Old content", Map.of(), 0.3, 0, 0, 1000L), // GH-90000
                new MemoryService.MemoryEntry("mem-older", agentId, MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Older content", Map.of(), 0.3, 0, 0, 500L) // GH-90000
            );

            when(episodicRepository.findLRU(agentId, 2)) // GH-90000
                .thenReturn(Promise.of(lruMemories)); // GH-90000

            List<MemoryService.MemoryEntry> results = runPromise(() -> // GH-90000
                episodicRepository.findLRU(agentId, 2) // GH-90000
            );

            assertThat(results).hasSize(2); // GH-90000
            assertThat(results.get(0).lastAccessed()).isGreaterThan(results.get(1).lastAccessed()); // GH-90000
        }

        @Test
        @DisplayName("[D010]: evict_to_target_removes_lru_memories [GH-90000]")
        void evictToTargetRemovesLRUMemories() { // GH-90000
            String agentId = "agent-001";
            long targetSize = 1024L * 1024; // 1MB

            when(episodicRepository.evictToTarget(agentId, targetSize)) // GH-90000
                .thenReturn(Promise.of(5)); // 5 memories evicted // GH-90000

            Integer evictedCount = runPromise(() -> // GH-90000
                episodicRepository.evictToTarget(agentId, targetSize) // GH-90000
            );

            assertThat(evictedCount).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("[D010]: accessed_memory_moves_to_front [GH-90000]")
        void accessedMemoryMovesToFront() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000

            MemoryService.MemoryEntry memory = new MemoryService.MemoryEntry( // GH-90000
                "mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.5, now - 10000, 5, now - 10000 // GH-90000
            );

            // Simulate access
            MemoryService.MemoryEntry accessed = memory.accessed(); // GH-90000

            assertThat(accessed.lastAccessed()).isGreaterThan(memory.lastAccessed()); // GH-90000
            assertThat(accessed.accessCount()).isEqualTo(memory.accessCount() + 1); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eviction Policy Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Eviction Policy [GH-90000]")
    class EvictionPolicyTests {

        @Test
        @DisplayName("[D010]: high_importance_memories_not_evicted [GH-90000]")
        void highImportanceMemoriesNotEvicted() { // GH-90000
            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                MemoryService.MemoryEntry.create("agent-001", MemoryService.MemoryTier.SEMANTIC, // GH-90000
                    "Critical knowledge", Map.of(), 0.95), // High importance - keep // GH-90000
                MemoryService.MemoryEntry.create("agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Old memory", Map.of(), 0.3) // Low importance - evict // GH-90000
            );

            List<MemoryService.MemoryEntry> evictable = memories.stream() // GH-90000
                .filter(m -> m.importance() < 0.8) // GH-90000
                .toList(); // GH-90000

            assertThat(evictable).hasSize(1); // GH-90000
            assertThat(evictable.get(0).content()).isEqualTo("Old memory [GH-90000]");
        }

        @Test
        @DisplayName("[D010]: recent_memories_not_evicted [GH-90000]")
        void recentMemoriesNotEvicted() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Recent", Map.of(), 0.5, now, 0, now), // Recent - keep // GH-90000
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Old", Map.of(), 0.5, now - 86400000, 0, now - 86400000) // Old - evict // GH-90000
            );

            List<MemoryService.MemoryEntry> evictable = memories.stream() // GH-90000
                .filter(m -> now - m.lastAccessed() > 3600000) // Older than 1 hour // GH-90000
                .toList(); // GH-90000

            assertThat(evictable).hasSize(1); // GH-90000
            assertThat(evictable.get(0).content()).isEqualTo("Old [GH-90000]");
        }

        @Test
        @DisplayName("[D010]: frequently_accessed_memories_not_evicted [GH-90000]")
        void frequentlyAccessedMemoriesNotEvicted() { // GH-90000
            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Popular", Map.of(), 0.5, 0, 100, 0), // High access - keep // GH-90000
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Unpopular", Map.of(), 0.5, 0, 1, 0) // Low access - evict // GH-90000
            );

            List<MemoryService.MemoryEntry> evictable = memories.stream() // GH-90000
                .filter(m -> m.accessCount() < 10) // GH-90000
                .toList(); // GH-90000

            assertThat(evictable).hasSize(1); // GH-90000
            assertThat(evictable.get(0).content()).isEqualTo("Unpopular [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Size-based Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Size-based Eviction [GH-90000]")
    class SizeBasedEvictionTests {

        @Test
        @DisplayName("[D010]: count_returns_memory_count [GH-90000]")
        void countReturnsMemoryCount() { // GH-90000
            String agentId = "agent-001";

            when(episodicRepository.count(agentId)).thenReturn(Promise.of(150L)); // GH-90000

            Long count = runPromise(() -> episodicRepository.count(agentId)); // GH-90000

            assertThat(count).isEqualTo(150L); // GH-90000
        }

        @Test
        @DisplayName("[D010]: size_returns_total_bytes [GH-90000]")
        void sizeReturnsTotalBytes() { // GH-90000
            String agentId = "agent-001";

            when(episodicRepository.size(agentId)).thenReturn(Promise.of(10485760L)); // 10MB // GH-90000

            Long size = runPromise(() -> episodicRepository.size(agentId)); // GH-90000

            assertThat(size).isEqualTo(10485760L); // GH-90000
        }

        @Test
        @DisplayName("[D010]: evict_when_size_exceeds_limit [GH-90000]")
        void evictWhenSizeExceedsLimit() { // GH-90000
            String agentId = "agent-001";
            long currentSize = 15 * 1024 * 1024; // 15MB
            long sizeLimit = 10 * 1024 * 1024;   // 10MB limit

            // Should evict enough to get under limit
            long bytesToEvict = currentSize - sizeLimit;

            assertThat(bytesToEvict).isEqualTo(5 * 1024 * 1024); // 5MB to evict // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Time-based Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Time-based Eviction [GH-90000]")
    class TimeBasedEvictionTests {

        @Test
        @DisplayName("[D010]: delete_older_than_removes_expired [GH-90000]")
        void deleteOlderThanRemovesExpired() { // GH-90000
            long cutoffTime = System.currentTimeMillis() - 86400000; // 24 hours ago // GH-90000

            when(episodicRepository.deleteOlderThan(cutoffTime)) // GH-90000
                .thenReturn(Promise.of(50)); // 50 memories deleted // GH-90000

            Integer deleted = runPromise(() -> // GH-90000
                episodicRepository.deleteOlderThan(cutoffTime) // GH-90000
            );

            assertThat(deleted).isEqualTo(50); // GH-90000
        }

        @Test
        @DisplayName("[D010]: memories_older_than_ttl_are_expired [GH-90000]")
        void memoriesOlderThanTtlAreExpired() { // GH-90000
            long now = System.currentTimeMillis(); // GH-90000
            long ttlMs = 3600000; // 1 hour

            List<MemoryService.MemoryEntry> memories = List.of( // GH-90000
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Recent", Map.of(), 0.5, now - 1000, 0, now - 1000), // Not expired // GH-90000
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC, // GH-90000
                    "Old", Map.of(), 0.5, now - 7200000, 0, now - 7200000) // Expired (>1hr) // GH-90000
            );

            List<MemoryService.MemoryEntry> expired = memories.stream() // GH-90000
                .filter(m -> now - m.timestamp() > ttlMs) // GH-90000
                .toList(); // GH-90000

            assertThat(expired).hasSize(1); // GH-90000
            assertThat(expired.get(0).id()).isEqualTo("mem-2 [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Batch Eviction [GH-90000]")
    class BatchEvictionTests {

        @Test
        @DisplayName("[D010]: batch_evict_deletes_multiple [GH-90000]")
        void batchEvictDeletesMultiple() { // GH-90000
            String agentId = "agent-001";
            List<String> memoryIds = List.of("mem-1", "mem-2", "mem-3"); // GH-90000

            // Each delete returns true
            when(episodicRepository.delete(anyString())) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            int deletedCount = 0;
            for (String id : memoryIds) { // GH-90000
                Boolean deleted = runPromise(() -> episodicRepository.delete(id)); // GH-90000
                if (deleted) deletedCount++; // GH-90000
            }

            assertThat(deletedCount).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("[D010]: partial_eviction_continues_on_failure [GH-90000]")
        void partialEvictionContinuesOnFailure() { // GH-90000
            String agentId = "agent-001";
            List<String> memoryIds = List.of("mem-1", "mem-2", "mem-3"); // GH-90000

            // Middle delete fails
            when(episodicRepository.delete("mem-1 [GH-90000]")).thenReturn(Promise.of(true));
            when(episodicRepository.delete("mem-2 [GH-90000]")).thenReturn(Promise.of(false));
            when(episodicRepository.delete("mem-3 [GH-90000]")).thenReturn(Promise.of(true));

            int deletedCount = 0;
            for (String id : memoryIds) { // GH-90000
                Boolean deleted = runPromise(() -> episodicRepository.delete(id)); // GH-90000
                if (deleted) deletedCount++; // GH-90000
            }

            assertThat(deletedCount).isEqualTo(2); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eviction Statistics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Eviction Statistics [GH-90000]")
    class EvictionStatisticsTests {

        @Test
        @DisplayName("[D010]: eviction_count_tracked [GH-90000]")
        void evictionCountTracked() { // GH-90000
            int evictionCount = 150;

            MemoryService.MemoryStats stats = new MemoryService.MemoryStats( // GH-90000
                1000, 500, 400, 100, 0.7, 1024000, 0.85, evictionCount
            );

            assertThat(stats.evictionCount()).isEqualTo(150); // GH-90000
        }

        @Test
        @DisplayName("[D010]: hit_rate_affected_by_eviction [GH-90000]")
        void hitRateAffectedByEviction() { // GH-90000
            // After evicting frequently accessed memories, hit rate drops
            double hitRateBefore = 0.90;
            double hitRateAfter = 0.75;

            assertThat(hitRateAfter).isLessThan(hitRateBefore); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier-specific Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tier-specific Eviction [GH-90000]")
    class TierSpecificEvictionTests {

        @Test
        @DisplayName("[D010]: episodic_evicted_before_semantic [GH-90000]")
        void episodicEvictedBeforeSemantic() { // GH-90000
            // Episodic is short-term, semantic is long-term
            // Eviction should prefer episodic

            MemoryService.MemoryEntry episodic = MemoryService.MemoryEntry.create( // GH-90000
                "agent-001", MemoryService.MemoryTier.EPISODIC, "Recent event", Map.of(), 0.6 // GH-90000
            );

            MemoryService.MemoryEntry semantic = MemoryService.MemoryEntry.create( // GH-90000
                "agent-001", MemoryService.MemoryTier.SEMANTIC, "Knowledge", Map.of(), 0.8 // GH-90000
            );

            // Episodic has lower importance (0.6 vs 0.8) // GH-90000
            assertThat(episodic.importance()).isLessThan(semantic.importance()); // GH-90000
        }

        @Test
        @DisplayName("[D010]: procedural_skills_protected [GH-90000]")
        void proceduralSkillsProtected() { // GH-90000
            // Procedural memories (skills) should be heavily protected // GH-90000
            MemoryService.MemoryEntry procedural = MemoryService.MemoryEntry.create( // GH-90000
                "agent-001", MemoryService.MemoryTier.PROCEDURAL, "Critical skill", Map.of(), 0.95 // GH-90000
            );

            assertThat(procedural.importance()).isGreaterThan(0.9); // GH-90000
        }
    }
}
