package com.ghatana.yappc.ai.cache;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Semantic Cache Service for AI Responses (Java/ActiveJ)
 *
 * High-performance semantic caching using embeddings and cosine similarity.
 * Returns cached responses for queries with similarity > 0.95 threshold.
 *
 * @doc.type class
 * @doc.purpose Semantic caching for AI responses using embedding similarity
 * @doc.layer core
 * @doc.pattern Service
 */
public class SemanticCacheService {

    // ============================================================================
    // Configuration
    // ============================================================================

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;
    private static final int DEFAULT_MAX_CACHE_SIZE = 10000;
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final int EMBEDDING_DIMENSION = 1536; // OpenAI ada-002 dimension

    // ============================================================================
    // Types & Records
    // ============================================================================

    public record CacheConfig(
            double similarityThreshold,
            int maxCacheSize,
            Duration ttl,
            boolean enableEdgeCaching,
            String edgeCacheUrl
    ) {
        public static CacheConfig defaults() {
            return new CacheConfig(
                    DEFAULT_SIMILARITY_THRESHOLD,
                    DEFAULT_MAX_CACHE_SIZE,
                    DEFAULT_TTL,
                    false,
                    null
            );
        }
    }

    public record CacheEntry(
            String id,
            String query,
            double[] embedding,
            String response,
            String model,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant lastAccessedAt,
            int hitCount,
            String userId,
            String tenantId
    ) {}

    public record CacheHit(
            String cacheId,
            String originalQuery,
            String matchedQuery,
            String response,
            double similarity,
            long latencySavedMs,
            Instant cachedAt
    ) {}

    public record CacheStats(
            long totalEntries,
            long totalHits,
            long totalMisses,
            double hitRate,
            long avgHitLatencyMicros,
            long avgSavedLatencyMs,
            long memoryUsageBytes,
            Map<String, Long> hitsByModel,
            Map<String, Long> hitsByFeature
    ) {}

    public record SimilarityMatch(
            String cacheId,
            double similarity,
            CacheEntry entry
    ) {}

    // ============================================================================
    // State
    // ============================================================================

    private final CacheConfig config;
    private final Map<String, CacheEntry> cache;
    private final ReadWriteLock lock;
    
    // Metrics
    private long totalHits = 0;
    private long totalMisses = 0;
    private long totalHitLatencyMicros = 0;
    private long totalSavedLatencyMs = 0;

    // Feature tracking
    private final Map<String, Long> hitsByModel = new ConcurrentHashMap<>();
    private final Map<String, Long> hitsByFeature = new ConcurrentHashMap<>();

    // ============================================================================
    // Constructor
    // ============================================================================

    public SemanticCacheService() {
        this(CacheConfig.defaults());
    }

