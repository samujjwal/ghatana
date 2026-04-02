package com.ghatana.tutorputor.cache;

import com.ghatana.platform.cache.DistributedCacheService;
import com.ghatana.tutorputor.cache.TutorPutorContentCacheService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Integration tests for TutorPutor content caching service
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TutorPutor Content Cache Service Integration Tests")
class TutorPutorContentCacheServiceIntegrationTest {

    @Mock
    private DistributedCacheService cacheService;

    private TutorPutorContentCacheService contentCache;

    @BeforeEach
    void setUp() {
        contentCache = new TutorPutorContentCacheService(cacheService);
    }

    @Nested
    @DisplayName("Generated Content Caching")
    class GeneratedContentCachingTests {

        @Test
        void shouldCacheGeneratedContent() {
            // Given
            String contentId = "content-123";
            GeneratedContent content = new GeneratedContent(
                    contentId,
                    "mathematics",
                    "Prepared content about calculus",
                    System.currentTimeMillis()
            );

            // When
            contentCache.cacheGeneratedContent(contentId, content);

            // Then
            verify(cacheService).put(
                    eq("content:generated:" + contentId),
                    eq(content),
                    eq(3600L)
            );
        }

        @Test
        void shouldRetrieveCachedGeneratedContent() {
            // Given
            String contentId = "content-456";
            GeneratedContent content = new GeneratedContent(
                    contentId,
                    "science",
                    "Physics content",
                    System.currentTimeMillis()
            );
            when(cacheService.get("content:generated:" + contentId, GeneratedContent.class))
                    .thenReturn(Optional.of(content));

            // When
            Optional<GeneratedContent> result = contentCache.getCachedGeneratedContent(contentId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().topic).isEqualTo("science");
        }

