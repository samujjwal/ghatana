/**
 * Semantic Cache Service for AI Responses (Node.js/TypeScript)
 * 
 * High-performance semantic caching using embeddings and cosine similarity.
 * Returns cached responses for queries with similarity > 0.95 threshold.
 * 
 * @doc.type class
 * @doc.purpose Semantic caching with embedding similarity for AI responses
 * @doc.layer product
 * @doc.pattern Service
 */

import { EventEmitter } from 'events';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface CacheConfig {
    similarityThreshold: number;    // Default: 0.95
    maxCacheSize: number;           // Default: 10000
    ttlMs: number;                  // Default: 24 hours
    enableEdgeCaching: boolean;     // CDN integration
    edgeCacheUrl?: string;
    embeddingDimension: number;     // Default: 1536 (OpenAI ada-002)
}

export interface CacheEntry {
    id: string;
    query: string;
    embedding: number[];
    response: string;
    model: string;
    metadata?: Record<string, unknown>;
    createdAt: Date;
    lastAccessedAt: Date;
    hitCount: number;
    userId?: string;
    tenantId?: string;
    tokenCount?: number;
    latencyMs?: number;
}

export interface CacheHit {
    cacheId: string;
    originalQuery: string;
    matchedQuery: string;
    response: string;
    similarity: number;
    latencySavedMs: number;
    cachedAt: Date;
}

export interface CacheMiss {
    query: string;
    nearestSimilarity: number;
    nearestQuery?: string;
    reason: 'no_match' | 'below_threshold' | 'expired' | 'empty_cache';
}

export interface CacheStats {
    totalEntries: number;
    totalHits: number;
    totalMisses: number;
    hitRate: number;
    avgHitLatencyMicros: number;
    avgSavedLatencyMs: number;
    memoryUsageBytes: number;
    hitsByModel: Record<string, number>;
    hitsByFeature: Record<string, number>;
    cacheEfficiency: number;  // Cost savings ratio
}

export interface SimilarityMatch {
    cacheId: string;
    similarity: number;
    entry: CacheEntry;
}

export interface CacheInvalidationPattern {
    model?: string;
    userId?: string;
    tenantId?: string;
    queryPattern?: RegExp;
    olderThan?: Date;
}

// ============================================================================
// Default Configuration
// ============================================================================

const DEFAULT_CONFIG: CacheConfig = {
    similarityThreshold: 0.95,
    maxCacheSize: 10000,
    ttlMs: 24 * 60 * 60 * 1000, // 24 hours
    enableEdgeCaching: false,
    embeddingDimension: 1536,
};

// Model latency estimates for savings calculation
const MODEL_LATENCY_ESTIMATES: Record<string, number> = {
    'gpt-4': 3000,
    'gpt-4-turbo': 2000,
    'claude-3-opus': 2500,
    'claude-3-sonnet': 1500,
    'ollama-llama3': 1000,
    'ollama-mixtral': 1200,
};

// ============================================================================
// Semantic Cache Service
// ============================================================================

export class SemanticCacheService extends EventEmitter {
    private cache: Map<string, CacheEntry> = new Map();
    private config: CacheConfig;

    // Metrics
    private totalHits = 0;
    private totalMisses = 0;
    private totalHitLatencyMicros = 0;
    private totalSavedLatencyMs = 0;
    private hitsByModel: Record<string, number> = {};
    private hitsByFeature: Record<string, number> = {};

    constructor(config: Partial<CacheConfig> = {}) {
        super();
        this.config = { ...DEFAULT_CONFIG, ...config };

        // Start TTL cleanup interval
        this.startCleanupInterval();
    }

    // ============================================================================
    // Core Cache Operations
    // ============================================================================

