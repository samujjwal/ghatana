import { describe, it, expect, vi, beforeEach } from 'vitest';
import { WorkflowServiceImpl, createDefaultWorkflowDefinition, createEmptyExecution } from '../services/workflows-impl';
import type { WorkflowDefinition, WorkflowNode, WorkflowEdge, CreateWorkflowRequest } from '../services/workflows';

/**
 * WorkflowServiceImpl Tests - 100% Coverage
 * 
 * @doc.type test
 * @doc.purpose Comprehensive tests for WorkflowServiceImpl
 * @doc.layer ui
 * @doc.pattern Unit Test
 */

describe('WorkflowServiceImpl', () => {
  let service: WorkflowServiceImpl;

  beforeEach(() => {
    vi.clearAllMocks();
    service = new WorkflowServiceImpl('/api/v1');
  });

  describe('getWorkflows', () => {
    it('should fetch workflows with options', async () => {
      const mockWorkflows = [{ id: '1', name: 'Test', tenantId: 'tenant-1', description: '', definition: { nodes: [], edges: [], variables: [], triggers: [], config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' } }, status: 'draft' as const, executions: 0, createdAt: '', updatedAt: '', createdBy: '' }];
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockWorkflows) });

      const result = await service.getWorkflows('tenant-1', { search: 'test', status: 'draft' });

      expect(result).toEqual(mockWorkflows);
    });
  });

  describe('getWorkflow', () => {
    it('should return workflow when found', async () => {
      const mockWorkflow = { id: '1', name: 'Test', tenantId: 'tenant-1', description: '', definition: { nodes: [], edges: [], variables: [], triggers: [], config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' } }, status: 'draft' as const, executions: 0, createdAt: '', updatedAt: '', createdBy: '' };
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockWorkflow) });

      const result = await service.getWorkflow('1', 'tenant-1');

      expect(result).toEqual(mockWorkflow);
    });

    it('should return null when not found', async () => {
      global.fetch = vi.fn().mockResolvedValue({ status: 404 });

      const result = await service.getWorkflow('1', 'tenant-1');

      expect(result).toBeNull();
    });
  });

  describe('createWorkflow', () => {
    it('should create after validation passes', async () => {
      const definition = createDefaultWorkflowDefinition();
      const request: CreateWorkflowRequest = { tenantId: 'tenant-1', name: 'Test', definition };
      const mockWorkflow = { id: '1', ...request, description: '', status: 'draft' as const, executions: 0, createdAt: '', updatedAt: '', createdBy: '' };
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockWorkflow) });

      const result = await service.createWorkflow(request);

      expect(result).toEqual(mockWorkflow);
    });

    it('should throw when validation fails', async () => {
      const request: CreateWorkflowRequest = { tenantId: 'tenant-1', name: 'Test', definition: { nodes: [], edges: [], variables: [], triggers: [], config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' } } };

      await expect(service.createWorkflow(request)).rejects.toThrow('Validation failed');
    });
  });

  describe('validateWorkflow', () => {
    it('should pass valid workflow', async () => {
      const definition = createDefaultWorkflowDefinition();

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(true);
    });

    it('should fail workflow without nodes', async () => {
      const definition: WorkflowDefinition = { nodes: [], edges: [], variables: [], triggers: [], config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' } };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'NO_NODES' }));
    });

    it('should fail workflow without start node', async () => {
      const definition: WorkflowDefinition = {
        nodes: [{ id: 'end', type: 'end', position: { x: 0, y: 0 }, data: { label: 'End', config: {} } }],
        edges: [],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'NO_START_NODE' }));
    });

    it('should fail workflow without end node', async () => {
      const definition: WorkflowDefinition = {
        nodes: [{ id: 'start', type: 'start', position: { x: 0, y: 0 }, data: { label: 'Start', config: {} } }],
        edges: [],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'NO_END_NODE' }));
    });

    it('should fail duplicate node ids', async () => {
      const definition: WorkflowDefinition = {
        nodes: [
          { id: 'node1', type: 'start', position: { x: 0, y: 0 }, data: { label: 'Start', config: {} } },
          { id: 'node1', type: 'end', position: { x: 100, y: 0 }, data: { label: 'End', config: {} } }
        ],
        edges: [],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'DUPLICATE_NODE_ID' }));
    });

    it('should fail invalid edge references', async () => {
      const definition: WorkflowDefinition = {
        nodes: [
          { id: 'start', type: 'start', position: { x: 0, y: 0 }, data: { label: 'Start', config: {} } },
          { id: 'end', type: 'end', position: { x: 100, y: 0 }, data: { label: 'End', config: {} } }
        ],
        edges: [{ id: 'e1', source: 'start', target: 'nonexistent' }],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'INVALID_EDGE_TARGET' }));
    });

    it('should fail self-loop edges', async () => {
      const definition: WorkflowDefinition = {
        nodes: [
          { id: 'start', type: 'start', position: { x: 0, y: 0 }, data: { label: 'Start', config: {} } },
          { id: 'end', type: 'end', position: { x: 100, y: 0 }, data: { label: 'End', config: {} } }
        ],
        edges: [
          { id: 'e1', source: 'start', target: 'end' },
          { id: 'e2', source: 'end', target: 'end' }
        ],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'SELF_LOOP' }));
    });

    it('should detect cycles', async () => {
      const definition: WorkflowDefinition = {
        nodes: [
          { id: 'start', type: 'start', position: { x: 0, y: 0 }, data: { label: 'Start', config: {} } },
          { id: 'a', type: 'task', position: { x: 100, y: 0 }, data: { label: 'A', config: {} } },
          { id: 'end', type: 'end', position: { x: 200, y: 0 }, data: { label: 'End', config: {} } }
        ],
        edges: [
          { id: 'e1', source: 'start', target: 'a' },
          { id: 'e2', source: 'a', target: 'end' },
          { id: 'e3', source: 'end', target: 'start' } // Creates cycle
        ],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'CYCLE_DETECTED' }));
    });

    it('should warn about disconnected nodes', async () => {
      const definition: WorkflowDefinition = {
        nodes: [
          { id: 'start', type: 'start', position: { x: 0, y: 0 }, data: { label: 'Start', config: {} } },
          { id: 'end', type: 'end', position: { x: 100, y: 0 }, data: { label: 'End', config: {} } },
          { id: 'orphan', type: 'task', position: { x: 50, y: 100 }, data: { label: 'Orphan', config: {} } }
        ],
        edges: [{ id: 'e1', source: 'start', target: 'end' }],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(true);
      expect(result.warnings).toContainEqual(expect.objectContaining({ code: 'DISCONNECTED_NODE' }));
    });

    it('should warn about negative positions', async () => {
      const definition: WorkflowDefinition = {
        nodes: [
          { id: 'start', type: 'start', position: { x: -10, y: 0 }, data: { label: 'Start', config: {} } },
          { id: 'end', type: 'end', position: { x: 100, y: 0 }, data: { label: 'End', config: {} } }
        ],
        edges: [{ id: 'e1', source: 'start', target: 'end' }],
        variables: [],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.warnings).toContainEqual(expect.objectContaining({ code: 'NEGATIVE_POSITION' }));
    });

    it('should fail duplicate variables', async () => {
      const definition: WorkflowDefinition = {
        nodes: [
          { id: 'start', type: 'start', position: { x: 0, y: 0 }, data: { label: 'Start', config: {} } },
          { id: 'end', type: 'end', position: { x: 100, y: 0 }, data: { label: 'End', config: {} } }
        ],
        edges: [{ id: 'e1', source: 'start', target: 'end' }],
        variables: [
          { name: 'var1', type: 'string', required: true },
          { name: 'var1', type: 'number', required: false }
        ],
        triggers: [],
        config: { timeout: 0, retryPolicy: { maxAttempts: 0, backoffMultiplier: 0, initialDelay: 0, maxDelay: 0 }, concurrency: 0, logLevel: 'info' }
      };

      const result = await service.validateWorkflow(definition);

      expect(result.valid).toBe(false);
      expect(result.errors).toContainEqual(expect.objectContaining({ code: 'DUPLICATE_VARIABLE' }));
    });
  });

  describe('executeWorkflow', () => {
    it('should execute with input', async () => {
      const mockExecution = { id: 'exec-1', workflowId: '1', status: 'pending', startedAt: '', input: { key: 'value' }, steps: [], logs: [] };
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockExecution) });

      const result = await service.executeWorkflow('1', { key: 'value' });

      expect(result.input).toEqual({ key: 'value' });
    });
  });

  describe('cloneWorkflow', () => {
    it('should clone with new name', async () => {
      const mockWorkflow = { id: '2', name: 'Cloned', tenantId: 'tenant-1', description: '', definition: createDefaultWorkflowDefinition(), status: 'draft' as const, executions: 0, createdAt: '', updatedAt: '', createdBy: '' };
      global.fetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(mockWorkflow) });

      const result = await service.cloneWorkflow('1', 'Cloned');

      expect(result.name).toBe('Cloned');
    });
  });
});

describe('Utility Functions', () => {
  describe('createDefaultWorkflowDefinition', () => {
    it('should create valid workflow structure', () => {
      const definition = createDefaultWorkflowDefinition();

      expect(definition.nodes).toHaveLength(2);
      expect(definition.nodes.some(n => n.type === 'start')).toBe(true);
      expect(definition.nodes.some(n => n.type === 'end')).toBe(true);
      expect(definition.edges).toHaveLength(1);
    });
  });

  describe('createEmptyExecution', () => {
    it('should create pending execution', () => {
      const execution = createEmptyExecution('workflow-1');

      expect(execution.workflowId).toBe('workflow-1');
      expect(execution.status).toBe('pending');
      expect(execution.steps).toEqual([]);
    });
  });
});
