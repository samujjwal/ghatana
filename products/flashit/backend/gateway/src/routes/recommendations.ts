/**
 * Recommendations API routes
 *
 * @description Endpoints for generating, retrieving, and managing AI-powered
 * personalized recommendations. Recommendations suggest moments to revisit,
 * connections to explore, habits to build, and wellbeing actions.
 *
 * @example
 * ```typescript
 * // Generate recommendations
 * POST /api/recommendations/generate
 * { "strategies": ["revisit", "connect"], "limit": 5 }
 *
 * // Get active recommendations
 * GET /api/recommendations?limit=10
 *
 * // Submit feedback
 * POST /api/recommendations/:id/feedback
 * { "action": "helpful", "rating": 4 }
 * ```
 *
 * @doc.type module
 * @doc.purpose Recommendation Engine REST API
 * @doc.layer product
 * @doc.pattern Route
 */

import { FastifyInstance } from 'fastify';
import { z, ZodError } from 'zod';
import { getUserIdFromRequest } from '../lib/auth.js';
import {
  generateRecommendations,
  getActiveRecommendations,
  submitRecommendationFeedback,
} from '../services/java-agents/recommendation-client.js';

const generateSchema = z.object({
  strategies: z
    .array(z.enum(['revisit', 'connect', 'habit', 'wellbeing', 'explore']))
    .optional(),
  limit: z.number().int().min(1).max(20).default(10),
  sphereIds: z.array(z.string().uuid()).optional(),
});

const feedbackSchema = z.object({
  action: z.enum(['clicked', 'dismissed', 'helpful', 'not_helpful']),
  rating: z.number().int().min(1).max(5).optional(),
  comment: z.string().max(500).optional(),
});

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function recommendationRoutes(fastify: FastifyInstance) {
  // M8 FIX: Global Zod error handler for this scope
  fastify.setErrorHandler((error, _request, reply) => {
    if (error instanceof ZodError) {
      return reply.status(400).send({
        error: 'Validation Error',
        issues: error.issues.map((i) => ({ path: i.path.join('.'), message: i.message })),
      });
    }
    throw error; // re-throw non-Zod errors to default handler
  });

  /**
   * POST /api/recommendations/generate
   * Generate personalized recommendations
   */
  fastify.post('/generate', {
    preHandler: [fastify.authenticate],
    handler: async (request, reply) => {
      const userId = getUserIdFromRequest(request);
      const body = generateSchema.parse(request.body);

      const result = await generateRecommendations(
        userId,
        body.strategies,
        body.limit,
        body.sphereIds
      );

      return reply.status(201).send({
        recommendations: result.recommendations,
        total: result.totalGenerated,
        strategies: result.strategies,
        processingTimeMs: result.processingTimeMs,
      });
    },
  });

  /**
   * GET /api/recommendations
   * Get active recommendations for the current user
   */
  fastify.get('/', {
    preHandler: [fastify.authenticate],
    handler: async (request, reply) => {
      const userId = getUserIdFromRequest(request);
      const query = z
        .object({ limit: z.coerce.number().int().min(1).max(50).default(10) })
        .parse(request.query);

      const recommendations = await getActiveRecommendations(userId, query.limit);

      return reply.status(200).send({
        recommendations,
        total: recommendations.length,
      });
    },
  });

  /**
   * POST /api/recommendations/:id/feedback
   * Submit feedback on a recommendation
   */
  fastify.post('/:id/feedback', {
    preHandler: [fastify.authenticate],
    handler: async (request, reply) => {
      const userId = getUserIdFromRequest(request);
      const { id } = request.params as { id: string };

      // M9 FIX: Validate UUID format before DB lookup
      if (!UUID_REGEX.test(id)) {
        return reply.status(400).send({ error: 'Invalid recommendation ID format' });
      }

      const body = feedbackSchema.parse(request.body);

      await submitRecommendationFeedback(
        id,
        userId,
        body.action,
        body.rating,
        body.comment
      );

      return reply.status(200).send({ success: true });
    },
  });
}