    /**
     * Look up cache by semantic similarity
     * Returns the most similar cached response if similarity > threshold
     */
    async lookup(
        queryEmbedding: number[],
        options: {
            model?: string;
            feature?: string;
            userId?: string;
            tenantId?: string;
        } = {}
    ): Promise<CacheHit | CacheMiss> {
        const startTime = performance.now();
        let nearestSimilarity = 0;
        let nearestQuery: string | undefined;

        if (this.cache.size === 0) {
            this.totalMisses++;
            return {
                query: '',
                nearestSimilarity: 0,
                reason: 'empty_cache',
            };
        }

        let bestMatch: SimilarityMatch | null = null;

        for (const entry of this.cache.values()) {
            // Skip expired entries
            if (this.isExpired(entry)) {
                continue;
            }

            // Filter by model if specified
            if (options.model && entry.model !== options.model) {
                continue;
            }

            // Filter by tenant if specified
            if (options.tenantId && entry.tenantId !== options.tenantId) {
                continue;
            }

            const similarity = this.cosineSimilarity(queryEmbedding, entry.embedding);

            // Track nearest for miss reporting
            if (similarity > nearestSimilarity) {
                nearestSimilarity = similarity;
                nearestQuery = entry.query;
            }

            if (similarity >= this.config.similarityThreshold) {
                if (!bestMatch || similarity > bestMatch.similarity) {
                    bestMatch = { cacheId: entry.id, similarity, entry };
                }
            }
        }

        const latencyMicros = (performance.now() - startTime) * 1000;
        this.totalHitLatencyMicros += latencyMicros;

        if (bestMatch) {
            // Update access stats
            this.updateAccessStats(bestMatch.entry);
            this.totalHits++;

            // Track by model and feature
            if (options.model) {
                this.hitsByModel[options.model] = (this.hitsByModel[options.model] || 0) + 1;
            }
            if (options.feature) {
                this.hitsByFeature[options.feature] = (this.hitsByFeature[options.feature] || 0) + 1;
            }

            // Calculate saved latency
            const savedMs = this.estimateSavedLatency(bestMatch.entry.model);
            this.totalSavedLatencyMs += savedMs;

            this.emit('cache:hit', {
                cacheId: bestMatch.cacheId,
                similarity: bestMatch.similarity,
                model: bestMatch.entry.model,
                savedMs,
            });

            return {
                cacheId: bestMatch.cacheId,
                originalQuery: '',
                matchedQuery: bestMatch.entry.query,
                response: bestMatch.entry.response,
                similarity: bestMatch.similarity,
                latencySavedMs: savedMs,
                cachedAt: bestMatch.entry.createdAt,
            };
        }

        this.totalMisses++;
        this.emit('cache:miss', { nearestSimilarity, reason: 'below_threshold' });

        return {
            query: '',
            nearestSimilarity,
            nearestQuery,
            reason: nearestSimilarity > 0 ? 'below_threshold' : 'no_match',
        };
    }

    /**
     * Store a new entry in the cache
     */
    async store(
        query: string,
        embedding: number[],
        response: string,
        model: string,
        options: {
            userId?: string;
            tenantId?: string;
            metadata?: Record<string, unknown>;
            tokenCount?: number;
            latencyMs?: number;
        } = {}
    ): Promise<CacheEntry> {
        // Check if we need to evict entries
        if (this.cache.size >= this.config.maxCacheSize) {
            this.evictLRU();
        }

        const id = this.generateId();
        const now = new Date();

        const entry: CacheEntry = {
            id,
            query,
            embedding,
            response,
            model,
            metadata: options.metadata,
            createdAt: now,
            lastAccessedAt: now,
            hitCount: 0,
            userId: options.userId,
            tenantId: options.tenantId,
            tokenCount: options.tokenCount,
            latencyMs: options.latencyMs,
        };

        this.cache.set(id, entry);
        this.emit('cache:store', { id, model, queryLength: query.length });

        return entry;
    }

