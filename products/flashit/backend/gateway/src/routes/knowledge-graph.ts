/**
 * Knowledge Graph API routes
 *
 * @description Endpoints for extracting, querying, and expanding the per-user
 * knowledge graph. The knowledge graph maps topics, entities, and relationships
 * discovered across a user's moments.
 *
 * @example
 * ```typescript
 * // Extract graph from recent moments
 * POST /api/knowledge-graph/extract
 * { "sphereId": "uuid", "limit": 100 }
 *
 * // Query graph from a concept
 * POST /api/knowledge-graph/query
 * { "queryNode": "machine learning", "depth": 2, "limit": 20 }
 *
 * // Expand graph with new connections
 * POST /api/knowledge-graph/expand
 *
 * // Get persisted graph
 * GET /api/knowledge-graph?limit=50
 * ```
 *
 * @doc.type module
 * @doc.purpose Knowledge Graph REST API
 * @doc.layer product
 * @doc.pattern Route
 */

import { FastifyInstance } from 'fastify';
import { z, ZodError } from 'zod';
import { getUserIdFromRequest } from '../lib/auth.js';
import {
  extractKnowledgeGraph,
  queryKnowledgeGraph,
  expandKnowledgeGraph,
  getPersistedGraph,
} from '../services/java-agents/knowledge-graph-client.js';

const extractSchema = z.object({
  sphereId: z.string().uuid().optional(),
  limit: z.number().int().min(1).max(200).default(100),
});

const querySchema = z.object({
  queryNode: z.string().min(1).max(200),
  depth: z.number().int().min(1).max(3).default(2),
  limit: z.number().int().min(1).max(50).default(20),
});

export default async function knowledgeGraphRoutes(fastify: FastifyInstance) {
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
   * POST /api/knowledge-graph/extract
   * Extract knowledge graph from moments
   */
  fastify.post('/extract', {
    preHandler: [fastify.authenticate],
    handler: async (request, reply) => {
      const userId = getUserIdFromRequest(request);
      const body = extractSchema.parse(request.body);

      const result = await extractKnowledgeGraph(userId, body.sphereId, body.limit);

      return reply.status(200).send({
        nodes: result.nodes,
        edges: result.edges,
        totalNodes: result.totalNodes,
        totalEdges: result.totalEdges,
        processingTimeMs: result.processingTimeMs,
      });
    },
  });

  /**
   * POST /api/knowledge-graph/query
   * Query graph starting from a concept
   */
  fastify.post('/query', {
    preHandler: [fastify.authenticate],
    handler: async (request, reply) => {
      const userId = getUserIdFromRequest(request);
      const body = querySchema.parse(request.body);

      const result = await queryKnowledgeGraph(
        userId,
        body.queryNode,
        body.depth,
        body.limit
      );

      return reply.status(200).send({
        nodes: result.nodes,
        edges: result.edges,
        totalNodes: result.totalNodes,
        totalEdges: result.totalEdges,
        processingTimeMs: result.processingTimeMs,
      });
    },
  });

  /**
   * POST /api/knowledge-graph/expand
   * Discover new connections in the graph
   */
  fastify.post('/expand', {
    preHandler: [fastify.authenticate],
    handler: async (request, reply) => {
      const userId = getUserIdFromRequest(request);

      const result = await expandKnowledgeGraph(userId);

      return reply.status(200).send({
        nodes: result.nodes,
        edges: result.edges,
        totalNodes: result.totalNodes,
        totalEdges: result.totalEdges,
        processingTimeMs: result.processingTimeMs,
      });
    },
  });

  /**
   * GET /api/knowledge-graph
   * Get the persisted knowledge graph
   */
  fastify.get('/', {
    preHandler: [fastify.authenticate],
    handler: async (request, reply) => {
      const userId = getUserIdFromRequest(request);
      const query = z
        .object({ limit: z.coerce.number().int().min(1).max(100).default(50) })
        .parse(request.query);

      const graph = await getPersistedGraph(userId, query.limit);

      return reply.status(200).send(graph);
    },
  });
}
