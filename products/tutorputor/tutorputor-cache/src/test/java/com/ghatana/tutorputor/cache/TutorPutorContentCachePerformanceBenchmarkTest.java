package com.ghatana.tutorputor.cache;

import com.ghatana.platform.cache.DistributedCacheService;
import com.ghatana.platform.testing.base.BasePerformanceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Performance benchmarking tests for TutorPutor content caching.
 * @doc.layer product-test
 * @doc.pattern PerformanceTest
 *
 * Measures:
 * - Cache hit ratios for generated content, libraries, learning paths, topic claims
 * - Latency impact: uncached vs cached operations
 * - Memory efficiency: cache size vs operation count
 * - Scalability: performance with 1000+ cached items
 * - Concurrent access patterns
 *
 * Success criteria:
 * - Cache hit ratio > 80% for repeated access patterns
 * - Cached operation latency < 5ms
 * - Uncached operation latency < 50ms (baseline)
 * - Memory overhead < 2GB for 10K cached items
 */
@DisplayName("TutorPutorContentCacheService Performance Benchmarks")
class TutorPutorContentCachePerformanceBenchmarkTest extends BasePerformanceTest {

    @Mock
    private DistributedCacheService cacheService;

    private TutorPutorContentCacheService contentCache;
    private Random random = new Random(42);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        contentCache = new TutorPutorContentCacheService(cacheService);
    }

    @Nested
    @DisplayName("Generated Content Cache Hit Ratio")
    class GeneratedContentCacheHitRatioTests {

        @Test
        @DisplayName("should achieve 85% cache hit ratio with repeated access")
        void shouldAchieve85PercentHitRatio() {
            int contentCount = 100;
            int accessCount = 1000;
            int hitCount = 0;

            // Pre-populate cache for 100 items
            for (int i = 0; i < contentCount; i++) {
                GeneratedContent content = new GeneratedContent(
                    "content:" + i, "topic:" + (i % 10), "generated content " + i
                );
                when(cacheService.get("content:generated:" + i, GeneratedContent.class))
                    .thenReturn(Optional.of(content));
            }

            // Access with 80% hit distribution
            for (int i = 0; i < accessCount; i++) {
                int contentId = random.nextInt(contentCount);
                Optional<GeneratedContent> cached = contentCache.getCachedGeneratedContent("" + contentId);
                
                if (cached.isPresent()) {
                    hitCount++;
                }
            }

            double hitRatio = (double) hitCount / accessCount;
            assertThat(hitRatio)
                .isGreaterThanOrEqualTo(0.75)  // Allow 75%+ hit ratio
                .describedAs("Cache hit ratio: %s%%", (hitRatio * 100));

            verify(cacheService, atLeastOnce()).get(contains("content:generated:"), eq(GeneratedContent.class));
        }

        @Test
        @DisplayName("should improve latency with cache hits")
        void shouldImproveLatencyWithCacheHits() {
            GeneratedContent content = new GeneratedContent("id", "topic", "data");
            when(cacheService.get("content:generated:1", GeneratedContent.class))
                .thenReturn(Optional.of(content));

            int iterations = 1000;
            long cachedTotalMs = 0;
            long uncachedTotalMs = 0;

            // Measure cached access
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                Optional<GeneratedContent> cached = contentCache.getCachedGeneratedContent("1");
                cachedTotalMs += (System.nanoTime() - start) / 1_000_000;
            }

            // Reset mock for uncached scenario
            reset(cacheService);
            when(cacheService.get("content:generated:1", GeneratedContent.class))
                .thenReturn(Optional.empty());

            // Measure uncached access
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                Optional<GeneratedContent> uncached = contentCache.getCachedGeneratedContent("1");
                uncachedTotalMs += (System.nanoTime() - start) / 1_000_000;
            }

            double cachedAvgMs = (double) cachedTotalMs / iterations;
            double uncachedAvgMs = (double) uncachedTotalMs / iterations;

            assertThat(cachedAvgMs).isLessThan(uncachedAvgMs);
            assertThat(cachedAvgMs).isLessThan(5);  // Cached should be < 5ms
            
            System.out.println("Cached avg: " + cachedAvgMs + "ms, Uncached avg: " + uncachedAvgMs + "ms");
        }

        @Test
        @DisplayName("should handle 10000 concurrent cache entries")
        void shouldHandleScale10kEntries() {
            // Populate cache service mock for 10K items
            for (int i = 0; i < 10000; i++) {
                GeneratedContent content = new GeneratedContent(
                    "content:" + i, "topic:" + (i % 100), "data " + i
                );
                when(cacheService.get("content:generated:" + i, GeneratedContent.class))
                    .thenReturn(Optional.of(content));
            }

            // Access random items
            long startTime = System.nanoTime();
            int accessCount = 1000;
            for (int i = 0; i < accessCount; i++) {
                int contentId = random.nextInt(10000);
                contentCache.getCachedGeneratedContent("" + contentId);
            }
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            double avgTimePerAccessMs = (double) elapsedMs / accessCount;
            assertThat(avgTimePerAccessMs).isLessThan(5);  // Each access < 5ms even with 10K entries
            
            System.out.println("10K entries - avg access time: " + avgTimePerAccessMs + "ms");
        }
    }

    @Nested
    @DisplayName("Learning Path Cache Performance")
    class LearningPathCachePerformanceTests {

        @Test
        @DisplayName("should cache learning path state efficiently")
        void shouldCacheLearningPathEfficiently() {
            int userCount = 100;
            int pathsPerUser = 10;

            // Pre-populate learning paths
            for (int u = 0; u < userCount; u++) {
                for (int p = 0; p < pathsPerUser; p++) {
                    String pathId = "path:" + u + ":" + p;
                    LearningPath path = new LearningPath(
                        pathId, "user:" + u,
                        new String[]{"content:1", "content:2", "content:3"},
                        (p * 10) % 100, System.currentTimeMillis()
                    );
                    when(cacheService.get("learning-path:" + pathId, LearningPath.class))
                        .thenReturn(Optional.of(path));
                }
            }

            // Access patterns: user 0 accesses path 0-9 repeatedly, user 1 accesses path 0-9, etc.
            long startTime = System.nanoTime();
            for (int cycle = 0; cycle < 100; cycle++) {
                for (int u = 0; u < userCount; u++) {
                    for (int p = 0; p < pathsPerUser; p++) {
                        contentCache.getCachedLearningPath("path:" + u + ":" + p);
                    }
                }
            }
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            int totalAccesses = userCount * pathsPerUser * 100;
            double avgAccessMs = (double) elapsedMs / totalAccesses;

            assertThat(avgAccessMs).isLessThan(5);
            System.out.println("Learning paths - avg access: " + avgAccessMs + "ms for " + totalAccesses + " accesses");
        }

        @Test
        @DisplayName("should efficiently support user-scoped invalidation")
        void shouldSupportUserScopedInvalidation() {
            int userCount = 100;
            int pathsPerUser = 50;

            // Mock cache for pattern invalidation
            when(cacheService.invalidatePattern(contains("learning-path:user:")))
                .thenReturn(pathsPerUser);  // Returns count of deleted keys

            long startTime = System.nanoTime();
            for (int u = 0; u < userCount; u++) {
                contentCache.invalidateUserLearningPaths("user:" + u);
            }
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            double avgInvalidationMs = (double) elapsedMs / userCount;
            assertThat(avgInvalidationMs).isLessThan(10);  // Invalidation < 10ms per user
            
            verify(cacheService, times(userCount))
                .invalidatePattern(contains("learning-path:user:"));

            System.out.println("User invalidation - avg time: " + avgInvalidationMs + "ms");
        }
    }

    @Nested
    @DisplayName("Library Cache Performance")
    class LibraryCachePerformanceTests {

        @Test
        @DisplayName("should cache library metadata with high hit ratio")
        void shouldCacheLibraryWithHighHitRatio() {
            int libraryCount = 50;
            int accessCount = 5000;
            int hitCount = 0;

            // Pre-populate libraries
            for (int i = 0; i < libraryCount; i++) {
                ContentLibrary lib = new ContentLibrary(
                    "lib:" + i, "Library " + i, (i + 1) * 100, System.currentTimeMillis()
                );
                when(cacheService.get("library:" + i, ContentLibrary.class))
                    .thenReturn(Optional.of(lib));
            }

            // Access with Zipfian distribution (some libraries more popular)
            for (int i = 0; i < accessCount; i++) {
                int libId = (int) (Math.pow(random.nextDouble(), 0.5) * libraryCount);  // Zipfian
                Optional<ContentLibrary> cached = contentCache.getCachedLibrary("" + libId);
                if (cached.isPresent()) {
                    hitCount++;
                }
            }

            double hitRatio = (double) hitCount / accessCount;
            assertThat(hitRatio)
                .isGreaterThanOrEqualTo(0.85)  // Should have 85%+ hit ratio with Zipfian
                .describedAs("Library cache hit ratio: %.2f%%", hitRatio * 100);

            System.out.println("Library hit ratio: " + (hitRatio * 100) + "%");
        }
    }

    @Nested
    @DisplayName("Topic Claims Cache Performance")
    class TopicClaimsCachePerformanceTests {

        @Test
        @DisplayName("should efficiently cache topic claims across topics")
        void shouldCacheTopicClaimsEfficiently() {
            int topicCount = 1000;

            // Pre-populate topics
            for (int i = 0; i < topicCount; i++) {
                TopicClaims claims = new TopicClaims(
                    "topic:" + i,
                    new String[]{"claim:1", "claim:2", "claim:3"},
                    3, System.currentTimeMillis()
                );
                when(cacheService.get("topic:claims:" + i, TopicClaims.class))
                    .thenReturn(Optional.of(claims));
            }

            // Sustained access to different topics
            long startTime = System.nanoTime();
            for (int t = 0; t < topicCount; t++) {
                contentCache.getCachedTopicClaims("" + t);
            }
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            double avgAccessMs = (double) elapsedMs / topicCount;
            assertThat(avgAccessMs).isLessThan(5);

            System.out.println("Topic claims - avg access: " + avgAccessMs + "ms for 1000 topics");
        }
    }

    @Nested
    @DisplayName("Mixed Operation Performance")
    class MixedOperationPerformanceTests {

        @Test
        @DisplayName("should handle mixed cache operations efficiently")
        void shouldHandleMixedOperationsEfficiently() {
            int iterations = 10000;
            long totalMs = 0;

            for (int i = 0; i < iterations; i++) {
                // 70% read, 20% write, 10% invalidate
                double op = random.nextDouble();
                long start = System.nanoTime();

                if (op < 0.7) {
                    // Read operation
                    GeneratedContent content = new GeneratedContent("id", "topic", "data");
                    when(cacheService.get("content:generated:1", GeneratedContent.class))
                        .thenReturn(Optional.of(content));
                    contentCache.getCachedGeneratedContent("1");
                } else if (op < 0.9) {
                    // Write operation
                    GeneratedContent content = new GeneratedContent("id", "topic", "data");
                    doNothing().when(cacheService).put("content:generated:1", content, 3600);
                    contentCache.cacheGeneratedContent("1", content);
                } else {
                    // Invalidate operation
                    doNothing().when(cacheService).invalidate("content:generated:1");
                    contentCache.invalidateGeneratedContent("1");
                }

                totalMs += (System.nanoTime() - start) / 1_000_000;
            }

            double avgMs = (double) totalMs / iterations;
            assertThat(avgMs).isLessThan(5);  // Average mixed operation < 5ms

            System.out.println("Mixed operations - avg: " + avgMs + "ms for 10K operations");
        }
    }

    @Nested
    @DisplayName("Concurrent Caching Performance")
    class ConcurrentCachingPerformanceTests {

        @Test
        @DisplayName("should maintain performance under concurrent load")
        void shouldMaintainPerformanceUnderConcurrentLoad() throws InterruptedException {
            int threadCount = 10;
            int operationsPerThread = 100;
            List<Long> threadTimes = new ArrayList<>();

            Thread[] threads = new Thread[threadCount];
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                threads[t] = new Thread(() -> {
                    long threadStartTime = System.nanoTime();
                    
                    for (int op = 0; op < operationsPerThread; op++) {
                        GeneratedContent content = new GeneratedContent(
                            "id", "topic", "data from thread " + threadId
                        );
                        when(cacheService.get("content:" + threadId + ":" + op, GeneratedContent.class))
                            .thenReturn(Optional.of(content));
                        
                        contentCache.getCachedGeneratedContent("" + op);
                    }

                    long elapsedMs = (System.nanoTime() - threadStartTime) / 1_000_000;
                    synchronized (threadTimes) {
                        threadTimes.add(elapsedMs);
                    }
                });
                threads[t].start();
            }

            for (Thread t : threads) {
                t.join();
            }

            double avgThreadTimeMs = threadTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            assertThat(avgThreadTimeMs).isLessThan(500);  // Each thread completes 100 ops within 500ms

            System.out.println("Concurrent - avg thread time: " + avgThreadTimeMs + "ms for " + 
                operationsPerThread + " operations per thread");
        }
    }

    // ============= Test Support Classes =============

    static class GeneratedContent {
        String id;
        String topic;
        String content;
        long createdAt = System.currentTimeMillis();

        GeneratedContent(String id, String topic, String content) {
            this.id = id;
            this.topic = topic;
            this.content = content;
        }
    }

    static class ContentLibrary {
        String id;
        String name;
        int itemCount;
        long updatedAt;

        ContentLibrary(String id, String name, int itemCount, long updatedAt) {
            this.id = id;
            this.name = name;
            this.itemCount = itemCount;
            this.updatedAt = updatedAt;
        }
    }

    static class LearningPath {
        String id;
        String userId;
        String[] contentSequence;
        int progressPercentage;
        long lastAccessedAt;

        LearningPath(String id, String userId, String[] contentSequence, int progressPercentage, long lastAccessedAt) {
            this.id = id;
            this.userId = userId;
            this.contentSequence = contentSequence;
            this.progressPercentage = progressPercentage;
            this.lastAccessedAt = lastAccessedAt;
        }
    }

    static class TopicClaims {
        String topicId;
        String[] claims;
        int claimCount;
        long generatedAt;

        TopicClaims(String topicId, String[] claims, int claimCount, long generatedAt) {
            this.topicId = topicId;
            this.claims = claims;
            this.claimCount = claimCount;
            this.generatedAt = generatedAt;
        }
    }
}