    /**
     * Store with deduplication - don't store if similar entry exists
     */
    async storeIfNotSimilar(
        query: string,
        embedding: number[],
        response: string,
        model: string,
        options: {
            userId?: string;
            tenantId?: string;
            metadata?: Record<string, unknown>;
            tokenCount?: number;
            latencyMs?: number;
        } = {}
    ): Promise<CacheEntry> {
        const existing = await this.lookup(embedding, { model });

        if ('cacheId' in existing) {
            // Similar entry exists, return it
            return this.cache.get(existing.cacheId)!;
        }

        return this.store(query, embedding, response, model, options);
    }

    /**
     * Invalidate cache entries by pattern
     */
    async invalidate(pattern: CacheInvalidationPattern): Promise<number> {
        const toRemove: string[] = [];

        for (const entry of this.cache.values()) {
            if (this.matchesPattern(entry, pattern)) {
                toRemove.push(entry.id);
            }
        }

        for (const id of toRemove) {
            this.cache.delete(id);
        }

        this.emit('cache:invalidate', { count: toRemove.length, pattern });
        return toRemove.length;
    }

    /**
     * Clear all cache entries
     */
    async clear(): Promise<number> {
        const count = this.cache.size;
        this.cache.clear();
        this.emit('cache:clear', { count });
        return count;
    }

    /**
     * Get cache statistics
     */
    getStats(): CacheStats {
        const totalRequests = this.totalHits + this.totalMisses;
        const hitRate = totalRequests > 0 ? this.totalHits / totalRequests : 0;
        const avgHitLatency = this.totalHits > 0 ? this.totalHitLatencyMicros / this.totalHits : 0;
        const avgSavedLatency = this.totalHits > 0 ? this.totalSavedLatencyMs / this.totalHits : 0;

        // Calculate efficiency (cost savings)
        const avgCostPerRequest = 0.002; // ~$0.002 per GPT-4 request
        const cacheEfficiency = this.totalHits * avgCostPerRequest;

        return {
            totalEntries: this.cache.size,
            totalHits: this.totalHits,
            totalMisses: this.totalMisses,
            hitRate,
            avgHitLatencyMicros: avgHitLatency,
            avgSavedLatencyMs: avgSavedLatency,
            memoryUsageBytes: this.estimateMemoryUsage(),
            hitsByModel: { ...this.hitsByModel },
            hitsByFeature: { ...this.hitsByFeature },
            cacheEfficiency,
        };
    }

    /**
     * Find similar queries (for analytics/debugging)
     */
    async findSimilar(
        queryEmbedding: number[],
        topK: number = 5,
        minSimilarity: number = 0.8
    ): Promise<SimilarityMatch[]> {
        const matches: SimilarityMatch[] = [];

        for (const entry of this.cache.values()) {
            if (this.isExpired(entry)) continue;

            const similarity = this.cosineSimilarity(queryEmbedding, entry.embedding);
            if (similarity >= minSimilarity) {
                matches.push({ cacheId: entry.id, similarity, entry });
            }
        }

        return matches
            .sort((a, b) => b.similarity - a.similarity)
            .slice(0, topK);
    }

    /**
     * Warm up cache with pre-computed entries
     */
    async warmUp(entries: CacheEntry[]): Promise<number> {
        let added = 0;

        for (const entry of entries) {
            if (this.cache.size < this.config.maxCacheSize) {
                this.cache.set(entry.id, entry);
                added++;
            }
        }

        this.emit('cache:warmup', { added, total: entries.length });
        return added;
    }

    // ============================================================================
    // Edge Cache Integration
    // ============================================================================

    /**
     * Generate edge cache key for CDN
     */
    generateEdgeCacheKey(embedding: number[], model: string): string {
        // Simple hash of embedding for deterministic key
        const hash = embedding.reduce((acc, val) => acc + Math.round(val * 1000), 0);
        return `ai-cache:${model}:${Math.abs(hash)}`;
    }

