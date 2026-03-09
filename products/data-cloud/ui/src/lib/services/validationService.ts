import type {
  WorkflowDefinition,
  WorkflowNode,
  WorkflowEdge,
  WorkflowTrigger,
  ValidationResult,
  ValidationError,
  ValidationWarning,
  NodeTypeValue,
} from '@/types/workflow.types';

/**
 * Workflow validation service.
 *
 * <p><b>Purpose</b><br>
 * Validates workflow definitions for structural and semantic correctness.
 * Provides detailed error messages and auto-fix suggestions.
 *
 * <p><b>Features</b><br>
 * - Structural validation (nodes, edges, connections)
 * - Semantic validation (node configurations, edge conditions)
 * - Auto-fix suggestions for common issues
 * - Error categorization and severity levels
 * - Performance validation (cycle detection, etc.)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { validateWorkflow } from '@/lib/services/validationService';
 *
 * const result = validateWorkflow(workflow);
 * if (!result.valid) {
 *   console.error('Validation errors:', result.errors);
 * }
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Workflow validation logic
 * @doc.layer frontend
 */

/**
 * Validates a workflow definition.
 *
 * @param workflow the workflow to validate
 * @returns validation result with errors and warnings
 */
export function validateWorkflow(workflow: WorkflowDefinition): ValidationResult {
  const errors: ValidationError[] = [];
  const warnings: ValidationWarning[] = [];

  // Validate basic structure
  validateBasicStructure(workflow, errors);

  // Validate nodes
  validateNodes(workflow.nodes, errors, warnings);

  // Validate edges
  validateEdges(workflow.nodes, workflow.edges, errors, warnings);

  // Validate connections
  validateConnections(workflow.nodes, workflow.edges, errors, warnings);

  // Validate triggers — use the WorkflowTrigger type from types to keep strict typing
  validateTriggers(workflow.triggers as Array<string | WorkflowTrigger>, errors, warnings);

  // Detect cycles
  detectCycles(workflow.nodes, workflow.edges, errors);

  const result = {
    isValid: errors.length === 0,
    valid: errors.length === 0,
    errors,
    warnings,
  };

  // Debug: print validation result when running tests to diagnose unexpected warnings
   
  console.debug('validateWorkflow result:', JSON.stringify(result, null, 2));

  return result;
}

/**
 * Validates basic workflow structure.
 *
 * @param workflow the workflow
 * @param errors error list to populate
 */
function validateBasicStructure(workflow: WorkflowDefinition, errors: ValidationError[]): void {
  if (!workflow.id) {
    errors.push({
      code: 'MISSING_ID',
      message: 'Workflow must have an ID',
      suggestion: 'Ensure workflow has a unique identifier',
    });
  }

  if (!workflow.name || workflow.name.trim().length === 0) {
    errors.push({
      code: 'MISSING_NAME',
      message: 'Workflow must have a name',
      suggestion: 'Provide a descriptive name for the workflow',
    });
  }

  if (!workflow.collectionId) {
    errors.push({
      code: 'MISSING_COLLECTION',
      message: 'Workflow must be associated with a collection',
      suggestion: 'Select a collection for this workflow',
    });
  }

  if (!Array.isArray(workflow.nodes) || workflow.nodes.length === 0) {
    errors.push({
      code: 'NO_NODES',
      message: 'Workflow must have at least one node',
      suggestion: 'Add nodes to the workflow canvas',
    });
  }
}

/**
 * Validates workflow nodes.
 *
 * @param nodes the nodes to validate
 * @param errors error list to populate
 * @param warnings warning list to populate
 */
function validateNodes(
  nodes: WorkflowNode[],
  errors: ValidationError[],
  warnings: ValidationWarning[]
): void {
  const nodeIds = new Set<string>();

  for (const node of nodes) {
    // Check for duplicate IDs
    if (nodeIds.has(node.id)) {
      errors.push({
        code: 'DUPLICATE_NODE_ID',
        message: `Duplicate node ID: ${node.id}`,
        nodeId: node.id,
        suggestion: 'Ensure each node has a unique identifier',
      });
    }
    nodeIds.add(node.id);

    // Check required fields
    if (!node.id) {
      errors.push({
        code: 'MISSING_NODE_ID',
        message: 'Node must have an ID',
        suggestion: 'Ensure node has a unique identifier',
      });
    }

    if (!node.type) {
      errors.push({
        code: 'MISSING_NODE_TYPE',
        message: 'Node must have a type',
        nodeId: node.id,
        suggestion: 'Select a node type from the palette',
      });
    }

    const nodeLabel = node.label ?? node.data?.label ?? '';
    if (!nodeLabel || nodeLabel.trim().length === 0) {
      warnings.push({
        code: 'MISSING_NODE_LABEL',
        message: 'Node should have a label',
        nodeId: node.id,
        suggestion: 'Add a descriptive label to the node',
      });
    }

    // Validate node-specific configurations
    validateNodeConfig(node, errors, warnings);
  }
}

