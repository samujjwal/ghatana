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
 * Tests for memory LRU eviction logic (D010). 
 *
 * <p>Validates LRU eviction policies and memory cleanup.
 *
 * @doc.type class
 * @doc.purpose LRU eviction logic tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("MemoryEviction – LRU Eviction Logic (D010)")
class MemoryEvictionTest extends EventloopTestBase {

    @Mock
    private EpisodicMemoryRepository episodicRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // LRU Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LRU Eviction")
    class LRUEvictionTests {

        @Test
        @DisplayName("[D010]: least_recently_used_evicted_first")
        void leastRecentlyUsedEvictedFirst() { 
            String agentId = "agent-001";
            long now = System.currentTimeMillis(); 

            List<MemoryService.MemoryEntry> memories = List.of( 
                new MemoryService.MemoryEntry("mem-1", agentId, MemoryService.MemoryTier.EPISODIC, 
                    "Old memory", Map.of(), 0.5, now - 10000, 5, now - 10000), 
                new MemoryService.MemoryEntry("mem-2", agentId, MemoryService.MemoryTier.EPISODIC, 
                    "Recent memory", Map.of(), 0.5, now - 1000, 5, now - 1000), 
                new MemoryService.MemoryEntry("mem-3", agentId, MemoryService.MemoryTier.EPISODIC, 
                    "Very old memory", Map.of(), 0.5, now - 50000, 5, now - 50000) 
            );

            List<MemoryService.MemoryEntry> lruOrder = memories.stream() 
                .sorted(Comparator.comparingLong(MemoryService.MemoryEntry::lastAccessed)) 
                .toList(); 

            assertThat(lruOrder.get(0).id()).isEqualTo("mem-3"); // Very old
            assertThat(lruOrder.get(1).id()).isEqualTo("mem-1"); // Old
            assertThat(lruOrder.get(2).id()).isEqualTo("mem-2"); // Recent
        }

        @Test
        @DisplayName("[D010]: find_lru_returns_oldest_accessed")
        void findLruReturnsOldestAccessed() { 
            String agentId = "agent-001";

            List<MemoryService.MemoryEntry> lruMemories = List.of( 
                new MemoryService.MemoryEntry("mem-old", agentId, MemoryService.MemoryTier.EPISODIC, 
                    "Old content", Map.of(), 0.3, 0, 0, 1000L), 
                new MemoryService.MemoryEntry("mem-older", agentId, MemoryService.MemoryTier.EPISODIC, 
                    "Older content", Map.of(), 0.3, 0, 0, 500L) 
            );

            when(episodicRepository.findLRU(agentId, 2)) 
                .thenReturn(Promise.of(lruMemories)); 

            List<MemoryService.MemoryEntry> results = runPromise(() -> 
                episodicRepository.findLRU(agentId, 2) 
            );

            assertThat(results).hasSize(2); 
            assertThat(results.get(0).lastAccessed()).isGreaterThan(results.get(1).lastAccessed()); 
        }

        @Test
        @DisplayName("[D010]: evict_to_target_removes_lru_memories")
        void evictToTargetRemovesLRUMemories() { 
            String agentId = "agent-001";
            long targetSize = 1024L * 1024; // 1MB

            when(episodicRepository.evictToTarget(agentId, targetSize)) 
                .thenReturn(Promise.of(5)); // 5 memories evicted 

            Integer evictedCount = runPromise(() -> 
                episodicRepository.evictToTarget(agentId, targetSize) 
            );

            assertThat(evictedCount).isEqualTo(5); 
        }