    /**
     * Get edge cache headers for response
     */
    getEdgeCacheHeaders(entry: CacheEntry): Record<string, string> {
        if (!this.config.enableEdgeCaching) {
            return {};
        }

        const maxAge = Math.floor(this.config.ttlMs / 1000);
        const cacheKey = this.generateEdgeCacheKey(entry.embedding, entry.model);

        return {
            'Cache-Control': `public, max-age=${maxAge}, stale-while-revalidate=${Math.floor(maxAge / 2)}`,
            'Surrogate-Key': cacheKey,
            'CDN-Cache-Control': `max-age=${maxAge}`,
            'Vary': 'Accept-Encoding',
        };
    }

    // ============================================================================
    // Similarity Calculation
    // ============================================================================

    /**
     * Calculate cosine similarity between two vectors
     */
    cosineSimilarity(vectorA: number[], vectorB: number[]): number {
        if (vectorA.length !== vectorB.length) {
            throw new Error('Vectors must have same dimension');
        }

        let dotProduct = 0;
        let normA = 0;
        let normB = 0;

        for (let i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        const denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator === 0 ? 0 : dotProduct / denominator;
    }

    /**
     * Calculate Euclidean distance (alternative metric)
     */
    euclideanDistance(vectorA: number[], vectorB: number[]): number {
        if (vectorA.length !== vectorB.length) {
            throw new Error('Vectors must have same dimension');
        }

        let sum = 0;
        for (let i = 0; i < vectorA.length; i++) {
            sum += Math.pow(vectorA[i] - vectorB[i], 2);
        }

        return Math.sqrt(sum);
    }

    // ============================================================================
    // Internal Helpers
    // ============================================================================

    private isExpired(entry: CacheEntry): boolean {
        return Date.now() - entry.createdAt.getTime() > this.config.ttlMs;
    }

    private updateAccessStats(entry: CacheEntry): void {
        const updated: CacheEntry = {
            ...entry,
            lastAccessedAt: new Date(),
            hitCount: entry.hitCount + 1,
        };
        this.cache.set(entry.id, updated);
    }

    private evictLRU(): void {
        const toRemove = Math.max(1, Math.floor(this.cache.size * 0.1));
        const entries = Array.from(this.cache.values())
            .sort((a, b) => a.lastAccessedAt.getTime() - b.lastAccessedAt.getTime())
            .slice(0, toRemove);

        for (const entry of entries) {
            this.cache.delete(entry.id);
        }

        this.emit('cache:evict', { count: toRemove, reason: 'lru' });
    }

    private matchesPattern(entry: CacheEntry, pattern: CacheInvalidationPattern): boolean {
        if (pattern.model && entry.model !== pattern.model) return false;
        if (pattern.userId && entry.userId !== pattern.userId) return false;
        if (pattern.tenantId && entry.tenantId !== pattern.tenantId) return false;
        if (pattern.queryPattern && !pattern.queryPattern.test(entry.query)) return false;
        if (pattern.olderThan && entry.createdAt > pattern.olderThan) return false;
        return true;
    }

    private estimateSavedLatency(model: string): number {
        return MODEL_LATENCY_ESTIMATES[model] || 2000;
    }

    private estimateMemoryUsage(): number {
        // Rough estimate per entry: query + embedding + response + metadata
        // ~500 + 12288 + 2000 + 200 = ~15KB per entry
        return this.cache.size * 15000;
    }

    private generateId(): string {
        return `cache_${Date.now()}_${Math.random().toString(36).substring(2, 10)}`;
    }

    private startCleanupInterval(): void {
        // Clean expired entries every hour
        setInterval(() => {
            let removed = 0;
            for (const entry of this.cache.values()) {
                if (this.isExpired(entry)) {
                    this.cache.delete(entry.id);
                    removed++;
                }
            }
            if (removed > 0) {
                this.emit('cache:cleanup', { removed });
            }
        }, 60 * 60 * 1000);
    }
}

// ============================================================================
// Singleton Export
// ============================================================================

export const semanticCacheService = new SemanticCacheService();

export default SemanticCacheService;
