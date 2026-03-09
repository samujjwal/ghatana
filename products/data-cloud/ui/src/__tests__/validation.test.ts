import { describe, it, expect } from 'vitest';
import { validateWorkflow } from '@/lib/services/validationService';
import { NodeType, EdgeType, TriggerType } from '@/types/workflow.types';
import type { WorkflowDefinition, WorkflowNode, WorkflowEdge } from '@/types/workflow.types';

/**
 * Workflow validation E2E tests.
 *
 * <p><b>Purpose</b><br>
 * Tests comprehensive validation logic for workflows.
 * Validates structural, semantic, and graph-based rules.
 *
 * <p><b>Test Coverage</b><br>
 * - Basic structure validation
 * - Node configuration validation
 * - Edge validation
 * - Cycle detection
 * - Orphaned node detection
 * - Unreachable node detection
 *
 * @doc.type test
 * @doc.purpose Workflow validation E2E tests
 * @doc.layer frontend
 */

const createNode = (overrides: Partial<WorkflowNode> = {}): WorkflowNode => ({
  id: 'node-1',
  type: NodeType.API_CALL,
  position: { x: 0, y: 0 },
  data: { label: 'Node' },
  config: {},
  ...overrides,
});

const createEdge = (overrides: Partial<WorkflowEdge> = {}): WorkflowEdge => ({
  id: 'edge-1',
  source: 'node-1',
  target: 'node-2',
  source: 'node-1',
  target: 'node-2',
  ...overrides,
});

const createWorkflow = (overrides: Partial<WorkflowDefinition> = {}): WorkflowDefinition => {
  const base: WorkflowDefinition = {
    id: 'workflow-1',
    tenantId: 'tenant-1',
    name: 'Test Workflow',
    collectionId: 'collection-1',
    nodes: [],
    edges: [],
    triggers: [],
    variables: {},
    status: 'DRAFT',
    version: 1,
    active: true,
    tags: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    createdBy: 'user-1',
    updatedBy: 'user-1',
  };

  const workflow = {
    ...base,
    ...overrides,
  };

  if (overrides.nodes) {
    workflow.nodes = overrides.nodes;
  }

  if (overrides.edges) {
    workflow.edges = overrides.edges;
  }

  if (overrides.triggers) {
    workflow.triggers = overrides.triggers;
  }

  if (overrides.variables) {
    workflow.variables = overrides.variables;
  }

  return workflow;
};

// Helper function to validate workflow and expect no errors
const expectValidWorkflow = (workflow: WorkflowDefinition) => {
  const result = validateWorkflow(workflow);
  if (!result.valid) {
    console.error('Validation errors:', result.errors);
  }
  expect(result.valid).toBe(true);
  expect(result.errors).toHaveLength(0);
};

// Helper function to expect specific validation errors
const expectValidationErrors = (
  workflow: WorkflowDefinition,
  expectedErrorCount: number,
  expectedErrorMessages: string[] = []
) => {
  const result = validateWorkflow(workflow);
  expect(result.valid).toBe(expectedErrorCount === 0);
  expect(result.errors).toHaveLength(expectedErrorCount);
  
  if (expectedErrorMessages.length > 0) {
    const errorMessages = result.errors.map(e => e.message);
    expectedErrorMessages.forEach(msg => {
      expect(errorMessages).toContain(msg);
    });
  }
};

