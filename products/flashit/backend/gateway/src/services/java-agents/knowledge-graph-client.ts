/**
 * Knowledge Graph Client
 *
 * Provides high-level interface for knowledge graph operations
 * through the Java Agent Service.
 *
 * @doc.type service
 * @doc.purpose Knowledge graph extraction and query client
 * @doc.layer infrastructure
 * @doc.pattern Client
 */

import { getJavaAgentClient, isJavaAgentServiceAvailable } from './agent-client.js';
import type {
  KnowledgeGraphRequest,
  KnowledgeGraphResponse,
} from './agent-client.js';
import { prisma } from '../../lib/prisma.js';
import { fetchMoments } from './moment-helpers.js';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const logger = {
  warn: (msg: string, ...args: unknown[]) => console.warn(`[knowledge-graph-client] ${msg}`, ...args),
  error: (msg: string, ...args: unknown[]) => console.error(`[knowledge-graph-client] ${msg}`, ...args),
};

/**
 * Extract knowledge graph from a user's recent moments.
 *
 * @param userId - User whose moments to analyze
 * @param sphereId - Optional sphere filter
 * @param limit - Maximum moments to analyze
 * @returns Extracted graph with nodes and edges
 */
export async function extractKnowledgeGraph(
  userId: string,
  sphereId?: string,
  limit: number = 100
): Promise<KnowledgeGraphResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();

  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot extract knowledge graph.');
  }

  const moments = await fetchMoments(userId, { sphereId, limit });

  if (moments.length === 0) {
    return {
      nodes: [],
      edges: [],
      totalNodes: 0,
      totalEdges: 0,
      processingTimeMs: 0,
      model: 'none',
    };
  }

  const request: KnowledgeGraphRequest = {
    userId,
    moments,
    operation: 'extract',
    depth: 0,
    limit: 0,
  };

  const client = getJavaAgentClient();
  const response = await client.extractGraph(request);

  // Persist extracted nodes and edges
  if (response.nodes.length > 0) {
    await persistGraphNodes(userId, response);
  }

  return response;
}

/**
 * Query the knowledge graph starting from a concept.
 *
 * @param userId - User whose graph to query
 * @param queryNode - Starting concept/topic name
 * @param depth - Traversal depth (1-3)
 * @param limit - Maximum nodes to return
 * @returns Subgraph reachable from the query node
 */
export async function queryKnowledgeGraph(
  userId: string,
  queryNode: string,
  depth: number = 2,
  limit: number = 20
): Promise<KnowledgeGraphResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();

  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot query knowledge graph.');
  }

  // Provide moment context for the query
  const moments = await fetchMoments(userId, { limit: 50 });

  const request: KnowledgeGraphRequest = {
    userId,
    moments,
    operation: 'query',
    queryNode,
    depth: Math.min(depth, 3),
    limit,
  };

  const client = getJavaAgentClient();
  return client.queryGraph(request);
}

/**
 * Expand the graph by discovering new connections.
 *
 * @param userId - User whose graph to expand
 * @returns Newly discovered nodes and edges
 */
export async function expandKnowledgeGraph(
  userId: string
): Promise<KnowledgeGraphResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();

  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot expand knowledge graph.');
  }

  const moments = await fetchMoments(userId, { limit: 100 });

  const request: KnowledgeGraphRequest = {
    userId,
    moments,
    operation: 'expand',
    depth: 0,
    limit: 0,
  };

  const client = getJavaAgentClient();
  const response = await client.expandGraph(request);

  if (response.nodes.length > 0) {
    await persistGraphNodes(userId, response);
  }

  return response;
}

/**
 * Get the persisted knowledge graph for a user.
 */
export async function getPersistedGraph(
  userId: string,
  limit: number = 50
): Promise<{ topics: unknown[]; entities: unknown[]; edges: unknown[] }> {
  const [topics, entities, edges] = await Promise.all([
    prisma.topicNode.findMany({
      where: { userId },
      orderBy: { momentCount: 'desc' },
      take: limit,
    }),
    prisma.entityNode.findMany({
      where: { userId },
      orderBy: { mentionCount: 'desc' },
      take: limit,
    }),
    prisma.graphEdge.findMany({
      where: { userId },
      orderBy: { weight: 'desc' },
      take: limit * 2,
    }),
  ]);

  return { topics, entities, edges };
}

// =========================================================================
// Internal Helpers
// =========================================================================

async function persistGraphNodes(
  userId: string,
  response: KnowledgeGraphResponse
): Promise<void> {
  // Upsert topic and entity nodes
  for (const node of response.nodes) {
    const normalizedName = node.name.toLowerCase().trim();

    if (node.nodeType === 'topic') {
      await prisma.topicNode.upsert({
        where: {
          userId_normalizedName: { userId, normalizedName },
        },
        update: {
          momentCount: { increment: node.momentCount },
          lastSeenAt: new Date(),
        },
        create: {
          userId,
          name: node.name,
          normalizedName,
          momentCount: node.momentCount,
          metadata: { weight: node.weight },
        },
      });
    } else if (node.nodeType === 'entity') {
      await prisma.entityNode.upsert({
        where: {
          userId_normalizedName_entityType: {
            userId,
            normalizedName,
            entityType: node.entityType || 'concept',
          },
        },
        update: {
          mentionCount: { increment: node.momentCount },
          lastSeenAt: new Date(),
        },
        create: {
          userId,
          name: node.name,
          normalizedName,
          entityType: node.entityType || 'concept',
          mentionCount: node.momentCount,
          metadata: { weight: node.weight },
        },
      });
    }
  }

  // Persist edges (upsert by composite key) — batch in a transaction
  const validEdges = response.edges.filter((edge) => {
    // C3 FIX: Validate UUIDs before attempting DB writes
    if (!UUID_REGEX.test(edge.sourceId) || !UUID_REGEX.test(edge.targetId)) {
      logger.warn(
        'Skipping edge with invalid UUID: sourceId=%s, targetId=%s, edgeType=%s',
        edge.sourceId,
        edge.targetId,
        edge.edgeType
      );
      return false;
    }
    return true;
  });

  if (validEdges.length > 0) {
    await prisma.$transaction(
      validEdges.map((edge) =>
        prisma.graphEdge.upsert({
          where: {
            userId_sourceType_sourceId_targetType_targetId_edgeType: {
              userId,
              sourceType: edge.sourceType,
              sourceId: edge.sourceId,
              targetType: edge.targetType,
              targetId: edge.targetId,
              edgeType: edge.edgeType,
            },
          },
          update: {
            weight: edge.weight,
            occurrences: { increment: edge.occurrences },
          },
          create: {
            userId,
            sourceType: edge.sourceType,
            sourceId: edge.sourceId,
            targetType: edge.targetType,
            targetId: edge.targetId,
            edgeType: edge.edgeType,
            weight: edge.weight,
            occurrences: edge.occurrences,
          },
        })
      )
    );
  }
}
