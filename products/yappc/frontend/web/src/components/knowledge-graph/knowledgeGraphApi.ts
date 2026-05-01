/**
 * Copyright (c) 2025 Ghatana Technologies
 * Knowledge Graph API Client
 *
 * API client for knowledge graph operations.
 * Communicates with the backend KG service.
 *
 * @doc.type service
 * @doc.purpose Knowledge graph API client
 * @doc.layer infrastructure
 * @doc.pattern API Client
 */

import { ApiClient, createCorrelationMiddleware } from '@ghatana/api';

// API client instance
const apiClient = new ApiClient({
  baseUrl: process.env.YAPPC_API_URL || '/api',
  timeoutMs: 10000,
});
apiClient.useRequest(createCorrelationMiddleware());

// Types matching backend API
interface KnowledgeNode {
  id: string;
  type: 'PROJECT' | 'WORKFLOW' | 'ENTITY' | 'RELATIONSHIP' | 'INSIGHT';
  label: string;
  description?: string;
  confidence: number;
  metadata: Record<string, unknown>;
}

interface KnowledgeEdge {
  source: string;
  target: string;
  type: string;
  weight: number;
}

interface KnowledgeGraphResult {
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
  insights: string[];
}

interface SemanticSearchResult {
  query: string;
  results: Array<{
    node: KnowledgeNode;
    score: number;
    context: string;
  }>;
}

/**
 * Knowledge Graph API
 *
 * Provides methods for:
 * - Fetching project knowledge graphs
 * - Semantic search
 * - Entity exploration
 */
export const knowledgeGraphApi = {
  /**
   * Get the knowledge graph for a project
   */
  async getProjectGraph(projectId: string): Promise<KnowledgeGraphResult> {
    const response = await apiClient.get<KnowledgeGraphResult>(`/v1/knowledge-graph/projects/${projectId}`);

    if (!response.data) {
      throw new Error('Failed to fetch knowledge graph: empty response');
    }

    return response.data;
  },

  /**
   * Perform semantic search over project knowledge.
   * `projectId` is required — vector queries must always be tenant-scoped
   * to the project to prevent cross-tenant data leakage (C-Y8).
   */
  async semanticSearch(query: string, projectId: string): Promise<SemanticSearchResult> {
    const params = new URLSearchParams();
    params.append('q', query);
    params.append('projectId', projectId);

    const response = await apiClient.get<SemanticSearchResult>(`/v1/knowledge-graph/search?${params.toString()}`);

    if (!response.data) {
      return { query, results: [] };
    }

    return response.data;
  },

  /**
   * Get related entities for a given node.
   * `projectId` is required — must always be tenant-scoped (C-Y8).
   */
  async getRelatedEntities(nodeId: string, projectId: string): Promise<KnowledgeNode[]> {
    const params = new URLSearchParams();
    params.append('nodeId', nodeId);
    params.append('projectId', projectId);

    const response = await apiClient.get<KnowledgeNode[]>(`/v1/knowledge-graph/related?${params.toString()}`);

    return response.data || [];
  },

  /**
   * Get AI-generated insights for a project
   */
  async getInsights(projectId: string): Promise<string[]> {
    const response = await apiClient.get<{ insights: string[] }>(`/v1/knowledge-graph/projects/${projectId}/insights`);

    return response.data?.insights || [];
  },
};

export type { KnowledgeNode, KnowledgeEdge, KnowledgeGraphResult, SemanticSearchResult };
export { knowledgeGraphApi };