        @Test
        void shouldReturnEmptyWhenContentNotCached() {
            // Given
            String contentId = "missing-content";
            when(cacheService.get("content:generated:" + contentId, GeneratedContent.class))
                    .thenReturn(Optional.empty());

            // When
            Optional<GeneratedContent> result = contentCache.getCachedGeneratedContent(contentId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldInvalidateGeneratedContent() {
            // Given
            String contentId = "invalidate-content";

            // When
            contentCache.invalidateGeneratedContent(contentId);

            // Then
            verify(cacheService).invalidate("content:generated:" + contentId);
        }
    }

    @Nested
    @DisplayName("Library Caching")
    class LibraryCachingTests {

        @Test
        void shouldCacheLibrary() {
            // Given
            String libraryId = "library-123";
            ContentLibrary library = new ContentLibrary(
                    libraryId,
                    "Math Fundamentals",
                    45,
                    System.currentTimeMillis()
            );

            // When
            contentCache.cacheLibrary(libraryId, library);

            // Then
            verify(cacheService).put(
                    eq("library:" + libraryId),
                    eq(library),
                    eq(7200L)
            );
        }

        @Test
        void shouldRetrieveCachedLibrary() {
            // Given
            String libraryId = "library-456";
            ContentLibrary library = new ContentLibrary(
                    libraryId,
                    "Science Collection",
                    28,
                    System.currentTimeMillis()
            );
            when(cacheService.get("library:" + libraryId, ContentLibrary.class))
                    .thenReturn(Optional.of(library));

            // When
            Optional<ContentLibrary> result = contentCache.getCachedLibrary(libraryId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().name).isEqualTo("Science Collection");
            assertThat(result.get().itemCount).isEqualTo(28);
        }

        @Test
        void shouldInvalidateLibrary() {
            // Given
            String libraryId = "invalidate-library";

            // When
            contentCache.invalidateLibrary(libraryId);

            // Then
            verify(cacheService).invalidate("library:" + libraryId);
        }
    }

    @Nested
    @DisplayName("Learning Path Caching")
    class LearningPathCachingTests {

        @Test
        void shouldCacheLearningPath() {
            // Given
            String pathId = "path-123";
            String[] sequence = {"content-1", "content-2", "content-3"};
            LearningPath path = new LearningPath(
                    pathId,
                    "user-456",
                    sequence,
                    65,
                    System.currentTimeMillis()
            );

            // When
            contentCache.cacheLearningPath(pathId, path);

            // Then
            verify(cacheService).put(
                    eq("learning-path:" + pathId),
                    eq(path),
                    eq(7200L)
            );
        }

        @Test
        void shouldRetrieveCachedLearningPath() {
            // Given
            String pathId = "path-789";
            String[] sequence = {"content-a", "content-b"};
            LearningPath path = new LearningPath(
                    pathId,
                    "user-123",
                    sequence,
                    30,
                    System.currentTimeMillis()
            );
            when(cacheService.get("learning-path:" + pathId, LearningPath.class))
                    .thenReturn(Optional.of(path));

            // When
            Optional<LearningPath> result = contentCache.getCachedLearningPath(pathId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().userId).isEqualTo("user-123");
            assertThat(result.get().progressPercentage).isEqualTo(30);
        }

        @Test
        void shouldInvalidateUserLearningPaths() {
            // Given
            String userId = "user-789";

            // When
            contentCache.invalidateUserLearningPaths(userId);

            // Then
            verify(cacheService).invalidatePattern("learning-path:user:" + userId + ":*");
        }
    }

    @Nested
    @DisplayName("Topic Claims Caching")
    class TopicClaimsCachingTests {

        @Test
        void shouldCacheTopicClaims() {
            // Given
            String topicId = "topic-123";
            String[] claims = {"claim-1", "claim-2", "claim-3"};
            TopicClaims topicClaims = new TopicClaims(
                    topicId,
                    claims,
                    3,
                    System.currentTimeMillis()
            );

            // When
            contentCache.cacheTopicClaims(topicId, topicClaims);

            // Then
            verify(cacheService).put(
                    eq("topic:claims:" + topicId),
                    eq(topicClaims),
                    eq(3600L)
            );
        }

        @Test
        void shouldRetrieveCachedTopicClaims() {
            // Given
            String topicId = "topic-456";
            String[] claims = {"calculus-1", "calculus-2"};
            TopicClaims topicClaims = new TopicClaims(
                    topicId,
                    claims,
                    2,
                    System.currentTimeMillis()
            );
            when(cacheService.get("topic:claims:" + topicId, TopicClaims.class))
                    .thenReturn(Optional.of(topicClaims));

            // When
            Optional<TopicClaims> result = contentCache.getCachedTopicClaims(topicId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().claimCount).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Cache Invalidation Patterns")
    class CacheInvalidationPatternsTests {

        @Test
        void shouldInvalidateAllContentCache() {
            // When
            contentCache.invalidateAllContentCache();

            // Then
            verify(cacheService).invalidatePattern("content:*");
        }

        @Test
        void shouldProvideAccurateCacheMetrics() {
            // Given
            DistributedCacheService.CacheStatistics stats = 
                    new DistributedCacheService.CacheStatistics(100, 512000);
            when(cacheService.getStatistics("content:*")).thenReturn(stats);

            // When
            TutorPutorContentCacheService.CacheMetrics metrics = contentCache.getCacheMetrics();

            // Then
            assertThat(metrics.cachedItems).isEqualTo(100);
            assertThat(metrics.totalSizeBytes).isEqualTo(512000);
        }
    }

    @Nested
    @DisplayName("Cache TTL Configuration")
    class CacheTTLConfigurationTests {

        @Test
        void shouldUseDifferentTTLsForDifferentContentTypes() {
            // Given
            GeneratedContent content = new GeneratedContent("id", "topic", "content", System.currentTimeMillis());
            LearningPath path = new LearningPath("id", "user", new String[0], 0, System.currentTimeMillis());
            TopicClaims claims = new TopicClaims("id", new String[0], 0, System.currentTimeMillis());

            // When
            contentCache.cacheGeneratedContent("id", content);
            contentCache.cacheLearningPath("id", path);
            contentCache.cacheTopicClaims("id", claims);

            // Then - verify different TTLs used
            verify(cacheService).put(anyString(), eq(content), eq(3600L));  // 1 hour
            verify(cacheService).put(anyString(), eq(path), eq(7200L));     // 2 hours
            verify(cacheService).put(anyString(), eq(claims), eq(3600L));   // 1 hour
        }
    }

    @Nested
    @DisplayName("Multiple Content Types")
    class MultipleContentTypesTests {

        @Test
        void shouldHandleMultipleCacheOperations() {
            // Given
            GeneratedContent content = new GeneratedContent("c1", "math", "content", System.currentTimeMillis());
            ContentLibrary library = new ContentLibrary("lib1", "Math", 10, System.currentTimeMillis());
            LearningPath path = new LearningPath("p1", "user1", new String[0], 50, System.currentTimeMillis());

            // When
            contentCache.cacheGeneratedContent("c1", content);
            contentCache.cacheLibrary("lib1", library);
            contentCache.cacheLearningPath("p1", path);

            // Then - all three operations should succeed
            verify(cacheService).put(anyString(), eq(content), anyLong());
            verify(cacheService).put(anyString(), eq(library), anyLong());
            verify(cacheService).put(anyString(), eq(path), anyLong());
        }

        @Test
        void shouldInvalidateMultipleItemTypes() {
            // When
            contentCache.invalidateGeneratedContent("c1");
            contentCache.invalidateLibrary("lib1");
            contentCache.invalidateUserLearningPaths("user1");
            contentCache.invalidateAllContentCache();

            // Then - all invalidations should succeed
            verify(cacheService).invalidate("content:generated:c1");
            verify(cacheService).invalidate("library:lib1");
            verify(cacheService).invalidatePattern("learning-path:user:user1:*");
            verify(cacheService).invalidatePattern("content:*");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        void shouldHandleEmptyContent() {
            // Given
            GeneratedContent emptyContent = new GeneratedContent("id", "", "", System.currentTimeMillis());

            // When
            contentCache.cacheGeneratedContent("id", emptyContent);

            // Then
            verify(cacheService).put(anyString(), eq(emptyContent), anyLong());
        }

        @Test
        void shouldHandleNullTopicsInClaims() {
            // Given
            TopicClaims nullTopicClaims = new TopicClaims(null, new String[0], 0, System.currentTimeMillis());

            // When + Then - should not throw
            contentCache.cacheTopicClaims("null-topic", nullTopicClaims);

            verify(cacheService).put(anyString(), eq(nullTopicClaims), anyLong());
        }

        @Test
        void shouldHandleLargeItemCounts() {
            // Given
            String[] largeSequence = new String[1000];
            for (int i = 0; i < 1000; i++) {
                largeSequence[i] = "content-" + i;
            }
            LearningPath largePath = new LearningPath("large-path", "user", largeSequence, 100, System.currentTimeMillis());

            // When
            contentCache.cacheLearningPath("large-path", largePath);

            // Then
            verify(cacheService).put(anyString(), eq(largePath), anyLong());
        }
    }
}
