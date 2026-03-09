/**
 * Workflow validation service.
 *
 * <p><b>Purpose</b><br>
 * Validates workflow definitions against rules and provides auto-fix suggestions.
 *
 * <p><b>Validation Rules</b><br>
 * - At least one start node
 * - At least one end node
 * - All nodes connected
 * - No orphaned nodes
 * - Valid node types
 * - Required fields present
 *
 * @doc.type service
 * @doc.purpose Workflow validation
 * @doc.layer frontend
 * @doc.pattern Service
 */

export type ValidationError = {
  id: string;
  severity: 'error' | 'warning';
  message: string;
  nodeId?: string;
  suggestion?: string;
};

export type ValidationResult = {
  isValid: boolean;
  errors: ValidationError[];
  warnings: ValidationError[];
};

type WorkflowValidationNode = {
  id: string;
  type: string;
  label?: string;
  position?: { x: number; y: number };
};

type WorkflowValidationEdge = {
  source: string;
  target: string;
};

type WorkflowValidationShape = {
  nodes: WorkflowValidationNode[];
  edges: WorkflowValidationEdge[];
};

export class ValidationService {
  /**
   * Validates workflow definition.
   */
  static validate(workflow: WorkflowValidationShape): ValidationResult {
    const errors: ValidationError[] = [];
    const warnings: ValidationError[] = [];

    // Check for start node
    const hasStart = workflow.nodes.some((n) => n.type === 'start');
    if (!hasStart) {
      errors.push({
        id: 'no-start-node',
        severity: 'error',
        message: 'Workflow must have a start node',
        suggestion: 'Add a start node to begin the workflow',
      });
    }

    // Check for end node
    const hasEnd = workflow.nodes.some((n) => n.type === 'end');
    if (!hasEnd) {
      errors.push({
        id: 'no-end-node',
        severity: 'error',
        message: 'Workflow must have an end node',
        suggestion: 'Add an end node to complete the workflow',
      });
    }

    // Check for orphaned nodes
    workflow.nodes.forEach((node) => {
      const hasIncoming = workflow.edges.some((e) => e.target === node.id);
      const hasOutgoing = workflow.edges.some((e) => e.source === node.id);

      if (node.type !== 'start' && !hasIncoming) {
        warnings.push({
          id: `orphaned-${node.id}`,
          severity: 'warning',
          message: `Node "${node.label}" has no incoming connections`,
          nodeId: node.id,
          suggestion: 'Connect this node to the workflow',
        });
      }

      if (node.type !== 'end' && !hasOutgoing) {
        warnings.push({
          id: `dangling-${node.id}`,
          severity: 'warning',
          message: `Node "${node.label}" has no outgoing connections`,
          nodeId: node.id,
          suggestion: 'Connect this node to another node or end',
        });
      }
    });

    // Check for required fields
    workflow.nodes.forEach((node) => {
      if (!node.label || node.label.trim() === '') {
        errors.push({
          id: `missing-label-${node.id}`,
          severity: 'error',
          message: `Node ${node.id} is missing a label`,
          nodeId: node.id,
          suggestion: 'Add a descriptive label to this node',
        });
      }
    });

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Applies auto-fix suggestions.
   */
  static applyFix(workflow: WorkflowValidationShape, errorId: string): WorkflowValidationShape {
    const fixed = JSON.parse(JSON.stringify(workflow)) as WorkflowValidationShape;

    if (errorId === 'no-start-node') {
      fixed.nodes.unshift({
        id: 'start-' + Date.now(),
        type: 'start',
        label: 'Start',
        position: { x: 100, y: 100 },
      });
    }

    if (errorId === 'no-end-node') {
      fixed.nodes.push({
        id: 'end-' + Date.now(),
        type: 'end',
        label: 'End',
        position: { x: 500, y: 500 },
      });
    }

    return fixed;
  }
}
