/**
 * Enhanced Search API routes for Flashit Web API
 * Provides semantic and hybrid search capabilities
 *
 * @doc.type route
 * @doc.purpose Advanced search endpoints with vector similarity
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance, FastifyRequest } from 'fastify';
import { JwtPayload } from '../lib/auth.js';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { requireAuth } from '../lib/auth.js';
import { prisma } from '../lib/prisma.js';
import { EnhancedSearchService, SearchType } from '../services/search/enhanced-search-service';
import { VectorEmbeddingService } from '../services/embeddings/vector-service';
import { generateInsights, detectPatterns, findConnections } from '../services/java-agents/reflection-client.js';

// Validation schemas
const searchSchema = z.object({
  query: z.string().min(1).max(500),
  type: z.enum(['semantic', 'text', 'hybrid', 'similar']).default('hybrid'),
  filters: z.object({
    sphereIds: z.array(z.string().uuid()).optional(),
    emotions: z.array(z.string()).optional(),
    tags: z.array(z.string()).optional(),
    importance: z.object({
      min: z.number().min(1).max(5).optional(),
      max: z.number().min(1).max(5).optional(),
    }).optional(),
    dateRange: z.object({
      from: z.string().datetime().optional(),
      to: z.string().datetime().optional(),
    }).optional(),
    contentTypes: z.array(z.enum(['text', 'audio', 'video'])).optional(),
    hasTranscript: z.boolean().optional(),
    hasReflection: z.boolean().optional(),
  }).optional(),
  limit: z.number().min(1).max(100).default(20),
  offset: z.number().min(0).default(0),
  includeHighlights: z.boolean().default(true),
  includeReflections: z.boolean().default(false),
  similarityThreshold: z.number().min(0).max(1).default(0.5),
  boostFactors: z.object({
    recency: z.number().min(0).max(2).optional(),
    importance: z.number().min(0).max(2).optional(),
    emotion: z.number().min(0).max(2).optional(),
  }).optional(),
});

const suggestionsSchema = z.object({
  query: z.string().max(100).optional().default(''),
  limit: z.number().min(1).max(20).default(5),
});

const similarMomentsSchema = z.object({
  momentId: z.string().uuid(),
  limit: z.number().min(1).max(50).default(10),
  similarityThreshold: z.number().min(0).max(1).default(0.7),
});

const generateEmbeddingSchema = z.object({
  momentId: z.string().uuid(),
  priority: z.enum(['high', 'normal', 'low']).default('normal'),
});

const generateReflectionSchema = z.object({
  momentId: z.string().uuid(),
  type: z.enum(['insights', 'patterns', 'connections']).default('insights'),
  priority: z.enum(['high', 'normal', 'low']).default('normal'),
});

export default async function searchRoutes(fastify: FastifyInstance) {

  /**
   * Enhanced search endpoint
   * POST /api/search
   */
  fastify.post('/', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(searchSchema),
      response: {
        200: zodToJsonSchema(z.object({
          results: z.array(z.object({
            momentId: z.string(),
            title: z.string(),
            content: z.string(),
            transcript: z.string().optional(),
            sphereId: z.string(),
            sphereName: z.string(),
            capturedAt: z.string(),
            emotions: z.array(z.string()),
            tags: z.array(z.string()),
            importance: z.number().optional(),
            score: z.number(),
            similarity: z.number().optional(),
            highlights: z.object({
              content: z.array(z.string()).optional(),
              transcript: z.array(z.string()).optional(),
            }).optional(),
            reflection: z.object({
              insights: z.array(z.string()),
              themes: z.array(z.string()),
            }).optional(),
          })),
          totalCount: z.number(),
          analytics: z.object({
            processingTimeMs: z.number(),
            resultCount: z.number(),
            type: z.string(),
          }),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = (request.user as JwtPayload).userId;
    const searchOptions = {
      ...request.body,
      userId,
      filters: request.body.filters ? {
        ...request.body.filters,
        dateRange: request.body.filters.dateRange ? {
          from: request.body.filters.dateRange.from ? new Date(request.body.filters.dateRange.from) : undefined,
          to: request.body.filters.dateRange.to ? new Date(request.body.filters.dateRange.to) : undefined,
        } : undefined,
      } : undefined,
    };

    try {
      const searchResults = await EnhancedSearchService.hybridSearch(searchOptions);

      return {
        results: searchResults.results.map(result => ({
          ...result,
          capturedAt: result.capturedAt.toISOString(),
        })),
        totalCount: searchResults.totalCount,
        analytics: {
          processingTimeMs: searchResults.analytics.processingTimeMs,
          resultCount: searchResults.analytics.resultCount,
          type: searchResults.analytics.type,
        },
      };

    } catch (error) {
      fastify.log.error('Search failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Search temporarily unavailable',
      });
    }
  });

  /**
   * Get search suggestions
   * GET /api/search/suggestions
   */
  fastify.get('/suggestions', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(suggestionsSchema),
      response: {
        200: zodToJsonSchema(z.object({
          suggestions: z.array(z.string()),
        })),
      },
    },
  }, async (request, reply) => {
    const { query, limit } = request.query;
    const userId = (request.user as JwtPayload).userId;

    try {
      const suggestions = await EnhancedSearchService.getSearchSuggestions(userId, query, limit);

      return { suggestions };

    } catch (error) {
      fastify.log.error('Search suggestions failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get search suggestions',
      });
    }
  });

  /**
   * Find similar moments
   * POST /api/search/similar
   */
  fastify.post('/similar', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(similarMomentsSchema),
      response: {
        200: zodToJsonSchema(z.object({
          similarMoments: z.array(z.object({
            momentId: z.string(),
            similarity: z.number(),
            content: z.string(),
            sphereName: z.string(),
            capturedAt: z.string(),
            tags: z.array(z.string()),
            emotions: z.array(z.string()),
          })),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentId, limit, similarityThreshold } = request.body;
    const userId = (request.user as JwtPayload).userId;

    try {
      // Verify access to the moment
      const moment = await prisma.moment.findFirst({
        where: {
          id: momentId,
          sphere: {
            sphereAccess: {
              some: {
                userId,
                revokedAt: null,
              },
            },
          },
        },
        include: { sphere: true },
      });

      if (!moment) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Moment not found or access denied',
        });
      }

      // Get user's accessible spheres
      const userSpheres = await prisma.sphereAccess.findMany({
        where: { userId, revokedAt: null },
        select: { sphereId: true },
      });

      const sphereIds = userSpheres.map(sa => sa.sphereId);

      // Find similar moments
      const queryText = moment.contentTranscript
        ? `${moment.contentText} ${moment.contentTranscript}`
        : moment.contentText;

      const similarMoments = await VectorEmbeddingService.findSimilarMoments(
        queryText,
        sphereIds,
        {
          limit: limit + 1, // +1 to account for the original moment
          similarityThreshold,
        }
      );

      // Filter out the original moment
      const filtered = similarMoments
        .filter(sm => sm.momentId !== momentId)
        .slice(0, limit);

      return {
        similarMoments: filtered.map(sm => ({
          momentId: sm.momentId,
          similarity: sm.similarity,
          content: sm.contentText,
          sphereName: sm.sphereName,
          capturedAt: sm.capturedAt.toISOString(),
          tags: [], // Would need to fetch from database
          emotions: [], // Would need to fetch from database
        })),
      };

    } catch (error) {
      fastify.log.error('Similar moments search failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to find similar moments',
      });
    }
  });

  /**
   * Generate embeddings for a moment
   * POST /api/search/embeddings/generate
   */
  fastify.post('/embeddings/generate', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(generateEmbeddingSchema),
      response: {
        200: zodToJsonSchema(z.object({
          jobIds: z.array(z.string()),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentId, priority } = request.body;
    const userId = (request.user as JwtPayload).userId;

    try {
      // Verify access to the moment
      const moment = await prisma.moment.findFirst({
        where: {
          id: momentId,
          sphere: {
            sphereAccess: {
              some: {
                userId,
                revokedAt: null,
                role: { in: ['OWNER', 'EDITOR'] }, // Only editors can generate embeddings
              },
            },
          },
        },
      });

      if (!moment) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Moment not found or insufficient permissions',
        });
      }

      // Generate embeddings
      const priorityNumber = priority === 'high' ? 1 : priority === 'low' ? 10 : 5;
      const jobIds = await VectorEmbeddingService.generateMomentEmbeddings(momentId, priorityNumber);

      return {
        jobIds,
        message: `Started ${jobIds.length} embedding generation jobs`,
      };

    } catch (error) {
      fastify.log.error('Embedding generation failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to generate embeddings',
      });
    }
  });

  /**
   * Generate AI reflection for a moment
   * POST /api/search/reflection/generate
   */
  fastify.post('/reflection/generate', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(generateReflectionSchema),
      response: {
        200: zodToJsonSchema(z.object({
          jobId: z.string(),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentId, type, priority } = request.body;
    const userId = (request.user as JwtPayload).userId;

    try {
      // Verify access to the moment
      const moment = await prisma.moment.findFirst({
        where: {
          id: momentId,
          sphere: {
            sphereAccess: {
              some: {
                userId,
                revokedAt: null,
              },
            },
          },
        },
      });

      if (!moment) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Moment not found or access denied',
        });
      }

      // Generate reflection based on type
      let result;
      if (type === 'insights') {
        result = await generateInsights(userId, moment.sphereId, 'month', 50);
      } else if (type === 'patterns') {
        result = await detectPatterns(userId, moment.sphereId, 'month', 100);
      } else {
        result = await findConnections(userId, moment.sphereId, 'month', 50);
      }

      return {
        result,
        message: `Generated ${type} reflection successfully`,
      };

    } catch (error) {
      fastify.log.error('Reflection generation failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to generate reflection',
      });
    }
  });

  /**
   * Get search analytics
   * GET /api/search/analytics
   */
  fastify.get('/analytics', {
    onRequest: [requireAuth],
    schema: {
      querystring: zodToJsonSchema(z.object({
        period: z.enum(['day', 'week', 'month']).default('week'),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          totalSearches: z.number(),
          popularQueries: z.array(z.object({
            query: z.string(),
            count: z.number(),
          })),
          searchTypeDistribution: z.object({
            semantic: z.number(),
            text: z.number(),
            hybrid: z.number(),
            similar: z.number(),
          }),
          avgProcessingTime: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const { period } = request.query;
    const userId = (request.user as JwtPayload).userId;

    try {
      const periodMs = period === 'day' ? 24 * 60 * 60 * 1000 :
        period === 'week' ? 7 * 24 * 60 * 60 * 1000 :
          30 * 24 * 60 * 60 * 1000;

      const since = new Date(Date.now() - periodMs);

      const searchEvents = await prisma.auditEvent.findMany({
        where: {
          userId,
          eventType: 'SEMANTIC_SEARCH_PERFORMED',
          createdAt: { gte: since },
        },
        select: {
          details: true,
        },
      });

      // Analyze search patterns
      const queryCount = new Map<string, number>();
      const typeCount = { semantic: 0, text: 0, hybrid: 0, similar: 0 };
      let totalProcessingTime = 0;

      for (const event of searchEvents) {
        const details = event.details as any;

        // Count queries
        const query = details.query?.toLowerCase();
        if (query) {
          queryCount.set(query, (queryCount.get(query) || 0) + 1);
        }

        // Count types
        if (details.type && typeCount.hasOwnProperty(details.type)) {
          (typeCount as any)[details.type]++;
        }

        // Sum processing time
        if (details.processingTimeMs) {
          totalProcessingTime += details.processingTimeMs;
        }
      }

      const popularQueries = Array.from(queryCount.entries())
        .sort(([, a], [, b]) => b - a)
        .slice(0, 10)
        .map(([query, count]) => ({ query, count }));

      return {
        totalSearches: searchEvents.length,
        popularQueries,
        searchTypeDistribution: typeCount,
        avgProcessingTime: searchEvents.length > 0 ? totalProcessingTime / searchEvents.length : 0,
      };

    } catch (error) {
      fastify.log.error('Search analytics failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get search analytics',
      });
    }
  });
}
