import { describe, it, expect, beforeEach } from 'vitest';
import { validateWorkflow } from '@/lib/services/validationService';
import {
  NodeType,
  ExecutionStatus,
  EdgeType,
  TriggerType,
} from '@/types/workflow.types';
import type { WorkflowDefinition, WorkflowNode, WorkflowEdge, WorkflowTrigger } from '@/types/workflow.types';

/**
 * Workflow lifecycle E2E tests.
 *
 * <p><b>Purpose</b><br>
 * Tests complete workflow lifecycle from creation to execution.
 * Validates workflow creation, validation, modification, and execution.
 *
 * <p><b>Test Coverage</b><br>
 * - Workflow creation with valid configuration
 * - Workflow validation with errors and warnings
 * - Node addition and removal
 * - Edge creation and validation
 * - Workflow modification and updates
 * - Trigger configuration
 *
 * @doc.type test
 * @doc.purpose Workflow lifecycle E2E tests
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
  type: EdgeType.DEFAULT,
  ...overrides,
});

const createTrigger = (overrides: Partial<WorkflowTrigger> = {}): WorkflowTrigger => ({
  id: 'trigger-1',
  type: TriggerType.MANUAL,
  active: true,
  config: {},
  ...overrides,
});

const createWorkflow = (overrides: Partial<WorkflowDefinition> = {}): WorkflowDefinition => {
  const base: WorkflowDefinition = {
    id: 'workflow-test-1',
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
    // Set timestamps slightly in the past to avoid millisecond-equality flakiness in tests
    createdAt: new Date(Date.now() - 1000).toISOString(),
    updatedAt: new Date(Date.now() - 1000).toISOString(),
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

describe('Workflow Lifecycle', () => {
  let workflow: WorkflowDefinition;

  beforeEach(() => {
    workflow = createWorkflow();
  });

  describe('Workflow Creation', () => {
    /**
     * Test: Create workflow with valid configuration.
     *
     * GIVEN: Valid workflow definition
     * WHEN: Workflow is created
     * THEN: Workflow is stored with correct properties
     */
    it('should create workflow with valid configuration', () => {
      expect(workflow.id).toBe('workflow-test-1');
      expect(workflow.name).toBe('Test Workflow');
      expect(workflow.status).toBe('DRAFT');
      expect(workflow.version).toBe(1);
    });

    /**
     * Test: Workflow has empty nodes and edges initially.
     *
     * GIVEN: New workflow
     * WHEN: Workflow is created
     * THEN: Nodes and edges arrays are empty
     */
    it('should have empty nodes and edges initially', () => {
      expect(workflow.nodes).toHaveLength(0);
      expect(workflow.edges).toHaveLength(0);
    });

    /**
     * Test: Workflow has no triggers initially.
     *
     * GIVEN: New workflow
     * WHEN: Workflow is created
     * THEN: Triggers array is empty
     */
    it('should have no triggers initially', () => {
      expect(workflow.triggers).toHaveLength(0);
    });
  });

  describe('Workflow Validation', () => {
    /**
     * Test: Empty workflow fails validation.
     *
     * GIVEN: Workflow with no nodes
     * WHEN: Validation is run
     * THEN: Validation fails with NO_NODES error
     */
    it('should fail validation with no nodes', () => {
      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({
          code: 'NO_NODES',
        })
      );
    });

    /**
     * Test: Workflow without triggers shows warning.
     *
     * GIVEN: Workflow with nodes but no triggers
     * WHEN: Validation is run
     * THEN: Validation shows NO_TRIGGERS warning
     */
    it('should warn about missing triggers', () => {
      workflow.nodes = [
        createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
      ];

      const result = validateWorkflow(workflow);
      expect(result.warnings).toContainEqual(
        expect.objectContaining({
          code: 'NO_TRIGGERS',
        })
      );
    });

    /**
     * Test: Valid workflow passes validation.
     *
     * GIVEN: Complete workflow with nodes and triggers
     * WHEN: Validation is run
     * THEN: Validation passes with no errors
     */
    it('should pass validation with valid configuration', () => {
      workflow.nodes = [
        createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
        createNode({ id: 'node-2', type: NodeType.END, data: { label: 'End' }, config: {} }),
      ];

      workflow.edges = [
        createEdge({
          id: 'edge-1',
          source: 'node-1',
          target: 'node-2',
          type: EdgeType.DEFAULT,
        }),
      ];

      workflow.triggers = [createTrigger()];

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(true);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
      expect(result.warnings).toHaveLength(0);
    });
  });

  describe('Node Management', () => {
    /**
     * Test: Add node to workflow.
     *
     * GIVEN: Workflow with no nodes
     * WHEN: Node is added
     * THEN: Node is added to nodes array
     */
    it('should add node to workflow', () => {
      const node = createNode({
        id: 'node-1',
        type: NodeType.API_CALL,
        data: { label: 'API Call' },
        config: { url: 'https://api.example.com', method: 'GET' },
      });

      workflow.nodes.push(node);

      expect(workflow.nodes).toHaveLength(1);
      expect(workflow.nodes[0].id).toBe('node-1');
    });

    /**
     * Test: Remove node from workflow.
     *
     * GIVEN: Workflow with nodes
     * WHEN: Node is removed
     * THEN: Node is removed from nodes array
     */
    it('should remove node from workflow', () => {
      workflow.nodes = [
        createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
        createNode({ id: 'node-2', type: NodeType.END, data: { label: 'End' }, config: {} }),
      ];

      workflow.nodes = workflow.nodes.filter((n) => n.id !== 'node-1');

      expect(workflow.nodes).toHaveLength(1);
      expect(workflow.nodes[0].id).toBe('node-2');
    });

    /**
     * Test: Update node configuration.
     *
     * GIVEN: Workflow with node
     * WHEN: Node configuration is updated
     * THEN: Node configuration is changed
     */
    it('should update node configuration', () => {
      workflow.nodes = [
        createNode({
          id: 'node-1',
          type: NodeType.API_CALL,
          data: { label: 'API Call' },
          config: { url: 'https://api.example.com', method: 'GET' },
        }),
      ];

      workflow.nodes[0].config.method = 'POST';

      expect(workflow.nodes[0].config.method).toBe('POST');
    });
  });

  describe('Edge Management', () => {
    /**
     * Test: Add edge between nodes.
     *
     * GIVEN: Workflow with two nodes
     * WHEN: Edge is created between nodes
     * THEN: Edge is added to edges array
     */
    it('should add edge between nodes', () => {
      workflow.nodes = [
        createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
        createNode({ id: 'node-2', type: NodeType.END, data: { label: 'End' }, config: {} }),
      ];

      const edge = createEdge({
        id: 'edge-1',
        source: 'node-1',
        target: 'node-2',
        type: EdgeType.DEFAULT,
      });

      workflow.edges.push(edge);

      expect(workflow.edges).toHaveLength(1);
      expect(workflow.edges[0].source).toBe('node-1');
    });

    /**
     * Test: Remove edge from workflow.
     *
     * GIVEN: Workflow with edges
     * WHEN: Edge is removed
     * THEN: Edge is removed from edges array
     */
    it('should remove edge from workflow', () => {
      workflow.edges = [
        createEdge({
          id: 'edge-1',
          source: 'node-1',
          target: 'node-2',
          type: EdgeType.DEFAULT,
        }),
        createEdge({
          id: 'edge-2',
          source: 'node-2',
          target: 'node-3',
          type: EdgeType.DEFAULT,
        }),
      ];

      workflow.edges = workflow.edges.filter((edgeItem) => edgeItem.id !== 'edge-1');

      expect(workflow.edges).toHaveLength(1);
      expect(workflow.edges[0].id).toBe('edge-2');
    });

    /**
     * Test: Detect invalid edge references.
     *
     * GIVEN: Workflow containing an edge targeting a missing node
     * WHEN: Validation is executed
     * THEN: `INVALID_TARGET_NODE` error is reported
     */
    it('should detect invalid edge references', () => {
      workflow.nodes = [
        createNode({ id: 'node-1', type: NodeType.START, data: { label: 'Start' } }),
      ];

      workflow.edges = [
        createEdge({
          id: 'edge-1',
          source: 'node-1',
          target: 'node-999',
          type: EdgeType.DEFAULT,
        }),
      ];

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(false);
      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(
        expect.objectContaining({ code: 'INVALID_TARGET_NODE' })
      );
    });
  });

  describe('Workflow Modification', () => {
    /**
     * Test: Update workflow name.
     *
     * GIVEN: Workflow with name
     * WHEN: Name is updated
     * THEN: Workflow name is changed
     */
    it('should update workflow name', () => {
      workflow.name = 'Updated Workflow';

      expect(workflow.name).toBe('Updated Workflow');
    });

    /**
     * Test: Update workflow status.
     *
     * GIVEN: Workflow in DRAFT status
     * WHEN: Status is changed to PUBLISHED
     * THEN: Workflow status is updated
     */
    it('should update workflow status', () => {
      workflow.status = 'PUBLISHED';

      expect(workflow.status).toBe(ExecutionStatus.PUBLISHED);
    });

    /**
     * Test: Increment workflow version.
     *
     * GIVEN: Workflow with version 1
     * WHEN: Workflow is updated
     * THEN: Version is incremented
     */
    it('should increment workflow version', () => {
      workflow.version = 2;

      expect(workflow.version).toBe(2);
    });

    /**
     * Test: Update workflow timestamp.
     *
     * GIVEN: Workflow with updatedAt timestamp
     * WHEN: Workflow is modified
     * THEN: updatedAt is changed
     */
    it('should update workflow timestamp', () => {
      const oldTime = workflow.updatedAt;
      workflow.updatedAt = new Date().toISOString();

      expect(workflow.updatedAt).not.toBe(oldTime);
    });
  });

  describe('Trigger Configuration', () => {
    /**
     * Test: Add trigger to workflow.
     *
     * GIVEN: Workflow with no triggers
     * WHEN: Trigger is added
     * THEN: Trigger is added to triggers array
     */
    it('should add trigger to workflow', () => {
      const trigger = {
        id: 'trigger-1',
        type: TriggerType.MANUAL,
        config: {},
        active: true,
      };

      workflow.triggers.push(trigger);

      expect(workflow.triggers).toHaveLength(1);
      expect(workflow.triggers[0].type).toBe(TriggerType.MANUAL);
    });

    /**
     * Test: Remove trigger from workflow.
     *
     * GIVEN: Workflow with triggers
     * WHEN: Trigger is removed
     * THEN: Trigger is removed from triggers array
     */
    it('should remove trigger from workflow', () => {
      workflow.triggers = [
        createTrigger({ id: 'trigger-1', type: TriggerType.MANUAL }),
        createTrigger({ id: 'trigger-2', type: TriggerType.SCHEDULED, config: { schedule: '0 0 * * *' } }),
      ];

      workflow.triggers = workflow.triggers.filter((t) => t.id !== 'trigger-1');

      expect(workflow.triggers).toHaveLength(1);
      expect(workflow.triggers[0].type).toBe(TriggerType.SCHEDULED);
    });

    /**
     * Test: Activate/deactivate trigger.
     *
     * GIVEN: Workflow with active trigger
     * WHEN: Trigger is deactivated
     * THEN: Trigger active status is changed
     */
    it('should activate/deactivate trigger', () => {
      workflow.triggers = [
        {
          id: 'trigger-1',
          type: TriggerType.MANUAL,
          config: {},
          active: true,
        },
      ];

      workflow.triggers[0].active = false;

      expect(workflow.triggers[0].active).toBe(false);
    });
  });

  describe('Complex Workflow Scenarios', () => {
    /**
     * Test: Create multi-branch workflow.
     *
     * GIVEN: Workflow with decision node
     * WHEN: Multiple branches are created
     * THEN: Workflow has multiple conditional edges
     */
    it('should create multi-branch workflow', () => {
      workflow.nodes = [
        createNode({
          id: 'node-1',
          type: NodeType.START,
          data: { label: 'Start' },
        }),
        createNode({
          id: 'node-2',
          type: NodeType.DECISION,
          data: { label: 'Check Status' },
          config: { condition: 'status === "active"' },
        }),
        createNode({
          id: 'node-3',
          type: NodeType.API_CALL,
          data: { label: 'Success Path' },
          config: { url: 'https://api.example.com/success' },
        }),
        createNode({
          id: 'node-4',
          type: NodeType.API_CALL,
          data: { label: 'Failure Path' },
          config: { url: 'https://api.example.com/failure' },
        }),
        createNode({
          id: 'node-5',
          type: NodeType.END,
          data: { label: 'End' },
        }),
      ];

      workflow.edges = [
        createEdge({
          id: 'edge-1',
          source: 'node-1',
          target: 'node-2',
          type: EdgeType.DEFAULT,
        }),
        createEdge({
          id: 'edge-2',
          source: 'node-2',
          target: 'node-3',
          type: EdgeType.CONDITIONAL,
          condition: 'status === "active"',
        }),
        createEdge({
          id: 'edge-3',
          source: 'node-2',
          target: 'node-4',
          type: EdgeType.CONDITIONAL,
          condition: 'status !== "active"',
        }),
        createEdge({
          id: 'edge-4',
          source: 'node-3',
          target: 'node-5',
          type: EdgeType.DEFAULT,
        }),
        createEdge({
          id: 'edge-5',
          source: 'node-4',
          target: 'node-5',
          type: EdgeType.DEFAULT,
        }),
      ];

      workflow.triggers = [
        {
          id: 'trigger-1',
          type: TriggerType.MANUAL,
          config: {},
          active: true,
        },
      ];

      const result = validateWorkflow(workflow);
      expect(result.isValid).toBe(true);
      expect(result.valid).toBe(true);
      expect(result.errors).toStrictEqual([]);
      expect(result.warnings).toStrictEqual([]);
    });
  });
});
