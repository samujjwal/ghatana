/**
 * Workflow Service Implementation
 * 
 * @doc.type class
 * @doc.purpose Concrete implementation of workflow service with validation
 * @doc.layer ui
 * @doc.pattern Service Implementation
 */

import type { 
  WorkflowService, Workflow, CreateWorkflowRequest, UpdateWorkflowRequest, 
  WorkflowDefinition, ValidationResult, WorkflowExecution, WorkflowQueryOptions,
  ExecutionQueryOptions, WorkflowTemplate, WorkflowStatus, ExecutionStatus,
  WorkflowNode, WorkflowEdge
} from './workflows';

export class WorkflowServiceImpl implements WorkflowService {
  private baseUrl: string;

  constructor(baseUrl = '/api/v1') {
    this.baseUrl = baseUrl;
  }

  async getWorkflows(tenantId: string, options?: WorkflowQueryOptions): Promise<Workflow[]> {
    const params = new URLSearchParams();
    if (options?.search) params.set('search', options.search);
    if (options?.status) params.set('status', options.status);
    if (options?.sortBy) params.set('sortBy', options.sortBy);
    if (options?.sortOrder) params.set('sortOrder', options.sortOrder);
    if (options?.page) params.set('page', String(options.page));
    if (options?.limit) params.set('limit', String(options.limit));

    const response = await fetch(`${this.baseUrl}/tenants/${tenantId}/workflows?${params}`);
    if (!response.ok) throw new Error(`Failed to fetch workflows: ${response.status}`);
    return response.json();
  }

