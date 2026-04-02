package com.ghatana.tutorputor.cache;

import com.ghatana.platform.cache.DistributedCacheService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Content generation and library caching for TutorPutor
 * @doc.layer product
 * @doc.pattern Service
 */
public class TutorPutorContentCacheService {

    private static final Logger log = LoggerFactory.getLogger(TutorPutorContentCacheService.class);

    // Cache TTLs
    private static final long CONTENT_GENERATION_TTL = 3600; // 1 hour
    private static final long LIBRARY_TTL = 7200; // 2 hours
    private static final long LEARNING_PATH_TTL = 7200; // 2 hours
    private static final long TOPIC_CLAIMS_TTL = 3600; // 1 hour

    private final DistributedCacheService cacheService;

    public TutorPutorContentCacheService(DistributedCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Get cached generated content
     */
    public Optional<GeneratedContent> getCachedGeneratedContent(String contentId) {
        String key = "content:generated:" + contentId;
        return cacheService.get(key, GeneratedContent.class);
    }

    /**
     * Cache generated content
     */
    public void cacheGeneratedContent(String contentId, GeneratedContent content) {
        String key = "content:generated:" + contentId;
        cacheService.put(key, content, CONTENT_GENERATION_TTL);
        log.info("Cached generated content: {}", contentId);
    }

    /**
     * Get cached library
     */
    public Optional<ContentLibrary> getCachedLibrary(String libraryId) {
        String key = "library:" + libraryId;
        return cacheService.get(key, ContentLibrary.class);
    }

    /**
     * Cache library
     */
    public void cacheLibrary(String libraryId, ContentLibrary library) {
        String key = "library:" + libraryId;
        cacheService.put(key, library, LIBRARY_TTL);
        log.info("Cached library: {}", libraryId);
    }

    /**
     * Get cached learning path
     */
    public Optional<LearningPath> getCachedLearningPath(String pathId) {
        String key = "learning-path:" + pathId;
        return cacheService.get(key, LearningPath.class);
    }

    /**
     * Cache learning path
     */
    public void cacheLearningPath(String pathId, LearningPath path) {
        String key = "learning-path:" + pathId;
        cacheService.put(key, path, LEARNING_PATH_TTL);
        log.info("Cached learning path: {}", pathId);
    }

    /**
     * Get cached topic claims
     */
    public Optional<TopicClaims> getCachedTopicClaims(String topicId) {
        String key = "topic:claims:" + topicId;
        return cacheService.get(key, TopicClaims.class);
    }

    /**
     * Cache topic claims
     */
    public void cacheTopicClaims(String topicId, TopicClaims claims) {
        String key = "topic:claims:" + topicId;
        cacheService.put(key, claims, TOPIC_CLAIMS_TTL);
        log.info("Cached topic claims: {}", topicId);
    }

    /**
     * Invalidate content generation cache
     */
    public void invalidateGeneratedContent(String contentId) {
        String key = "content:generated:" + contentId;
        cacheService.invalidate(key);
        log.info("Invalidated generated content cache: {}", contentId);
    }

    /**
     * Invalidate library cache
     */
    public void invalidateLibrary(String libraryId) {
        String key = "library:" + libraryId;
        cacheService.invalidate(key);
        log.info("Invalidated library cache: {}", libraryId);
    }

    /**
     * Invalidate all learning path caches for a user
     */
    public void invalidateUserLearningPaths(String userId) {
        String pattern = "learning-path:user:" + userId + ":*";
        cacheService.invalidatePattern(pattern);
        log.info("Invalidated learning paths for user: {}", userId);
    }

    /**
     * Invalidate all content caches
     */
    public void invalidateAllContentCache() {
        cacheService.invalidatePattern("content:*");
        log.info("Invalidated all content caches");
    }

    /**
     * Get cache statistics
     */
    public CacheMetrics getCacheMetrics() {
        DistributedCacheService.CacheStatistics stats = cacheService.getStatistics("content:*");
        return new CacheMetrics(stats.totalKeys, stats.totalSize);
    }

    /**
     * Cache metrics
     */
    public static class CacheMetrics {
        public final long cachedItems;
        public final long totalSizeBytes;

        public CacheMetrics(long cachedItems, long totalSizeBytes) {
            this.cachedItems = cachedItems;
            this.totalSizeBytes = totalSizeBytes;
        }
    }

    /**
     * Domain model: Generated content
     */
    public static class GeneratedContent {
        public String id;
        public String topic;
        public String content;
        public long createdAt;

        public GeneratedContent() {}

        public GeneratedContent(String id, String topic, String content, long createdAt) {
            this.id = id;
            this.topic = topic;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    /**
     * Domain model: Content library
     */
    public static class ContentLibrary {
        public String id;
        public String name;
        public int itemCount;
        public long updatedAt;

        public ContentLibrary() {}

        public ContentLibrary(String id, String name, int itemCount, long updatedAt) {
            this.id = id;
            this.name = name;
            this.itemCount = itemCount;
            this.updatedAt = updatedAt;
        }
    }

    /**
     * Domain model: Learning path
     */
    public static class LearningPath {
        public String id;
        public String userId;
        public String[] contentSequence;
        public int progressPercentage;
        public long lastAccessedAt;

        public LearningPath() {}

        public LearningPath(String id, String userId, String[] contentSequence, int progressPercentage, long lastAccessedAt) {
            this.id = id;
            this.userId = userId;
            this.contentSequence = contentSequence;
            this.progressPercentage = progressPercentage;
            this.lastAccessedAt = lastAccessedAt;
        }
    }

    /**
     * Domain model: Topic claims
     */
    public static class TopicClaims {
        public String topicId;
        public String[] claims;
        public int claimCount;
        public long generatedAt;

        public TopicClaims() {}

        public TopicClaims(String topicId, String[] claims, int claimCount, long generatedAt) {
            this.topicId = topicId;
            this.claims = claims;
            this.claimCount = claimCount;
            this.generatedAt = generatedAt;
        }
    }
}
