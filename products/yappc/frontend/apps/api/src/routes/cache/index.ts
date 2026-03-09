/**
 * Semantic Cache API Routes (Node.js/Fastify)
 * 
 * REST endpoints for cache statistics, invalidation, and management.
 * 
 * @doc.type module
 * @doc.purpose Cache management API for UI and admin operations
 * @doc.layer product
 * @doc.pattern Controller
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { semanticCacheService, CacheInvalidationPattern } from '../../services/cache/SemanticCacheService';

// ============================================================================
// Request Types
// ============================================================================

interface LookupRequest {
    embedding: number[];
    model?: string;
    feature?: string;
}

interface StoreRequest {
    query: string;
    embedding: number[];
    response: string;
    model: string;
    metadata?: Record<string, unknown>;
    tokenCount?: number;
    latencyMs?: number;
}

interface InvalidateRequest {
    model?: string;
    userId?: string;
    tenantId?: string;
    queryPattern?: string;
    olderThan?: string; // ISO date string
}

interface FindSimilarRequest {
    embedding: number[];
    topK?: number;
    minSimilarity?: number;
}

// ============================================================================
// Route Registration
// ============================================================================

export async function semanticCacheRoutes(fastify: FastifyInstance): Promise<void> {
    // ============================================================================
    // Cache Statistics
    // ============================================================================

    /**
     * Get cache statistics
     * GET /api/cache/stats
     */
    fastify.get('/stats', async (request, reply) => {
        const stats = semanticCacheService.getStats();

        // Calculate target progress (80% hit rate goal)
        const hitRateTarget = 0.8;
        const progress = Math.min(100, (stats.hitRate / hitRateTarget) * 100);

        return reply.send({
            success: true,
            data: {
                ...stats,
                target: {
                    hitRate: hitRateTarget,
                    progress: Math.round(progress),
                    isAchieved: stats.hitRate >= hitRateTarget,
                },
                savings: {
                    totalLatencySavedMs: stats.avgSavedLatencyMs * stats.totalHits,
                    estimatedCostSavings: stats.cacheEfficiency.toFixed(4),
                    avgLatencySavedPerHit: stats.avgSavedLatencyMs,
                },
                performance: {
                    avgLookupLatencyMicros: stats.avgHitLatencyMicros,
                    avgLookupLatencyMs: (stats.avgHitLatencyMicros / 1000).toFixed(3),
                },
            },
        });
    });

    /**
     * Get cache entries count by model
     * GET /api/cache/stats/by-model
     */
    fastify.get('/stats/by-model', async (request, reply) => {
        const stats = semanticCacheService.getStats();

        return reply.send({
            success: true,
            data: stats.hitsByModel,
        });
    });

    /**
     * Get cache entries count by feature
     * GET /api/cache/stats/by-feature
     */
    fastify.get('/stats/by-feature', async (request, reply) => {
        const stats = semanticCacheService.getStats();

        return reply.send({
            success: true,
            data: stats.hitsByFeature,
        });
    });

    // ============================================================================
    // Cache Operations
    // ============================================================================

    /**
     * Look up cache entry by embedding
     * POST /api/cache/lookup
     */
    fastify.post<{ Body: LookupRequest }>(
        '/lookup',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['embedding'],
                    properties: {
                        embedding: {
                            type: 'array',
                            items: { type: 'number' },
                        },
                        model: { type: 'string' },
                        feature: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            const userId = (request as unknown).user?.id;
            const tenantId = (request as unknown).user?.tenantId;

            const result = await semanticCacheService.lookup(request.body.embedding, {
                model: request.body.model,
                feature: request.body.feature,
                userId,
                tenantId,
            });

            if ('cacheId' in result) {
                // Cache hit
                return reply.send({
                    success: true,
                    hit: true,
                    data: result,
                });
            } else {
                // Cache miss
                return reply.send({
                    success: true,
                    hit: false,
                    data: result,
                });
            }
        }
    );

    /**
     * Store entry in cache
     * POST /api/cache/store
     */
    fastify.post<{ Body: StoreRequest }>(
        '/store',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['query', 'embedding', 'response', 'model'],
                    properties: {
                        query: { type: 'string' },
                        embedding: {
                            type: 'array',
                            items: { type: 'number' },
                        },
                        response: { type: 'string' },
                        model: { type: 'string' },
                        metadata: { type: 'object' },
                        tokenCount: { type: 'number' },
                        latencyMs: { type: 'number' },
                    },
                },
            },
        },
        async (request, reply) => {
            const userId = (request as unknown).user?.id;
            const tenantId = (request as unknown).user?.tenantId;

            const entry = await semanticCacheService.storeIfNotSimilar(
                request.body.query,
                request.body.embedding,
                request.body.response,
                request.body.model,
                {
                    userId,
                    tenantId,
                    metadata: request.body.metadata,
                    tokenCount: request.body.tokenCount,
                    latencyMs: request.body.latencyMs,
                }
            );

            return reply.status(201).send({
                success: true,
                data: {
                    id: entry.id,
                    createdAt: entry.createdAt,
                    model: entry.model,
                },
            });
        }
    );

    /**
     * Find similar entries (debugging/analytics)
     * POST /api/cache/similar
     */
    fastify.post<{ Body: FindSimilarRequest }>(
        '/similar',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['embedding'],
                    properties: {
                        embedding: {
                            type: 'array',
                            items: { type: 'number' },
                        },
                        topK: { type: 'number', default: 5 },
                        minSimilarity: { type: 'number', default: 0.8 },
                    },
                },
            },
        },
        async (request, reply) => {
            const matches = await semanticCacheService.findSimilar(
                request.body.embedding,
                request.body.topK || 5,
                request.body.minSimilarity || 0.8
            );

            return reply.send({
                success: true,
                data: matches.map((m: unknown) => ({
                    cacheId: m.cacheId,
                    similarity: m.similarity,
                    query: m.entry.query.substring(0, 200) + '...',
                    model: m.entry.model,
                    createdAt: m.entry.createdAt,
                    hitCount: m.entry.hitCount,
                })),
                count: matches.length,
            });
        }
    );

    // ============================================================================
    // Cache Invalidation
    // ============================================================================

    /**
     * Invalidate cache entries by pattern
     * POST /api/cache/invalidate
     */
    fastify.post<{ Body: InvalidateRequest }>(
        '/invalidate',
        {
            schema: {
                body: {
                    type: 'object',
                    properties: {
                        model: { type: 'string' },
                        userId: { type: 'string' },
                        tenantId: { type: 'string' },
                        queryPattern: { type: 'string' },
                        olderThan: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            const pattern: CacheInvalidationPattern = {
                model: request.body.model,
                userId: request.body.userId,
                tenantId: request.body.tenantId,
                queryPattern: request.body.queryPattern ? new RegExp(request.body.queryPattern) : undefined,
                olderThan: request.body.olderThan ? new Date(request.body.olderThan) : undefined,
            };

            const removed = await semanticCacheService.invalidate(pattern);

            return reply.send({
                success: true,
                data: {
                    removedCount: removed,
                    pattern: {
                        ...request.body,
                        queryPattern: request.body.queryPattern, // Keep original string
                    },
                },
            });
        }
    );

    /**
     * Clear all cache entries
     * DELETE /api/cache/all
     */
    fastify.delete('/all', async (request, reply) => {
        // Require admin role
        if (!(request as unknown).user?.roles?.includes('admin')) {
            return reply.status(403).send({
                success: false,
                error: 'Admin access required',
            });
        }

        const removed = await semanticCacheService.clear();

        return reply.send({
            success: true,
            data: {
                removedCount: removed,
            },
            message: 'Cache cleared successfully',
        });
    });

    // ============================================================================
    // Edge Cache Headers
    // ============================================================================

    /**
     * Get edge cache configuration
     * GET /api/cache/edge-config
     */
    fastify.get('/edge-config', async (request, reply) => {
        const stats = semanticCacheService.getStats();

        return reply.send({
            success: true,
            data: {
                enabled: false, // Would be from config
                cacheControlHeader: 'public, max-age=86400, stale-while-revalidate=43200',
                surrogateKeyPrefix: 'ai-cache',
                varyHeaders: ['Accept-Encoding'],
                estimatedHitRate: stats.hitRate,
            },
        });
    });
}

export default semanticCacheRoutes;