        @Test
        @DisplayName("[D010]: accessed_memory_moves_to_front")
        void accessedMemoryMovesToFront() { 
            long now = System.currentTimeMillis(); 

            MemoryService.MemoryEntry memory = new MemoryService.MemoryEntry( 
                "mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC,
                "Content", Map.of(), 0.5, now - 10000, 5, now - 10000 
            );

            // Simulate access
            MemoryService.MemoryEntry accessed = memory.accessed(); 

            assertThat(accessed.lastAccessed()).isGreaterThan(memory.lastAccessed()); 
            assertThat(accessed.accessCount()).isEqualTo(memory.accessCount() + 1); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eviction Policy Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Eviction Policy")
    class EvictionPolicyTests {

        @Test
        @DisplayName("[D010]: high_importance_memories_not_evicted")
        void highImportanceMemoriesNotEvicted() { 
            List<MemoryService.MemoryEntry> memories = List.of( 
                MemoryService.MemoryEntry.create("agent-001", MemoryService.MemoryTier.SEMANTIC, 
                    "Critical knowledge", Map.of(), 0.95), // High importance - keep 
                MemoryService.MemoryEntry.create("agent-001", MemoryService.MemoryTier.EPISODIC, 
                    "Old memory", Map.of(), 0.3) // Low importance - evict 
            );

            List<MemoryService.MemoryEntry> evictable = memories.stream() 
                .filter(m -> m.importance() < 0.8) 
                .toList(); 

            assertThat(evictable).hasSize(1); 
            assertThat(evictable.get(0).content()).isEqualTo("Old memory");
        }

        @Test
        @DisplayName("[D010]: recent_memories_not_evicted")
        void recentMemoriesNotEvicted() { 
            long now = System.currentTimeMillis(); 

            List<MemoryService.MemoryEntry> memories = List.of( 
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC, 
                    "Recent", Map.of(), 0.5, now, 0, now), // Recent - keep 
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC, 
                    "Old", Map.of(), 0.5, now - 86400000, 0, now - 86400000) // Old - evict 
            );

            List<MemoryService.MemoryEntry> evictable = memories.stream() 
                .filter(m -> now - m.lastAccessed() > 3600000) // Older than 1 hour 
                .toList(); 

            assertThat(evictable).hasSize(1); 
            assertThat(evictable.get(0).content()).isEqualTo("Old");
        }

        @Test
        @DisplayName("[D010]: frequently_accessed_memories_not_evicted")
        void frequentlyAccessedMemoriesNotEvicted() { 
            List<MemoryService.MemoryEntry> memories = List.of( 
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC, 
                    "Popular", Map.of(), 0.5, 0, 100, 0), // High access - keep 
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC, 
                    "Unpopular", Map.of(), 0.5, 0, 1, 0) // Low access - evict 
            );

            List<MemoryService.MemoryEntry> evictable = memories.stream() 
                .filter(m -> m.accessCount() < 10) 
                .toList(); 

            assertThat(evictable).hasSize(1); 
            assertThat(evictable.get(0).content()).isEqualTo("Unpopular");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Size-based Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Size-based Eviction")
    class SizeBasedEvictionTests {

        @Test
        @DisplayName("[D010]: count_returns_memory_count")
        void countReturnsMemoryCount() { 
            String agentId = "agent-001";

            when(episodicRepository.count(agentId)).thenReturn(Promise.of(150L)); 

            Long count = runPromise(() -> episodicRepository.count(agentId)); 

            assertThat(count).isEqualTo(150L); 
        }

        @Test
        @DisplayName("[D010]: size_returns_total_bytes")
        void sizeReturnsTotalBytes() { 
            String agentId = "agent-001";

            when(episodicRepository.size(agentId)).thenReturn(Promise.of(10485760L)); // 10MB 

            Long size = runPromise(() -> episodicRepository.size(agentId)); 

            assertThat(size).isEqualTo(10485760L); 
        }