/**
 * Validates node-specific configuration.
 *
 * @param node the node to validate
 * @param errors error list to populate
 * @param warnings warning list to populate
 */
function normalizeNodeType(type?: NodeTypeValue | null): string {
  return type?.toString().toUpperCase() ?? '';
}

function validateNodeConfig(node: WorkflowNode, errors: ValidationError[], _warnings: ValidationWarning[]): void {
  const type = normalizeNodeType(node.type);
  const config = node.config ?? (node.data as Record<string, unknown> | undefined) ?? {};

  switch (type) {
    case 'API_CALL':
      if (!config.url) {
        errors.push({
          code: 'MISSING_API_URL',
          message: 'API Call node must have a URL',
          nodeId: node.id,
          suggestion: 'Provide the API endpoint URL',
        });
      }
      // Method is optional; default to GET at runtime. Do not warn or error if missing.
      break;

    case 'DECISION':
      if (!config.condition) {
        errors.push({
          code: 'MISSING_DECISION_CONDITION',
          message: 'Decision node must have a condition',
          nodeId: node.id,
          suggestion: 'Define the decision condition',
        });
      }
      break;

    case 'APPROVAL':
      if (!config.assignee) {
        errors.push({
          code: 'MISSING_APPROVAL_ASSIGNEE',
          message: 'Approval node must have an assignee',
          nodeId: node.id,
          suggestion: 'Specify who should approve this step',
        });
      }
      break;

    case 'LOOP':
      if (!config.items) {
        errors.push({
          code: 'MISSING_LOOP_ITEMS',
          message: 'Loop node must have items to iterate',
          nodeId: node.id,
          suggestion: 'Specify the collection to loop over',
        });
      }
      break;
  }
}

/**
 * Validates workflow edges.
 *
 * @param nodes the nodes
 * @param edges the edges to validate
 * @param errors error list to populate
 * @param warnings warning list to populate
 */
function validateEdges(
  nodes: WorkflowNode[],
  edges: WorkflowEdge[],
  errors: ValidationError[],
  warnings: ValidationWarning[]
): void {
  const nodeIds = new Set(nodes.map((n) => n.id));
  const edgeIds = new Set<string>();

  for (const edge of edges) {
    // Check for duplicate IDs
    if (edgeIds.has(edge.id)) {
      errors.push({
        code: 'DUPLICATE_EDGE_ID',
        message: `Duplicate edge ID: ${edge.id}`,
        edgeId: edge.id,
        suggestion: 'Ensure each edge has a unique identifier',
      });
    }
    edgeIds.add(edge.id);

    // Check node references
    const sourceId = edge.source;
    const targetId = edge.target;

    if (!sourceId || !nodeIds.has(sourceId)) {
      errors.push({
        code: 'INVALID_SOURCE_NODE',
        message: `Edge references non-existent source node: ${sourceId ?? 'unknown'}`,
        edgeId: edge.id,
        suggestion: 'Ensure source node exists',
      });
    }

    if (!targetId || !nodeIds.has(targetId)) {
      errors.push({
        code: 'INVALID_TARGET_NODE',
        message: `Edge references non-existent target node: ${targetId ?? 'unknown'}`,
        edgeId: edge.id,
        suggestion: 'Ensure target node exists',
      });
    }

    // Check self-loops
    if (sourceId && targetId && sourceId === targetId) {
      warnings.push({
        code: 'SELF_LOOP',
        message: 'Edge creates a self-loop',
        edgeId: edge.id,
        suggestion: 'Consider if this self-loop is intentional',
      });
    }
  }
}

/**
 * Validates workflow connections.
 *
 * @param nodes the nodes
 * @param edges the edges
 * @param errors error list to populate
 * @param warnings warning list to populate
 */
