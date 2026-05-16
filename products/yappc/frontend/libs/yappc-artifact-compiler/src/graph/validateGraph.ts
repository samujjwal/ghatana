/**
 * @fileoverview Graph validation utility.
 *
 * Validates ArtifactGraph structural integrity:
 * - No duplicate node IDs
 * - All edge source/target IDs reference existing nodes
 * - Confidence scores in valid range [0, 1]
 * - Required fields present
 * - Index consistency
 */

import type { ArtifactGraph } from './types';

export interface GraphValidationError {
  readonly code: string;
  readonly message: string;
  readonly severity: 'error' | 'warning';
  readonly context?: {
    nodeId?: string;
    edgeId?: string;
    field?: string;
    value?: unknown;
  };
}

export interface GraphValidationResult {
  readonly valid: boolean;
  readonly errors: readonly GraphValidationError[];
  readonly warnings: readonly GraphValidationError[];
}

/**
 * Validates an ArtifactGraph for structural integrity.
 * Returns validation errors and warnings.
 */
export function validateGraph(graph: ArtifactGraph): GraphValidationResult {
  const errors: GraphValidationError[] = [];
  const warnings: GraphValidationError[] = [];

  // Build node ID lookup for O(1) checks
  const nodeIds = new Set(graph.nodes.map((n) => n.id));
  const nodeIdSet = nodeIds;

  // Check for duplicate node IDs
  const duplicateNodeIds = graph.nodes
    .map((n) => n.id)
    .filter((id, index, arr) => arr.indexOf(id) !== index);
  for (const dupId of duplicateNodeIds) {
    errors.push({
      code: 'DUPLICATE_NODE_ID',
      message: `Duplicate node ID: ${dupId}`,
      severity: 'error',
      context: { nodeId: dupId },
    });
  }

  // Check all edge source/target IDs reference existing nodes
  for (const edge of graph.edges) {
    if (!nodeIdSet.has(edge.sourceId)) {
      errors.push({
        code: 'EDGE_SOURCE_NOT_FOUND',
        message: `Edge references non-existent source node: ${edge.sourceId}`,
        severity: 'error',
        context: { edgeId: edge.id, field: 'sourceId', value: edge.sourceId },
      });
    }
    if (!nodeIdSet.has(edge.targetId)) {
      errors.push({
        code: 'EDGE_TARGET_NOT_FOUND',
        message: `Edge references non-existent target node: ${edge.targetId}`,
        severity: 'error',
        context: { edgeId: edge.id, field: 'targetId', value: edge.targetId },
      });
    }
  }

  // Validate confidence scores in range [0, 1]
  for (const node of graph.nodes) {
    if (node.confidence < 0 || node.confidence > 1) {
      errors.push({
        code: 'INVALID_CONFIDENCE',
        message: `Node confidence must be between 0 and 1: ${node.confidence}`,
        severity: 'error',
        context: { nodeId: node.id, field: 'confidence', value: node.confidence },
      });
    }
  }
  for (const edge of graph.edges) {
    if (edge.confidence < 0 || edge.confidence > 1) {
      errors.push({
        code: 'INVALID_CONFIDENCE',
        message: `Edge confidence must be between 0 and 1: ${edge.confidence}`,
        severity: 'error',
        context: { edgeId: edge.id, field: 'confidence', value: edge.confidence },
      });
    }
  }

  // Validate required fields
  for (const node of graph.nodes) {
    if (!node.id || node.id.trim() === '') {
      errors.push({
        code: 'MISSING_REQUIRED_FIELD',
        message: 'Node missing required field: id',
        severity: 'error',
        context: { field: 'id' },
      });
    }
    if (!node.kind) {
      errors.push({
        code: 'MISSING_REQUIRED_FIELD',
        message: 'Node missing required field: kind',
        severity: 'error',
        context: { nodeId: node.id, field: 'kind' },
      });
    }
    if (!node.label || node.label.trim() === '') {
      errors.push({
        code: 'MISSING_REQUIRED_FIELD',
        message: 'Node missing required field: label',
        severity: 'error',
        context: { nodeId: node.id, field: 'label' },
      });
    }
  }
  for (const edge of graph.edges) {
    if (!edge.id || edge.id.trim() === '') {
      errors.push({
        code: 'MISSING_REQUIRED_FIELD',
        message: 'Edge missing required field: id',
        severity: 'error',
        context: { field: 'id' },
      });
    }
    if (!edge.sourceId || edge.sourceId.trim() === '') {
      errors.push({
        code: 'MISSING_REQUIRED_FIELD',
        message: 'Edge missing required field: sourceId',
        severity: 'error',
        context: { edgeId: edge.id, field: 'sourceId' },
      });
    }
    if (!edge.targetId || edge.targetId.trim() === '') {
      errors.push({
        code: 'MISSING_REQUIRED_FIELD',
        message: 'Edge missing required field: targetId',
        severity: 'error',
        context: { edgeId: edge.id, field: 'targetId' },
      });
    }
    if (!edge.kind) {
      errors.push({
        code: 'MISSING_REQUIRED_FIELD',
        message: 'Edge missing required field: kind',
        severity: 'error',
        context: { edgeId: edge.id, field: 'kind' },
      });
    }
  }

  // Check index consistency
  const indexedNodeIds = new Set<string>();
  for (const [kind, ids] of Object.entries(graph.nodeIndex)) {
    for (const id of ids) {
      if (!nodeIdSet.has(id)) {
        warnings.push({
          code: 'INDEX_NODE_NOT_FOUND',
          message: `Node index references non-existent node: ${id}`,
          severity: 'warning',
          context: { field: 'nodeIndex', value: { kind, id } },
        });
      }
      if (indexedNodeIds.has(id)) {
        warnings.push({
          code: 'DUPLICATE_INDEX_ENTRY',
          message: `Node appears multiple times in index: ${id}`,
          severity: 'warning',
          context: { nodeId: id, field: 'nodeIndex', value: { kind } },
        });
      }
      indexedNodeIds.add(id);
    }
  }

  const indexedEdgeIds = new Set<string>();
  for (const [kind, ids] of Object.entries(graph.edgeIndex)) {
    for (const id of ids) {
      const edgeExists = graph.edges.some((e) => e.id === id);
      if (!edgeExists) {
        warnings.push({
          code: 'INDEX_EDGE_NOT_FOUND',
          message: `Edge index references non-existent edge: ${id}`,
          severity: 'warning',
          context: { field: 'edgeIndex', value: { kind, id } },
        });
      }
      if (indexedEdgeIds.has(id)) {
        warnings.push({
          code: 'DUPLICATE_INDEX_ENTRY',
          message: `Edge appears multiple times in index: ${id}`,
          severity: 'warning',
          context: { edgeId: id, field: 'edgeIndex', value: { kind } },
        });
      }
      indexedEdgeIds.add(id);
    }
  }

  // Validate graph metadata
  if (!graph.id || graph.id.trim() === '') {
    errors.push({
      code: 'MISSING_REQUIRED_FIELD',
      message: 'Graph missing required field: id',
      severity: 'error',
      context: { field: 'id' },
    });
  }
  if (!graph.repositoryRoot || graph.repositoryRoot.trim() === '') {
    errors.push({
      code: 'MISSING_REQUIRED_FIELD',
      message: 'Graph missing required field: repositoryRoot',
      severity: 'error',
      context: { field: 'repositoryRoot' },
    });
  }
  if (!graph.createdAt || isNaN(Date.parse(graph.createdAt))) {
    errors.push({
      code: 'INVALID_DATETIME',
      message: 'Graph has invalid createdAt timestamp',
      severity: 'error',
      context: { field: 'createdAt', value: graph.createdAt },
    });
  }
  if (!graph.updatedAt || isNaN(Date.parse(graph.updatedAt))) {
    errors.push({
      code: 'INVALID_DATETIME',
      message: 'Graph has invalid updatedAt timestamp',
      severity: 'error',
      context: { field: 'updatedAt', value: graph.updatedAt },
    });
  }
  if (graph.version < 0) {
    errors.push({
      code: 'INVALID_VERSION',
      message: 'Graph version must be non-negative',
      severity: 'error',
      context: { field: 'version', value: graph.version },
    });
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}
