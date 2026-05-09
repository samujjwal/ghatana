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
import {
  LineageDagResponseSchema,
  LineageImpactResponseSchema,
  type LineageDagResponse,
  type LineageImpactResponse,
} from '../contracts/schemas';

export interface LineageNode {
  id: string;
  type: 'DATASET' | 'TRANSFORMATION' | 'QUERY' | 'DASHBOARD' | 'ML_MODEL';
  name: string;
  metadata: Record<string, unknown>;
}

export interface LineageEdge {
  source: string;
  target: string;
  type: 'DERIVES_FROM' | 'FEEDS_INTO' | 'TRANSFORMS';
  metadata?: Record<string, unknown>;
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
  // DC-32: Canonical execution status aligned with OperatorState
  status: 'CREATED' | 'INITIALIZED' | 'RUNNING' | 'STOPPED' | 'FAILED';
  duration: number;
  recordsProcessed?: number;
  error?: string;
}

/**
 * Lineage Service Client
 */
export class LineageService {
  private mapNodeType(type: string): LineageNode['type'] {
    switch (type.toUpperCase()) {
      case 'TRANSFORMATION':
        return 'TRANSFORMATION';
      case 'QUERY':
        return 'QUERY';
      case 'DASHBOARD':
        return 'DASHBOARD';
      case 'ML_MODEL':
        return 'ML_MODEL';
      default:
        return 'DATASET';
    }
  }

  /**
   * Get lineage graph for a dataset.
   *
   * Calls {@code GET /api/v1/lineage/:datasetId?direction=BOTH}.
   */
  async getLineage(
    datasetId: string,
    direction: 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH' = 'BOTH',
    _depth: number = 3
  ): Promise<LineageGraph> {
    const rawResponse = await apiClient.get<LineageDagResponse>(`/lineage/${datasetId}`, {
      params: { direction },
    });
    const data = LineageDagResponseSchema.parse(rawResponse);

    const nodes: LineageNode[] = data.dag.nodes.map((n) => ({
      id: n.id,
      type: this.mapNodeType(n.type),
      name: n.name,
      metadata: {
        ...n.metadata,
        role: n.role,
      },
    }));

    const edges: LineageEdge[] = data.dag.edges.map((e) => ({
      source: e.source,
      target: e.target,
      type: (e.type === 'DERIVES_FROM' ? 'DERIVES_FROM'
           : e.type === 'FEEDS_INTO'  ? 'FEEDS_INTO'
           : 'TRANSFORMS') as LineageEdge['type'],
    }));

    return { nodes, edges, rootNode: datasetId };
  }

  /**
   * Get impact analysis for a dataset.
   *
   * Calls {@code GET /api/v1/lineage/:datasetId/impact}.
   */
  async getImpactAnalysis(datasetId: string): Promise<ImpactAnalysis> {
    const rawResponse = await apiClient.get<LineageImpactResponse>(`/lineage/${datasetId}/impact`);
    const data = LineageImpactResponseSchema.parse(rawResponse);

    return {
      affectedDatasets: data.affectedCount,
      affectedDashboards: 0,
      affectedQueries: 0,
      affectedWorkflows: 0,
      details: data.affectedCollections.map((col) => ({
        id: col,
        type: 'DATASET',
        name: col,
        impact: data.impactLevel === 'LOW' ? 'INDIRECT' as const : 'DIRECT' as const,
        distance: 1,
      })),
    };
  }

  /**
   * Get time-travel lineage snapshot — not yet exposed by the launcher.
   */
  async getTimeTravelLineage(
    _datasetId: string,
    _timestamp: string
  ): Promise<TimeTravelSnapshot> {
    return Promise.reject(new Error(
      'Time-travel lineage is not yet exposed by the Data Cloud launcher.',
    ));
  }

  /**
   * Get execution logs for a dataset — not yet exposed by the launcher.
   */
  async getExecutionLogs(
    _datasetId: string,
    _limit: number = 50
  ): Promise<ExecutionLog[]> {
    return Promise.reject(new Error(
      'Lineage execution log APIs are not yet exposed by the Data Cloud launcher.',
    ));
  }

  /**
   * Search lineage by keyword — not yet exposed by the launcher.
   */
  async searchLineage(
    _query: string
  ): Promise<{ datasets: LineageNode[]; relationships: LineageEdge[] }> {
    return Promise.reject(new Error(
      'Lineage search APIs are not yet exposed by the Data Cloud launcher.',
    ));
  }

  /**
   * Get column-level lineage — not yet exposed by the launcher.
   */
  async getColumnLineage(
    _datasetId: string,
    _columnName: string
  ): Promise<LineageGraph> {
    return Promise.reject(new Error(
      'Column lineage APIs are not yet exposed by the Data Cloud launcher.',
    ));
  }
}

/**
 * Default lineage service instance
 */
export const lineageService = new LineageService();

export default lineageService;


