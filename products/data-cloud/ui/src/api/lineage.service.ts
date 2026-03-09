/**
 * Lineage & Metadata API Service
 *
 * Provides API client for data lineage, impact analysis, and time-travel.
 *
 * @doc.type service
 * @doc.purpose Lineage and metadata API client
 * @doc.layer frontend
 */

import axios, { AxiosInstance } from 'axios';

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
  private client: AxiosInstance;

  constructor(baseURL: string = '/api') {
    this.client = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * Get lineage graph for a dataset
   */
  async getLineage(
    datasetId: string,
    direction: 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH' = 'BOTH',
    depth: number = 3
  ): Promise<LineageGraph> {
    const response = await this.client.get<LineageGraph>(`/lineage/${datasetId}`, {
      params: { direction, depth },
    });
    return response.data;
  }

  /**
   * Get impact analysis for a dataset
   */
  async getImpactAnalysis(datasetId: string): Promise<ImpactAnalysis> {
    const response = await this.client.get<ImpactAnalysis>(
      `/lineage/${datasetId}/impact`
    );
    return response.data;
  }

  /**
   * Get time-travel lineage snapshot
   */
  async getTimeTravelLineage(
    datasetId: string,
    timestamp: string
  ): Promise<TimeTravelSnapshot> {
    const response = await this.client.get<TimeTravelSnapshot>(
      `/lineage/${datasetId}`,
      {
        params: { timestamp },
      }
    );
    return response.data;
  }

  /**
   * Get execution logs for a dataset
   */
  async getExecutionLogs(
    datasetId: string,
    limit: number = 50
  ): Promise<ExecutionLog[]> {
    const response = await this.client.get<ExecutionLog[]>(
      `/lineage/${datasetId}/executions`,
      {
        params: { limit },
      }
    );
    return response.data;
  }

  /**
   * Search lineage by keyword
   */
  async searchLineage(
    query: string
  ): Promise<{ datasets: LineageNode[]; relationships: LineageEdge[] }> {
    const response = await this.client.get('/lineage/search', {
      params: { q: query },
    });
    return response.data;
  }

  /**
   * Get column-level lineage
   */
  async getColumnLineage(
    datasetId: string,
    columnName: string
  ): Promise<LineageGraph> {
    const response = await this.client.get<LineageGraph>(
      `/lineage/${datasetId}/columns/${columnName}`
    );
    return response.data;
  }
}

/**
 * Default lineage service instance
 */
export const lineageService = new LineageService();

export default lineageService;

