/**
 * @fileoverview Graph structural integrity validation.
 *
 * P0-3: Enhanced validation for:
 * - Resolved edge targets must exist in node set
 * - Raw label edges must have corresponding node or be in unresolved set
 * - Resolution records must reference valid targets
 * - Source ranges must be valid (start <= end, non-negative)
 * - Unresolved lifecycle consistency
 */

import type { ArtifactGraph } from './types';

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
 * Validate an ArtifactGraph for structural integrity and lifecycle consistency.
 *
 * P0-3: Enhanced validation checks:
 * - Duplicate node IDs
 * - Edge source/target existence
 * - Confidence score validity
 * - Required field presence
 * - Index consistency
 * - Resolved edge targets exist in node set
 * - Raw label edges have corresponding node or are unresolved
 * - Resolution records reference valid targets
 * - Source ranges are valid (start <= end, non-negative)
 * - Unresolved edges appear in resolution records or remaining set
 */
export function validateGraph(graph: ArtifactGraph): GraphValidationResult {
  const errors: GraphValidationError[] = [];
  const warnings: GraphValidationError[] = [];

  const nodeIds = new Set<string>();
  const nodeIdSet = new Set<string>(graph.nodes.map((n) => n.id));

  // Check for duplicate node IDs
  for (const node of graph.nodes) {
    if (nodeIds.has(node.id)) {
      errors.push({
        code: 'DUPLICATE_NODE_ID',
        message: `Duplicate node ID: ${node.id}`,
        severity: 'error',
        context: { nodeId: node.id },
      });
    }
    nodeIds.add(node.id);
  }

  // Check edge source/target existence
  for (const edge of graph.edges) {
    if (!nodeIdSet.has(edge.sourceId)) {
      errors.push({
        code: 'EDGE_SOURCE_NOT_FOUND',
        message: `Edge source node not found: ${edge.sourceId}`,
        severity: 'error',
        context: { edgeId: edge.id },
      });
    }
    if (!nodeIdSet.has(edge.targetId)) {
      errors.push({
        code: 'EDGE_TARGET_NOT_FOUND',
        message: `Edge target node not found: ${edge.targetId}`,
        severity: 'error',
        context: { edgeId: edge.id },
      });
    }
  }

  // P0-3: Check confidence score validity
  for (const node of graph.nodes) {
    if (node.confidence !== undefined && (node.confidence < 0 || node.confidence > 1)) {
      errors.push({
        code: 'INVALID_CONFIDENCE',
        message: `Node confidence must be between 0 and 1: ${node.confidence}`,
        severity: 'error',
        context: { nodeId: node.id, value: node.confidence },
      });
    }
  }

  for (const edge of graph.edges) {
    if (edge.confidence !== undefined && (edge.confidence < 0 || edge.confidence > 1)) {
      errors.push({
        code: 'INVALID_CONFIDENCE',
        message: `Edge confidence must be between 0 and 1: ${edge.confidence}`,
        severity: 'error',
        context: { edgeId: edge.id, value: edge.confidence },
      });
    }
  }

  // Check required fields
  for (const node of graph.nodes) {
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

  // P0-3: Validate source ranges
  for (const node of graph.nodes) {
    const loc = node.sourceLocation;
    if (loc) {
      if (loc.startLine < 0 || loc.startColumn < 0 || loc.endLine < 0 || loc.endColumn < 0) {
        errors.push({
          code: 'INVALID_SOURCE_RANGE',
          message: `Source location has negative values: line/col must be >= 0`,
          severity: 'error',
          context: { nodeId: node.id, field: 'sourceLocation', value: loc },
        });
      }
      if (loc.startLine > loc.endLine || (loc.startLine === loc.endLine && loc.startColumn > loc.endColumn)) {
        errors.push({
          code: 'INVALID_SOURCE_RANGE',
          message: `Source location end must be after start: start(${loc.startLine}:${loc.startColumn}) > end(${loc.endLine}:${loc.endColumn})`,
          severity: 'error',
          context: { nodeId: node.id, field: 'sourceLocation', value: loc },
        });
      }
    }
  }

  // P0-3: Validate unresolved edge lifecycle consistency
  const unresolvedTargetRefs = new Set<string>(
    graph.unresolvedEdges.map((e) => e.targetRef),
  );

  // Raw label edges must have corresponding node or be in unresolved set
  for (const edge of graph.unresolvedEdges) {
    if (edge.targetRef.startsWith('label://')) {
      if (!nodeIdSet.has(edge.targetRef) && !unresolvedTargetRefs.has(edge.targetRef)) {
        warnings.push({
          code: 'RAW_LABEL_EDGE_WITHOUT_TARGET',
          message: `Raw label edge target ${edge.targetRef} has no corresponding node or unresolved edge`,
          severity: 'warning',
          context: { field: 'targetRef', value: edge.targetRef },
        });
      }
    }
  }

  // Every resolution record target must exist in node set
  for (const record of graph.edgeResolutionRecords) {
    if (record.resolvedTargetId && !nodeIdSet.has(record.resolvedTargetId)) {
      errors.push({
        code: 'RESOLUTION_RECORD_TARGET_MISSING',
        message: `Resolution record target ${record.resolvedTargetId} does not exist in node set`,
        severity: 'error',
        context: { field: 'resolvedTargetId', value: record.resolvedTargetId },
      });
    }
  }

  // Every unresolved edge should appear in resolution records
  const resolvedEdgeSourceIds = new Set(
    graph.edgeResolutionRecords.map((r) => r.unresolvedEdge.sourceId),
  );
  for (const edge of graph.unresolvedEdges) {
    if (!resolvedEdgeSourceIds.has(edge.sourceId)) {
      warnings.push({
        code: 'UNRESOLVED_EDGE_NOT_TRACKED',
        message: `Unresolved edge from ${edge.sourceId} does not appear in resolution records`,
        severity: 'warning',
        context: { field: 'sourceId', value: edge.sourceId },
      });
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}