  async getWorkflow(workflowId: string, tenantId: string): Promise<Workflow | null> {
    const response = await fetch(`${this.baseUrl}/tenants/${tenantId}/workflows/${workflowId}`);
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to fetch workflow: ${response.status}`);
    return response.json();
  }

  async createWorkflow(data: CreateWorkflowRequest): Promise<Workflow> {
    // Validate before creating
    const validation = await this.validateWorkflow(data.definition);
    if (!validation.valid) {
      throw new Error(`Validation failed: ${validation.errors.map(e => e.message).join(', ')}`);
    }

    const response = await fetch(`${this.baseUrl}/tenants/${data.tenantId}/workflows`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    
    if (!response.ok) throw new Error(`Failed to create workflow: ${response.status}`);
    return response.json();
  }

  async updateWorkflow(workflowId: string, data: UpdateWorkflowRequest): Promise<Workflow> {
    const response = await fetch(`${this.baseUrl}/workflows/${workflowId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    
    if (!response.ok) throw new Error(`Failed to update workflow: ${response.status}`);
    return response.json();
  }

  async deleteWorkflow(workflowId: string, tenantId: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/tenants/${tenantId}/workflows/${workflowId}`, {
      method: 'DELETE'
    });
    
    if (!response.ok && response.status !== 404) {
      throw new Error(`Failed to delete workflow: ${response.status}`);
    }
  }

  async executeWorkflow(workflowId: string, input?: Record<string, unknown>): Promise<WorkflowExecution> {
    const response = await fetch(`${this.baseUrl}/workflows/${workflowId}/execute`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ input: input || {} })
    });
    
    if (!response.ok) throw new Error(`Failed to execute workflow: ${response.status}`);
    return response.json();
  }

  async getExecutions(workflowId: string, options?: ExecutionQueryOptions): Promise<WorkflowExecution[]> {
    const params = new URLSearchParams();
    if (options?.status) params.set('status', options.status);
    if (options?.startDate) params.set('startDate', options.startDate);
    if (options?.endDate) params.set('endDate', options.endDate);
    if (options?.page) params.set('page', String(options.page));
    if (options?.limit) params.set('limit', String(options.limit));

    const response = await fetch(`${this.baseUrl}/workflows/${workflowId}/executions?${params}`);
    if (!response.ok) throw new Error(`Failed to fetch executions: ${response.status}`);
    return response.json();
  }

  async validateWorkflow(definition: WorkflowDefinition): Promise<ValidationResult> {
    const errors: ValidationResult['errors'] = [];
    const warnings: ValidationResult['warnings'] = [];

    // Check for nodes
    if (!definition.nodes || definition.nodes.length === 0) {
      errors.push({ code: 'NO_NODES', message: 'Workflow must have at least one node' });
      return { valid: false, errors, warnings };
    }

    // Check for start node
    const startNodes = definition.nodes.filter(n => n.type === 'start');
    if (startNodes.length === 0) {
      errors.push({ code: 'NO_START_NODE', message: 'Workflow must have a start node', nodeId: undefined });
    } else if (startNodes.length > 1) {
      errors.push({ code: 'MULTIPLE_START_NODES', message: 'Workflow can only have one start node' });
    }

    // Check for end node
    const endNodes = definition.nodes.filter(n => n.type === 'end');
    if (endNodes.length === 0) {
      errors.push({ code: 'NO_END_NODE', message: 'Workflow must have at least one end node' });
    }

    // Validate node IDs are unique
    const nodeIds = new Set<string>();
    for (const node of definition.nodes) {
      if (nodeIds.has(node.id)) {
        errors.push({ code: 'DUPLICATE_NODE_ID', message: `Duplicate node ID: ${node.id}`, nodeId: node.id });
      }
      nodeIds.add(node.id);

      // Validate node position
      if (node.position.x < 0 || node.position.y < 0) {
        warnings.push({ code: 'NEGATIVE_POSITION', message: `Node ${node.id} has negative position`, nodeId: node.id });
      }
    }

    // Validate edges
    if (definition.edges) {
      for (const edge of definition.edges) {
        if (!nodeIds.has(edge.source)) {
          errors.push({ code: 'INVALID_EDGE_SOURCE', message: `Edge references unknown source: ${edge.source}`, nodeId: edge.source });
        }
        if (!nodeIds.has(edge.target)) {
          errors.push({ code: 'INVALID_EDGE_TARGET', message: `Edge references unknown target: ${edge.target}`, nodeId: edge.target });
        }
        if (edge.source === edge.target) {
          errors.push({ code: 'SELF_LOOP', message: `Edge forms a self-loop on node ${edge.source}`, nodeId: edge.source });
        }
      }

      // Check for disconnected nodes
      const connectedNodes = new Set<string>();
      for (const edge of definition.edges) {
        connectedNodes.add(edge.source);
        connectedNodes.add(edge.target);
      }

      for (const node of definition.nodes) {
        if (!connectedNodes.has(node.id) && node.type !== 'start' && node.type !== 'end') {
          warnings.push({ code: 'DISCONNECTED_NODE', message: `Node ${node.id} is not connected to workflow`, nodeId: node.id });
        }
      }
    }

    // Check for cycles (simple check)
    const hasCycle = this.detectCycle(definition.nodes, definition.edges || []);
    if (hasCycle) {
      errors.push({ code: 'CYCLE_DETECTED', message: 'Workflow contains a cycle' });
    }

    // Validate variables
    if (definition.variables) {
      const varNames = new Set<string>();
      for (const v of definition.variables) {
        if (varNames.has(v.name)) {
          errors.push({ code: 'DUPLICATE_VARIABLE', message: `Duplicate variable: ${v.name}` });
        }
        varNames.add(v.name);
      }
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  async publishWorkflow(workflowId: string, version: string): Promise<Workflow> {
    const response = await fetch(`${this.baseUrl}/workflows/${workflowId}/publish`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ version })
    });
    
    if (!response.ok) throw new Error(`Failed to publish workflow: ${response.status}`);
    return response.json();
  }

  async cloneWorkflow(workflowId: string, newName: string): Promise<Workflow> {
    const response = await fetch(`${this.baseUrl}/workflows/${workflowId}/clone`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newName })
    });
    
    if (!response.ok) throw new Error(`Failed to clone workflow: ${response.status}`);
    return response.json();
  }

  async getTemplates(category?: string): Promise<WorkflowTemplate[]> {
    const params = category ? `?category=${category}` : '';
    const response = await fetch(`${this.baseUrl}/workflow-templates${params}`);
    if (!response.ok) throw new Error(`Failed to fetch templates: ${response.status}`);
    return response.json();
  }

  private detectCycle(nodes: WorkflowNode[], edges: WorkflowEdge[]): boolean {
    // Simple cycle detection using DFS
    const adj = new Map<string, string[]>();
    for (const node of nodes) {
      adj.set(node.id, []);
    }
    for (const edge of edges) {
      adj.get(edge.source)?.push(edge.target);
    }

    const visited = new Set<string>();
    const recStack = new Set<string>();

    const dfs = (nodeId: string): boolean => {
      visited.add(nodeId);
      recStack.add(nodeId);

      for (const neighbor of adj.get(nodeId) || []) {
        if (!visited.has(neighbor)) {
          if (dfs(neighbor)) return true;
        } else if (recStack.has(neighbor)) {
          return true;
        }
      }

      recStack.delete(nodeId);
      return false;
    };

    for (const node of nodes) {
      if (!visited.has(node.id)) {
        if (dfs(node.id)) return true;
      }
    }

    return false;
  }
}

// Utility functions
export function createDefaultWorkflowDefinition(): WorkflowDefinition {
  const startNode: WorkflowNode = {
    id: 'start',
    type: 'start',
    position: { x: 100, y: 100 },
    data: { label: 'Start', config: {} }
  };

  const endNode: WorkflowNode = {
    id: 'end',
    type: 'end',
    position: { x: 400, y: 100 },
    data: { label: 'End', config: {} }
  };

  return {
    nodes: [startNode, endNode],
    edges: [{ id: 'e1', source: 'start', target: 'end' }],
    variables: [],
    triggers: [{ type: 'manual', config: {}, enabled: true }],
    config: {
      timeout: 300000,
      retryPolicy: { maxAttempts: 3, backoffMultiplier: 2, initialDelay: 1000, maxDelay: 60000 },
      concurrency: 1,
      logLevel: 'info'
    }
  };
}

export function createEmptyExecution(workflowId: string): WorkflowExecution {
  return {
    id: `exec-${Date.now()}`,
    workflowId,
    status: 'pending' as ExecutionStatus,
    startedAt: new Date().toISOString(),
    input: {},
    steps: [],
    logs: []
  };
}
