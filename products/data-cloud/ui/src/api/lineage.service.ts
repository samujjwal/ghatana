/**
 * Lineage & Metadata API Service
 *
 * Provides API client for data lineage, impact analysis, and time-travel.
 *
 * @doc.type service
 * @doc.purpose Lineage and metadata API client
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';

export interface LineageNode {
  id: string;
  type: 'DATASET' | 'TRANSFORMATION' | 'QUERY' | 'DASHBOARD' | 'ML_MODEL';
  name: string;
  metadata: Record<string, any>;
}

export interface LineageEdge {
  source: string;
  target: string;
  type: 'DERIVES_FROM' | 'FEEDS_INTO' | 'TRANSFORMS';
  metadata?: Record<string, any>;
}

export interface LineageGraph {
  nodes: LineageNode[];
  edges: LineageEdge[];
  rootNode: string;
}

export interface ImpactAnalysis {
  affectedDatasets: number;
  affectedDashboards: number;
  affectedQueries: number;
  affectedWorkflows: number;
  details: Array<{
    id: string;
    type: string;
    name: string;
    impact: 'DIRECT' | 'INDIRECT';
    distance: number;
  }>;
}

export interface TimeTravelSnapshot {
  timestamp: string;
  lineage: LineageGraph;
  changes: Array<{
    type: 'NODE_ADDED' | 'NODE_REMOVED' | 'EDGE_ADDED' | 'EDGE_REMOVED';
    nodeId?: string;
    edgeId?: string;
    description: string;
  }>;
}

export interface ExecutionLog {
  id: string;
  timestamp: string;
  workflowId?: string;
  datasetId: string;
  status: 'SUCCESS' | 'FAILED' | 'RUNNING';
  duration: number;
  recordsProcessed?: number;
  error?: string;
}

/**
 * Lineage Service Client
 */
export class LineageService {
  /**
   * Get lineage graph for a dataset
   */
  async getLineage(
    datasetId: string,
    direction: 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH' = 'BOTH',
    depth: number = 3
  ): Promise<LineageGraph> {
    return apiClient.get<LineageGraph>(`/lineage/${datasetId}`, {
      params: { direction, depth },
    });
  }

  /**
   * Get impact analysis for a dataset
   */
  async getImpactAnalysis(datasetId: string): Promise<ImpactAnalysis> {
    return apiClient.get<ImpactAnalysis>(`/lineage/${datasetId}/impact`);
  }

  /**
   * Get time-travel lineage snapshot
   */
  async getTimeTravelLineage(
    datasetId: string,
    timestamp: string
  ): Promise<TimeTravelSnapshot> {
    return apiClient.get<TimeTravelSnapshot>(`/lineage/${datasetId}`, {
      params: { timestamp },
    });
  }

  /**
   * Get execution logs for a dataset
   */
  async getExecutionLogs(
    datasetId: string,
    limit: number = 50
  ): Promise<ExecutionLog[]> {
    return apiClient.get<ExecutionLog[]>(`/lineage/${datasetId}/executions`, {
      params: { limit },
    });
  }

  /**
   * Search lineage by keyword
   */
  async searchLineage(
    query: string
  ): Promise<{ datasets: LineageNode[]; relationships: LineageEdge[] }> {
    return apiClient.get('/lineage/search', { params: { q: query } });
  }

  /**
   * Get column-level lineage
   */
  async getColumnLineage(
    datasetId: string,
    columnName: string
  ): Promise<LineageGraph> {
    return apiClient.get<LineageGraph>(`/lineage/${datasetId}/columns/${columnName}`);
  }
}

/**
 * Default lineage service instance
 */
export const lineageService = new LineageService();

export default lineageService;