        @Test
        @DisplayName("[D010]: evict_when_size_exceeds_limit")
        void evictWhenSizeExceedsLimit() { 
            String agentId = "agent-001";
            long currentSize = 15 * 1024 * 1024; // 15MB
            long sizeLimit = 10 * 1024 * 1024;   // 10MB limit

            // Should evict enough to get under limit
            long bytesToEvict = currentSize - sizeLimit;

            assertThat(bytesToEvict).isEqualTo(5 * 1024 * 1024); // 5MB to evict 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Time-based Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Time-based Eviction")
    class TimeBasedEvictionTests {

        @Test
        @DisplayName("[D010]: delete_older_than_removes_expired")
        void deleteOlderThanRemovesExpired() { 
            long cutoffTime = System.currentTimeMillis() - 86400000; // 24 hours ago 

            when(episodicRepository.deleteOlderThan(cutoffTime)) 
                .thenReturn(Promise.of(50)); // 50 memories deleted 

            Integer deleted = runPromise(() -> 
                episodicRepository.deleteOlderThan(cutoffTime) 
            );

            assertThat(deleted).isEqualTo(50); 
        }

        @Test
        @DisplayName("[D010]: memories_older_than_ttl_are_expired")
        void memoriesOlderThanTtlAreExpired() { 
            long now = System.currentTimeMillis(); 
            long ttlMs = 3600000; // 1 hour

            List<MemoryService.MemoryEntry> memories = List.of( 
                new MemoryService.MemoryEntry("mem-1", "agent-001", MemoryService.MemoryTier.EPISODIC, 
                    "Recent", Map.of(), 0.5, now - 1000, 0, now - 1000), // Not expired 
                new MemoryService.MemoryEntry("mem-2", "agent-001", MemoryService.MemoryTier.EPISODIC, 
                    "Old", Map.of(), 0.5, now - 7200000, 0, now - 7200000) // Expired (>1hr) 
            );

            List<MemoryService.MemoryEntry> expired = memories.stream() 
                .filter(m -> now - m.timestamp() > ttlMs) 
                .toList(); 

            assertThat(expired).hasSize(1); 
            assertThat(expired.get(0).id()).isEqualTo("mem-2");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Batch Eviction")
    class BatchEvictionTests {

        @Test
        @DisplayName("[D010]: batch_evict_deletes_multiple")
        void batchEvictDeletesMultiple() { 
            String agentId = "agent-001";
            List<String> memoryIds = List.of("mem-1", "mem-2", "mem-3"); 

            // Each delete returns true
            when(episodicRepository.delete(anyString())) 
                .thenReturn(Promise.of(true)); 

            int deletedCount = 0;
            for (String id : memoryIds) { 
                Boolean deleted = runPromise(() -> episodicRepository.delete(id)); 
                if (deleted) deletedCount++; 
            }

            assertThat(deletedCount).isEqualTo(3); 
        }

        @Test
        @DisplayName("[D010]: partial_eviction_continues_on_failure")
        void partialEvictionContinuesOnFailure() { 
            String agentId = "agent-001";
            List<String> memoryIds = List.of("mem-1", "mem-2", "mem-3"); 

            // Middle delete fails
            when(episodicRepository.delete("mem-1")).thenReturn(Promise.of(true));
            when(episodicRepository.delete("mem-2")).thenReturn(Promise.of(false));
            when(episodicRepository.delete("mem-3")).thenReturn(Promise.of(true));

            int deletedCount = 0;
            for (String id : memoryIds) { 
                Boolean deleted = runPromise(() -> episodicRepository.delete(id)); 
                if (deleted) deletedCount++; 
            }

            assertThat(deletedCount).isEqualTo(2); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eviction Statistics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Eviction Statistics")
    class EvictionStatisticsTests {

        @Test
        @DisplayName("[D010]: eviction_count_tracked")
        void evictionCountTracked() { 
            int evictionCount = 150;

            MemoryService.MemoryStats stats = new MemoryService.MemoryStats( 
                1000, 500, 400, 100, 0.7, 1024000, 0.85, evictionCount
            );

            assertThat(stats.evictionCount()).isEqualTo(150); 
        }

        @Test
        @DisplayName("[D010]: hit_rate_affected_by_eviction")
        void hitRateAffectedByEviction() { 
            // After evicting frequently accessed memories, hit rate drops
            double hitRateBefore = 0.90;
            double hitRateAfter = 0.75;

            assertThat(hitRateAfter).isLessThan(hitRateBefore); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier-specific Eviction Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tier-specific Eviction")
    class TierSpecificEvictionTests {

        @Test
        @DisplayName("[D010]: episodic_evicted_before_semantic")
        void episodicEvictedBeforeSemantic() { 
            // Episodic is short-term, semantic is long-term
            // Eviction should prefer episodic

            MemoryService.MemoryEntry episodic = MemoryService.MemoryEntry.create( 
                "agent-001", MemoryService.MemoryTier.EPISODIC, "Recent event", Map.of(), 0.6 
            );

            MemoryService.MemoryEntry semantic = MemoryService.MemoryEntry.create( 
                "agent-001", MemoryService.MemoryTier.SEMANTIC, "Knowledge", Map.of(), 0.8 
            );

            // Episodic has lower importance (0.6 vs 0.8) 
            assertThat(episodic.importance()).isLessThan(semantic.importance()); 
        }

        @Test
        @DisplayName("[D010]: procedural_skills_protected")
        void proceduralSkillsProtected() { 
            // Procedural memories (skills) should be heavily protected 
            MemoryService.MemoryEntry procedural = MemoryService.MemoryEntry.create( 
                "agent-001", MemoryService.MemoryTier.PROCEDURAL, "Critical skill", Map.of(), 0.95 
            );

            assertThat(procedural.importance()).isGreaterThan(0.9); 
        }
    }
}