function validateConnections(
  nodes: WorkflowNode[],
  edges: WorkflowEdge[],
  errors: ValidationError[],
  warnings: ValidationWarning[]
): void {
  // Check for orphaned nodes (nodes with no connections)
  const connectedNodes = new Set<string>();
  for (const edge of edges) {
    const sourceId = edge.source;
    const targetId = edge.target;
    if (sourceId) connectedNodes.add(sourceId);
    if (targetId) connectedNodes.add(targetId);
  }

  for (const node of nodes) {
    const nodeType = normalizeNodeType(node.type);
    // Treat END nodes as orphaned if they have no connections. Only START is excluded from orphan check.
    if (nodeType !== 'START' && !connectedNodes.has(node.id)) {
      warnings.push({
        code: 'ORPHANED_NODE',
        message: 'Node is not connected to any other node',
        nodeId: node.id,
        suggestion: 'Connect this node to the workflow or remove it',
      });
    }
  }

  // Check for unreachable nodes
  const reachableNodes = findReachableNodes(nodes, edges);
  for (const node of nodes) {
    if (!reachableNodes.has(node.id) && normalizeNodeType(node.type) !== 'START') {
      warnings.push({
        code: 'UNREACHABLE_NODE',
        message: 'Node is unreachable from the start node',
        nodeId: node.id,
        suggestion: 'Ensure there is a path from the start node to this node',
      });
    }
  }
}

/**
 * Validates workflow triggers.
 *
 * @param triggers the triggers to validate
 * @param errors error list to populate
 * @param warnings warning list to populate
 */
function validateTriggers(
  triggers: Array<string | WorkflowTrigger>,
  errors: ValidationError[],
  warnings: ValidationWarning[]
): void {
  if (!triggers || triggers.length === 0) {
    warnings.push({
      code: 'NO_TRIGGERS',
      message: 'Workflow has no triggers',
      suggestion: 'Add at least one trigger to enable workflow execution',
    });
  }
}

/**
 * Detects cycles in the workflow graph.
 *
 * @param nodes the nodes
 * @param edges the edges
 * @param errors error list to populate
 */
function detectCycles(nodes: WorkflowNode[], edges: WorkflowEdge[], errors: ValidationError[]): void {
  const adjacencyList = new Map<string, string[]>();

  // Build adjacency list
  for (const node of nodes) {
    adjacencyList.set(node.id, []);
  }
  for (const edge of edges) {
    const sourceId = edge.source;
    const targetId = edge.target;
    if (!sourceId || !targetId) {
      continue;
    }
    const neighbors = adjacencyList.get(sourceId) || [];
    neighbors.push(targetId);
    adjacencyList.set(sourceId, neighbors);
  }

  // DFS to detect cycles
  const visited = new Set<string>();
  const recursionStack = new Set<string>();

  function hasCycle(nodeId: string): boolean {
    visited.add(nodeId);
    recursionStack.add(nodeId);

    const neighbors = adjacencyList.get(nodeId) || [];
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor)) {
        if (hasCycle(neighbor)) {
          return true;
        }
      } else if (recursionStack.has(neighbor)) {
        return true;
      }
    }

    recursionStack.delete(nodeId);
    return false;
  }

  for (const node of nodes) {
    if (!visited.has(node.id)) {
      if (hasCycle(node.id)) {
        errors.push({
          code: 'CYCLE_DETECTED',
          message: 'Workflow contains a cycle',
          suggestion: 'Remove edges to break the cycle',
        });
        break;
      }
    }
  }
}

/**
 * Finds all reachable nodes from the start node.
 *
 * @param nodes the nodes
 * @param edges the edges
 * @returns set of reachable node IDs
 */
function findReachableNodes(nodes: WorkflowNode[], edges: WorkflowEdge[]): Set<string> {
  const startNode = nodes.find((n) => normalizeNodeType(n.type) === 'START');
  if (!startNode) {
    return new Set();
  }

  const reachable = new Set<string>();
  const queue = [startNode.id];

  while (queue.length > 0) {
    const nodeId = queue.shift()!;
    if (reachable.has(nodeId)) {
      continue;
    }

    reachable.add(nodeId);

    // Find all edges from this node
    for (const edge of edges) {
      const sourceId = edge.source;
      const targetId = edge.target;
      if (!sourceId || !targetId) {
        continue;
      }

      if (sourceId === nodeId && !reachable.has(targetId)) {
        queue.push(targetId);
      }
    }
  }

  return reachable;
}
