/**
 * Validation utilities for diagram
 * 
 * @doc.type utils
 * @doc.purpose Validate diagram structure and connections
 * @doc.layer shared
 */

import type { Node, Edge } from '@xyflow/react';
import type { ValidationError } from './types';

/**
 * Validate diagram structure
 */
export function validateDiagram(nodes: Node[], edges: Edge[]): ValidationError[] {
  const errors: ValidationError[] = [];

  // Check for disconnected nodes
  const connectedNodeIds = new Set<string>();
  edges.forEach((edge) => {
    connectedNodeIds.add(edge.source);
    connectedNodeIds.add(edge.target);
  });

  nodes.forEach((node) => {
    if (!connectedNodeIds.has(node.id) && nodes.length > 1) {
      errors.push({
        id: `disconnected-${node.id}`,
        type: 'node',
        message: `Node "${node.data?.label || node.id}" is not connected`,
        severity: 'warning',
      });
    }
  });

  // Check for cycles
  const hasCycle = detectCycles(nodes, edges);
  if (hasCycle) {
    errors.push({
      id: 'cycle-detected',
      type: 'diagram',
      message: 'Diagram contains circular dependencies',
      severity: 'error',
    });
  }

  // Check for required fields
  nodes.forEach((node) => {
    const data = node.data || {};
    if (!data.label || (data.label as string).trim() === '') {
      errors.push({
        id: `missing-label-${node.id}`,
        type: 'node',
        message: `Node is missing a name`,
        severity: 'error',
      });
    }
  });

  return errors;
}

/**
 * Detect cycles in directed graph using DFS
 */
function detectCycles(nodes: Node[], edges: Edge[]): boolean {
  const adjacencyList = new Map<string, string[]>();
  
  // Build adjacency list
  nodes.forEach((node) => adjacencyList.set(node.id, []));
  edges.forEach((edge) => {
    const targets = adjacencyList.get(edge.source) || [];
    targets.push(edge.target);
    adjacencyList.set(edge.source, targets);
  });

  const visited = new Set<string>();
  const recursionStack = new Set<string>();

  function dfs(nodeId: string): boolean {
    visited.add(nodeId);
    recursionStack.add(nodeId);

    const neighbors = adjacencyList.get(nodeId) || [];
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor)) {
        if (dfs(neighbor)) return true;
      } else if (recursionStack.has(neighbor)) {
        return true; // Cycle detected
      }
    }

    recursionStack.delete(nodeId);
    return false;
  }

  for (const node of nodes) {
    if (!visited.has(node.id)) {
      if (dfs(node.id)) return true;
    }
  }

  return false;
}

/**
 * Validate pattern-specific rules
 */
export function validatePattern(nodes: Node[], edges: Edge[]): ValidationError[] {
  const errors = validateDiagram(nodes, edges);

  // Pattern should have at least one event type
  const hasEventType = nodes.some((node) => node.type === 'eventType');
  if (!hasEventType) {
    errors.push({
      id: 'no-event-type',
      type: 'diagram',
      message: 'Pattern must have at least one Event Type',
      severity: 'error',
    });
  }

  // Pattern should have at least one action
  const hasAction = nodes.some((node) => node.type === 'action');
  if (!hasAction) {
    errors.push({
      id: 'no-action',
      type: 'diagram',
      message: 'Pattern must have at least one Action',
      severity: 'warning',
    });
  }

  return errors;
}

/**
 * Validate pipeline-specific rules
 */
export function validatePipeline(nodes: Node[], edges: Edge[]): ValidationError[] {
  const errors = validateDiagram(nodes, edges);

  // Pipeline should have at least one source
  const hasSource = nodes.some((node) => node.type === 'source');
  if (!hasSource) {
    errors.push({
      id: 'no-source',
      type: 'diagram',
      message: 'Pipeline must have at least one Source',
      severity: 'error',
    });
  }

  // Pipeline should have at least one sink
  const hasSink = nodes.some((node) => node.type === 'sink');
  if (!hasSink) {
    errors.push({
      id: 'no-sink',
      type: 'diagram',
      message: 'Pipeline must have at least one Sink',
      severity: 'error',
    });
  }

  // Check if sources are connected to sinks
  const sourceNodes = nodes.filter((node) => node.type === 'source');
  const sinkNodes = nodes.filter((node) => node.type === 'sink');

  if (sourceNodes.length > 0 && sinkNodes.length > 0) {
    const pathExists = sourceNodes.some((source) =>
      sinkNodes.some((sink) => hasPath(source.id, sink.id, edges))
    );

    if (!pathExists) {
      errors.push({
        id: 'no-source-to-sink-path',
        type: 'diagram',
        message: 'No path found from Source to Sink',
        severity: 'error',
      });
    }
  }

  return errors;
}

/**
 * Check if there's a path from source to target using BFS
 */
function hasPath(sourceId: string, targetId: string, edges: Edge[]): boolean {
  if (sourceId === targetId) return true;

  const adjacencyList = new Map<string, string[]>();
  edges.forEach((edge) => {
    const targets = adjacencyList.get(edge.source) || [];
    targets.push(edge.target);
    adjacencyList.set(edge.source, targets);
  });

  const queue = [sourceId];
  const visited = new Set<string>([sourceId]);

  while (queue.length > 0) {
    const current = queue.shift()!;
    const neighbors = adjacencyList.get(current) || [];

    for (const neighbor of neighbors) {
      if (neighbor === targetId) return true;
      if (!visited.has(neighbor)) {
        visited.add(neighbor);
        queue.push(neighbor);
      }
    }
  }

  return false;
}