    public SemanticCacheService(CacheConfig config) {
        this.config = config;
        this.cache = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    // ============================================================================
    // Core Cache Operations
    // ============================================================================

    /**
     * Look up cache by semantic similarity
     * Returns the most similar cached response if similarity > threshold
     */
    public Promise<Optional<CacheHit>> lookup(double[] queryEmbedding, String model, String feature) {
        return Promise.ofBlocking(Runnable::run, () -> {
            long startNanos = System.nanoTime();

            lock.readLock().lock();
            try {
                SimilarityMatch bestMatch = null;

                for (CacheEntry entry : cache.values()) {
                    // Skip expired entries
                    if (isExpired(entry)) {
                        continue;
                    }

                    // Optional: filter by model
                    if (model != null && !model.equals(entry.model())) {
                        continue;
                    }

                    double similarity = cosineSimilarity(queryEmbedding, entry.embedding());

                    if (similarity >= config.similarityThreshold()) {
                        if (bestMatch == null || similarity > bestMatch.similarity()) {
                            bestMatch = new SimilarityMatch(entry.id(), similarity, entry);
                        }
                    }
                }

                long latencyMicros = (System.nanoTime() - startNanos) / 1000;
                totalHitLatencyMicros += latencyMicros;

                if (bestMatch != null) {
                    // Update access stats
                    updateAccessStats(bestMatch.entry());
                    totalHits++;
                    
                    if (model != null) {
                        hitsByModel.merge(model, 1L, Long::sum);
                    }
                    if (feature != null) {
                        hitsByFeature.merge(feature, 1L, Long::sum);
                    }

                    // Estimate saved latency (typical AI response time)
                    long estimatedSavedMs = estimateSavedLatency(bestMatch.entry().model());
                    totalSavedLatencyMs += estimatedSavedMs;

                    return Optional.of(new CacheHit(
                            bestMatch.cacheId(),
                            null, // Original query not available
                            bestMatch.entry().query(),
                            bestMatch.entry().response(),
                            bestMatch.similarity(),
                            estimatedSavedMs,
                            bestMatch.entry().createdAt()
                    ));
                }

                totalMisses++;
                return Optional.empty();

            } finally {
                lock.readLock().unlock();
            }
        });
    }

    /**
     * Store a new entry in the cache
     */
    public Promise<CacheEntry> store(
            String query,
            double[] embedding,
            String response,
            String model,
            String userId,
            String tenantId,
            Map<String, Object> metadata
    ) {
        return Promise.ofBlocking(Runnable::run, () -> {
            // Check if we need to evict entries
            if (cache.size() >= config.maxCacheSize()) {
                evictLRU();
            }

            String id = generateId();
            Instant now = Instant.now();

            CacheEntry entry = new CacheEntry(
                    id,
                    query,
                    embedding,
                    response,
                    model,
                    metadata != null ? metadata : Map.of(),
                    now,
                    now,
                    0,
                    userId,
                    tenantId
            );

            lock.writeLock().lock();
            try {
                cache.put(id, entry);
            } finally {
                lock.writeLock().unlock();
            }

            return entry;
        });
    }

    /**
     * Store with deduplication - don't store if similar entry exists
     */
    public Promise<CacheEntry> storeIfNotSimilar(
            String query,
            double[] embedding,
            String response,
            String model,
            String userId,
            String tenantId,
            Map<String, Object> metadata
    ) {
        return lookup(embedding, model, null)
                .then(existing -> {
                    if (existing.isPresent()) {
                        // Similar entry exists, return existing
                        return Promise.of(cache.get(existing.get().cacheId()));
                    }
                    return store(query, embedding, response, model, userId, tenantId, metadata);
                });
    }

    /**
     * Invalidate cache entries by pattern
     */
    public Promise<Integer> invalidate(CacheInvalidationPattern pattern) {
        return Promise.ofBlocking(Runnable::run, () -> {
            lock.writeLock().lock();
            try {
                List<String> toRemove = cache.values().stream()
                        .filter(entry -> matchesPattern(entry, pattern))
                        .map(CacheEntry::id)
                        .toList();

                for (String id : toRemove) {
                    cache.remove(id);
                }

                return toRemove.size();
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * Clear all cache entries
     */
    public Promise<Integer> clear() {
        return Promise.ofBlocking(Runnable::run, () -> {
            lock.writeLock().lock();
            try {
                int count = cache.size();
                cache.clear();
                return count;
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        long totalRequests = totalHits + totalMisses;
        double hitRate = totalRequests > 0 ? (double) totalHits / totalRequests : 0.0;
        long avgHitLatency = totalHits > 0 ? totalHitLatencyMicros / totalHits : 0;
        long avgSavedLatency = totalHits > 0 ? totalSavedLatencyMs / totalHits : 0;

        return new CacheStats(
                cache.size(),
                totalHits,
                totalMisses,
                hitRate,
                avgHitLatency,
                avgSavedLatency,
                estimateMemoryUsage(),
                Map.copyOf(hitsByModel),
                Map.copyOf(hitsByFeature)
        );
    }

    /**
     * Find similar queries (for analytics/debugging)
     */
    public Promise<List<SimilarityMatch>> findSimilar(double[] queryEmbedding, int topK, double minSimilarity) {
        return Promise.ofBlocking(Runnable::run, () -> {
            lock.readLock().lock();
            try {
                return cache.values().stream()
                        .filter(e -> !isExpired(e))
                        .map(entry -> {
                            double similarity = cosineSimilarity(queryEmbedding, entry.embedding());
                            return new SimilarityMatch(entry.id(), similarity, entry);
                        })
                        .filter(m -> m.similarity() >= minSimilarity)
                        .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                        .limit(topK)
                        .toList();
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    /**
     * Warm up cache with pre-computed entries
     */
    public Promise<Integer> warmUp(List<CacheEntry> entries) {
        return Promise.ofBlocking(Runnable::run, () -> {
            lock.writeLock().lock();
            try {
                int added = 0;
                for (CacheEntry entry : entries) {
                    if (cache.size() < config.maxCacheSize()) {
                        cache.put(entry.id(), entry);
                        added++;
                    }
                }
                return added;
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    // ============================================================================
    // Edge Cache Integration
    // ============================================================================

    /**
     * Generate edge cache key for CDN
     */
    public String generateEdgeCacheKey(double[] embedding, String model) {
        // Hash the embedding to create a deterministic cache key
        int hash = Arrays.hashCode(embedding);
        return String.format("ai-cache:%s:%d", model, hash);
    }

    /**
     * Get edge cache headers for response
     */
    public Map<String, String> getEdgeCacheHeaders(CacheEntry entry) {
        if (!config.enableEdgeCaching()) {
            return Map.of();
        }

        long maxAge = config.ttl().toSeconds();
        String cacheKey = generateEdgeCacheKey(entry.embedding(), entry.model());

        return Map.of(
                "Cache-Control", String.format("public, max-age=%d, stale-while-revalidate=%d", maxAge, maxAge / 2),
                "Surrogate-Key", cacheKey,
                "CDN-Cache-Control", String.format("max-age=%d", maxAge),
                "Vary", "Accept-Encoding"
        );
    }

    // ============================================================================
    // Internal Helpers
    // ============================================================================

    /**
     * Calculate cosine similarity between two vectors
     */
    public double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    private boolean isExpired(CacheEntry entry) {
        return entry.createdAt().plus(config.ttl()).isBefore(Instant.now());
    }

    private void updateAccessStats(CacheEntry entry) {
        // Create updated entry with new access time and hit count
        CacheEntry updated = new CacheEntry(
                entry.id(),
                entry.query(),
                entry.embedding(),
                entry.response(),
                entry.model(),
                entry.metadata(),
                entry.createdAt(),
                Instant.now(),
                entry.hitCount() + 1,
                entry.userId(),
                entry.tenantId()
        );

        cache.put(entry.id(), updated);
    }

    private void evictLRU() {
        lock.writeLock().lock();
        try {
            // Find and remove least recently used entries (10% of cache)
            int toRemove = Math.max(1, cache.size() / 10);

            List<String> lruEntries = cache.values().stream()
                    .sorted(Comparator.comparing(CacheEntry::lastAccessedAt))
                    .limit(toRemove)
                    .map(CacheEntry::id)
                    .toList();

            for (String id : lruEntries) {
                cache.remove(id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean matchesPattern(CacheEntry entry, CacheInvalidationPattern pattern) {
        if (pattern.model() != null && !pattern.model().equals(entry.model())) {
            return false;
        }
        if (pattern.userId() != null && !pattern.userId().equals(entry.userId())) {
            return false;
        }
        if (pattern.tenantId() != null && !pattern.tenantId().equals(entry.tenantId())) {
            return false;
        }
        if (pattern.queryPattern() != null && !entry.query().matches(pattern.queryPattern())) {
            return false;
        }
        if (pattern.olderThan() != null && entry.createdAt().isAfter(pattern.olderThan())) {
            return false;
        }
        return true;
    }

    private long estimateSavedLatency(String model) {
        // Typical API response times by model
        return switch (model) {
            case "gpt-4" -> 3000L;
            case "gpt-4-turbo" -> 2000L;
            case "claude-3-opus" -> 2500L;
            case "claude-3-sonnet" -> 1500L;
            case "ollama-llama3" -> 1000L;
            case "ollama-mixtral" -> 1200L;
            default -> 2000L;
        };
    }

    private long estimateMemoryUsage() {
        // Rough estimate: query (avg 500 chars) + embedding (1536 doubles) + response (avg 2000 chars) + overhead
        // Per entry: ~500 + 12288 + 2000 + 200 = ~15KB
        return cache.size() * 15000L;
    }

    private String generateId() {
        return "cache_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ============================================================================
    // Invalidation Pattern
    // ============================================================================

    public record CacheInvalidationPattern(
            String model,
            String userId,
            String tenantId,
            String queryPattern,
            Instant olderThan
    ) {
        public static CacheInvalidationPattern byModel(String model) {
            return new CacheInvalidationPattern(model, null, null, null, null);
        }

        public static CacheInvalidationPattern byUser(String userId) {
            return new CacheInvalidationPattern(null, userId, null, null, null);
        }

        public static CacheInvalidationPattern byTenant(String tenantId) {
            return new CacheInvalidationPattern(null, null, tenantId, null, null);
        }

        public static CacheInvalidationPattern olderThan(Instant timestamp) {
            return new CacheInvalidationPattern(null, null, null, null, timestamp);
        }
    }
}