describe('Workflow Validation', () => {

  describe('Basic Structure Validation', () => {
    /**
     * Test: Missing workflow ID fails validation.
     *
     * GIVEN: Workflow without ID
     * WHEN: Validation is run
     * THEN: MISSING_ID error is returned
     */
    it('should fail validation with missing ID', () => {
      const workflow = createWorkflow({ id: '' });
      const result = validateWorkflow(workflow);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'MISSING_ID' })
      );
    });

    /**
     * Test: Missing workflow name fails validation.
     *
     * GIVEN: Workflow without name
     * WHEN: Validation is run
     * THEN: MISSING_NAME error is returned
     */
    it('should fail validation with missing name', () => {
      const workflow = createWorkflow({ name: '' });
      const result = validateWorkflow(workflow);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'MISSING_NAME' })
      );
    });

    /**
     * Test: Missing collection ID fails validation.
     *
     * GIVEN: Workflow without collection ID
     * WHEN: Validation is run
     * THEN: MISSING_COLLECTION error is returned
     */
    it('should fail validation with missing collection', () => {
      const workflow = createWorkflow({ collectionId: '' });
      const result = validateWorkflow(workflow);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'MISSING_COLLECTION' })
      );
    });

    /**
     * Test: No nodes fails validation.
     *
     * GIVEN: Workflow with empty nodes array
     * WHEN: Validation is run
     * THEN: NO_NODES error is returned
     */
    it('should fail validation with no nodes', () => {
      const workflow = createWorkflow({ nodes: [] });
      const result = validateWorkflow(workflow);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'NO_NODES' })
      );
    });
  });

  describe('Node Validation', () => {
    /**
     * Test: Duplicate node IDs fail validation.
     *
     * GIVEN: Workflow with duplicate node IDs
     * WHEN: Validation is run
     * THEN: DUPLICATE_NODE_ID error is returned
     */
    it('should fail validation with duplicate node IDs', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
          createNode({ id: 'node-1', type: NodeType.END, data: { label: 'End' } }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'DUPLICATE_NODE_ID' })
      );
    });

    /**
     * Test: Missing node type fails validation.
     *
     * GIVEN: Node without type
     * WHEN: Validation is run
     * THEN: MISSING_NODE_TYPE error is returned
     */
    it('should fail validation with missing node type', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({ id: 'node-1', type: undefined as any }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'MISSING_NODE_TYPE' })
      );
    });

    /**
     * Test: Missing API URL in API Call node fails validation.
     *
     * GIVEN: API Call node without URL
     * WHEN: Validation is run
     * THEN: MISSING_API_URL error is returned
     */
    it('should fail validation with missing API URL', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({
            id: 'node-1',
            type: NodeType.API_CALL,
            data: { label: 'API Call' },
            config: {}, // Missing url/method
          }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'MISSING_API_URL' })
      );
    });

    /**
     * Test: Missing decision condition fails validation.
     *
     * GIVEN: Decision node without condition
     * WHEN: Validation is run
     * THEN: MISSING_DECISION_CONDITION error is returned
     */
    it('should fail validation with missing decision condition', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({
            id: 'node-1',
            type: NodeType.DECISION,
            data: { label: 'Decision' },
            config: {}, // Missing condition
          }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'MISSING_DECISION_CONDITION' })
      );
    });
  });

  describe('Edge Validation', () => {
    /**
     * Test: Invalid source node reference fails validation.
     *
     * GIVEN: Edge with non-existent source node
     * WHEN: Validation is run
     * THEN: INVALID_SOURCE_NODE error is returned
     */
    it('should fail validation with invalid source node', () => {
      const workflow = createWorkflow({
        nodes: [createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } })],
        edges: [
          createEdge({
            id: 'edge-1',
            source: 'node-999',
            target: 'node-1',
            source: 'node-999',
            target: 'node-1',
          }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'INVALID_SOURCE_NODE' })
      );
    });

    /**
     * Test: Invalid target node reference fails validation.
     *
     * GIVEN: Edge with non-existent target node
     * THEN: INVALID_TARGET_NODE error is returned
     */
    it('should fail validation with invalid target node', () => {
      const workflow = createWorkflow({
        nodes: [createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } })],
        edges: [
          createEdge({
            id: 'edge-1',
            source: 'node-1',
            target: 'node-999',
            source: 'node-1',
            target: 'node-999',
          }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'INVALID_TARGET_NODE' })
      );
    });

    /**
     * Test: Self-loop edges produce warnings instead of errors.
     *
     * GIVEN: Workflow where an edge starts and ends on the same node
     * WHEN: Validation is executed
     * THEN: `SELF_LOOP` warning is returned
     */
    it('should warn about self-loop edges', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({
            id: 'node-1',
            type: NodeType.API_CALL,
            data: { label: 'API Call' },
            config: { url: 'https://api.example.com', method: 'GET' },
          }),
        ],
        edges: [
          createEdge({
            id: 'edge-1',
            source: 'node-1',
            target: 'node-1',
            source: 'node-1',
            target: 'node-1',
          }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.warnings).toContainEqual(
        expect.objectContaining({ code: 'SELF_LOOP' })
      );
    });

    /**
     * Test: Directed cycles invalidate workflows.
     *
     * GIVEN: Workflow graph containing a directed cycle
     * WHEN: Validation is executed
     * THEN: `CYCLE_DETECTED` error is returned
     */
    it('should detect cycles in workflow', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
          createNode({
            id: 'node-2',
            type: NodeType.API_CALL,
            data: { label: 'API Call' },
            config: { url: 'https://api.example.com', method: 'GET' },
          }),
          createNode({ id: 'node-3', type: NodeType.END, data: { label: 'End' }, config: {} }),
        ],
        edges: [
          createEdge({
            id: 'edge-1',
            source: 'node-1',
            target: 'node-2',
            source: 'node-1',
            target: 'node-2',
          }),
          createEdge({
            id: 'edge-2',
            source: 'node-2',
            target: 'node-3',
            source: 'node-2',
            target: 'node-3',
          }),
          createEdge({
            id: 'edge-3',
            source: 'node-3',
            target: 'node-1',
            source: 'node-3',
            target: 'node-1',
          }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'CYCLE_DETECTED' })
      );
    });
    /**
     * Test: Orphaned node detection.
     *
     * GIVEN: Workflow with a node that has no edges
     * WHEN: Validation is run
     * THEN: `ORPHANED_NODE` warning is returned
     */
    it('should detect orphaned nodes', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
          createNode({
            id: 'node-2',
            type: NodeType.API_CALL,
            data: { label: 'API Call' },
            config: { url: 'https://api.example.com' },
          }),
          createNode({ id: 'node-3', type: NodeType.END, data: { label: 'End' }, config: {} }),
        ],
        edges: [
          createEdge({
            id: 'edge-1',
            source: 'node-1',
            target: 'node-2',
            source: 'node-1',
            target: 'node-2',
          }),
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.warnings).toContainEqual(
        expect.objectContaining({ code: 'ORPHANED_NODE', nodeId: 'node-3' })
      );
    });
  });

  describe('Trigger Validation', () => {
    /**
     * Test: Missing triggers shows warning.
     *
     * GIVEN: Workflow with nodes but no triggers
     * WHEN: Validation is run
     * THEN: `NO_TRIGGERS` warning is returned
     */
    it('should warn about missing triggers', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
          createNode({ id: 'node-2', type: NodeType.END, data: { label: 'End' }, config: {} }),
        ],
        triggers: [],
      });

      const result = validateWorkflow(workflow);
      expect(result.warnings).toContainEqual(
        expect.objectContaining({ code: 'NO_TRIGGERS' })
      );
    });
  });

  describe('Valid Workflows', () => {
    /**
     * Test: Simple linear workflow passes validation.
     *
     * GIVEN: Valid linear workflow
     * WHEN: Validation is run
     * THEN: Workflow is valid
     */
    it('should validate simple linear workflow', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
          createNode({
            id: 'node-2',
            type: NodeType.API_CALL,
            data: { label: 'API Call' },
            config: { url: 'https://api.example.com', method: 'GET' },
          }),
          createNode({ id: 'node-3', type: NodeType.END, data: { label: 'End' }, config: {} }),
        ],
        edges: [
          createEdge({
            id: 'edge-1',
            sourceNodeId: 'node-1',
            targetNodeId: 'node-2',
            source: 'node-1',
            target: 'node-2',
            type: EdgeType.DEFAULT,
          }),
          createEdge({
            id: 'edge-2',
            sourceNodeId: 'node-2',
            targetNodeId: 'node-3',
            source: 'node-2',
            target: 'node-3',
            type: EdgeType.DEFAULT,
          }),
        ],
        triggers: [
          {
            id: 'trigger-1',
            type: TriggerType.MANUAL,
            config: {},
            active: true,
          },
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    /**
     * Test: Complex branching workflow passes validation.
     *
     * GIVEN: Valid workflow with decision branching
     * WHEN: Validation is run
     * THEN: Workflow is valid
     */
    it('should validate complex branching workflow', () => {
      const workflow = createWorkflow({
        nodes: [
          createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
          createNode({
            id: 'node-2',
            type: NodeType.DECISION,
            data: { label: 'Check' },
            config: { condition: 'status === active' },
          }),
          createNode({
            id: 'node-3',
            type: NodeType.API_CALL,
            data: { label: 'Success' },
            config: { url: 'https://api.example.com/success' },
          }),
          createNode({
            id: 'node-4',
            type: NodeType.API_CALL,
            data: { label: 'Failure' },
            config: { url: 'https://api.example.com/failure' },
          }),
          createNode({ id: 'node-5', type: NodeType.END, data: { label: 'End' }, config: {} }),
        ],
        edges: [
          createEdge({
            id: 'edge-1',
            sourceNodeId: 'node-1',
            targetNodeId: 'node-2',
            source: 'node-1',
            target: 'node-2',
            type: EdgeType.DEFAULT,
          }),
          createEdge({
            id: 'edge-2',
            sourceNodeId: 'node-2',
            targetNodeId: 'node-3',
            source: 'node-2',
            target: 'node-3',
            type: EdgeType.CONDITIONAL,
          }),
          createEdge({
            id: 'edge-3',
            sourceNodeId: 'node-2',
            targetNodeId: 'node-4',
            source: 'node-2',
            target: 'node-4',
            type: EdgeType.CONDITIONAL,
          }),
          createEdge({
            id: 'edge-4',
            sourceNodeId: 'node-3',
            targetNodeId: 'node-5',
            source: 'node-3',
            target: 'node-5',
            type: EdgeType.DEFAULT,
          }),
          createEdge({
            id: 'edge-5',
            sourceNodeId: 'node-4',
            targetNodeId: 'node-5',
            source: 'node-4',
            target: 'node-5',
            type: EdgeType.DEFAULT,
          }),
        ],
        triggers: [
          {
            id: 'trigger-1',
            type: TriggerType.MANUAL,
            config: {},
            active: true,
          },
        ],
      });

      const result = validateWorkflow(workflow);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });
  });
});
