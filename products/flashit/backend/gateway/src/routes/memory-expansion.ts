/**
 * Memory Expansion API Routes
 * Phase 1 Week 12: User-initiated memory expansion
 *
 * @doc.type api-routes
 * @doc.purpose API endpoints for memory expansion requests and results
 * @doc.layer product
 * @doc.pattern REST API
 */

import { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { generateInsights, detectPatterns, findConnections } from '../services/java-agents/reflection-client.js';

// Validation schemas
const ExpansionRequestSchema = z.object({
  sphereId: z.string().uuid().optional(),
  momentIds: z.array(z.string().uuid()).optional(),
  expansionType: z.enum(['summarize', 'extract_themes', 'identify_patterns', 'find_connections']),
  timeRange: z
    .object({
      startDate: z.string().datetime(),
      endDate: z.string().datetime(),
    })
    .optional(),
  priority: z.enum(['high', 'normal', 'low']).default('normal'),
});

const ExpansionQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(10),
});

/**
 * Memory Expansion Routes Plugin
 */
const memoryExpansionRoutes: FastifyPluginAsync = async (app) => {
  // Type assertion for auth decorator
  type AppWithAuth = typeof app & {
    authenticate: (request: any, reply: any) => Promise<void>;
  };
  const authApp = app as AppWithAuth;

  /**
   * POST /api/memory-expansion
   * Request a new memory expansion analysis
   */
  app.post(
    '/api/memory-expansion',
    {
      onRequest: [authApp.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      const body = request.body as z.infer<typeof ExpansionRequestSchema>;

      try {
        // Validate that at least one selection method is provided
        if (!body.momentIds && !body.sphereId && !body.timeRange) {
          return reply.status(400).send({
            error: 'Must provide momentIds, sphereId, or timeRange',
          });
        }

        // Determine time range for reflection
        let timeRangeOption: 'week' | 'month' | 'year' | 'all' = 'month';
        if (body.timeRange) {
          const days = Math.ceil(
            (new Date(body.timeRange.endDate).getTime() - new Date(body.timeRange.startDate).getTime()) / (1000 * 60 * 60 * 24)
          );
          if (days <= 7) timeRangeOption = 'week';
          else if (days <= 30) timeRangeOption = 'month';
          else if (days <= 365) timeRangeOption = 'year';
          else timeRangeOption = 'all';
        }

        // Generate reflection based on expansion type
        let result;
        switch (body.expansionType) {
          case 'summarize':
          case 'extract_themes':
            result = await generateInsights(userId, body.sphereId, timeRangeOption);
            break;
          case 'identify_patterns':
            result = await detectPatterns(userId, body.sphereId, timeRangeOption);
            break;
          case 'find_connections':
            result = await findConnections(userId, body.sphereId, timeRangeOption);
            break;
          default:
            result = await generateInsights(userId, body.sphereId, timeRangeOption);
        }

        return reply.status(200).send({
          result,
          message: 'Memory expansion completed',
        });
      } catch (error) {
        request.log.error({ error }, 'Failed to request memory expansion');
        return reply.status(500).send({
          error: 'Failed to request memory expansion',
        });
      }
    }
  );

  /**
   * GET /api/memory-expansion/:jobId
   * Get the status and result of a memory expansion job
   */
  app.get(
    '/api/memory-expansion/:jobId',
    {
      onRequest: [authApp.authenticate],
    },
    async (request, reply) => {
      const { jobId } = request.params as { jobId: string };

      try {
        const result = await memoryExpansionService.getExpansionResult(jobId);
        return reply.status(200).send(result);
      } catch (error) {
        request.log.error({ error, jobId }, 'Failed to get expansion result');
        return reply.status(500).send({
          error: 'Failed to get expansion result',
        });
      }
    }
  );

  /**
   * GET /api/memory-expansion
   * Get user's recent memory expansions
   */
  app.get(
    '/api/memory-expansion',
    {
      onRequest: [authApp.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      const query = request.query as z.infer<typeof ExpansionQuerySchema>;

      try {
        const expansions = await memoryExpansionService.getUserExpansions(userId, query.limit);
        return reply.status(200).send({ expansions });
      } catch (error) {
        request.log.error({ error }, 'Failed to get user expansions');
        return reply.status(500).send({
          error: 'Failed to get user expansions',
        });
      }
    }
  );

  /**
   * GET /api/memory-expansion/result/:expansionId
   * Get a specific expansion result by ID
   */
  app.get(
    '/api/memory-expansion/result/:expansionId',
    {
      onRequest: [authApp.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      const { expansionId } = request.params as { expansionId: string };

      try {
        const expansion = await memoryExpansionService.getExpansionById(expansionId, userId);

        if (!expansion) {
          return reply.status(404).send({
            error: 'Expansion not found',
          });
        }

        return reply.status(200).send(expansion);
      } catch (error) {
        request.log.error({ error, expansionId }, 'Failed to get expansion');
        return reply.status(500).send({
          error: 'Failed to get expansion',
        });
      }
    }
  );

  /**
   * POST /api/memory-expansion/batch
   * Request multiple expansions at once
   */
  app.post(
    '/api/memory-expansion/batch',
    {
      onRequest: [authApp.authenticate],
    },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      const { requests } = request.body as { requests: z.infer<typeof ExpansionRequestSchema>[] };

      try {
        const jobIds = await Promise.all(
          requests.map(async (req) => {
            const timeRange = req.timeRange
              ? {
                  startDate: new Date(req.timeRange.startDate),
                  endDate: new Date(req.timeRange.endDate),
                }
              : undefined;

            return await memoryExpansionService.requestExpansion({
              userId,
              sphereId: req.sphereId,
              momentIds: req.momentIds,
              expansionType: req.expansionType,
              timeRange,
              priority: req.priority,
            });
          })
        );

        return reply.status(200).send({
          jobIds,
          message: `${jobIds.length} memory expansion requests submitted`,
        });
      } catch (error) {
        request.log.error({ error }, 'Failed to request batch expansions');
        return reply.status(500).send({
          error: 'Failed to request batch expansions',
        });
      }
    }
  );

  /**
   * Log audit event for expansion view
   */
  app.addHook('onResponse', async (request) => {
    if (request.url.includes('/memory-expansion/result/')) {
      const userId = (request.user as any)?.userId;
      if (userId && request.statusCode === 200) {
        // Async fire-and-forget audit log
        setImmediate(() => {
          const { prisma } = require('../lib/prisma.js');
          prisma.auditEvent
            .create({
              data: {
                userId,
                eventType: 'MEMORY_EXPANSION_VIEWED',
                resourceType: 'EXPANSION_RESULT',
                resourceId: request.url.split('/').pop() || '',
                metadata: {},
              },
            })
            .catch((err: Error) => {
              request.log.error({ err }, 'Failed to log audit event');
            });
        });
      }
    }
  });
};

export default memoryExpansionRoutes;
