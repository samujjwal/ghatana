/**
 * Workflow Service Interface
 * 
 * @doc.type interface
 * @doc.purpose Workflow design, execution, and management
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface WorkflowService {
  /** Get all workflows for tenant */
  getWorkflows(tenantId: string, options?: WorkflowQueryOptions): Promise<Workflow[]>;
  
  /** Get single workflow by ID */
  getWorkflow(workflowId: string, tenantId: string): Promise<Workflow | null>;
  
  /** Create new workflow */
  createWorkflow(data: CreateWorkflowRequest): Promise<Workflow>;
  
  /** Update existing workflow */
  updateWorkflow(workflowId: string, data: UpdateWorkflowRequest): Promise<Workflow>;
  
  /** Delete workflow */
  deleteWorkflow(workflowId: string, tenantId: string): Promise<void>;
  
  /** Execute workflow */
  executeWorkflow(workflowId: string, input?: Record<string, unknown>): Promise<WorkflowExecution>;
  
  /** Get workflow execution history */
  getExecutions(workflowId: string, options?: ExecutionQueryOptions): Promise<WorkflowExecution[]>;
  
  /** Validate workflow definition */
  validateWorkflow(definition: WorkflowDefinition): Promise<ValidationResult>;
  
  /** Publish workflow (version and activate) */
  publishWorkflow(workflowId: string, version: string): Promise<Workflow>;
  
  /** Clone workflow */
  cloneWorkflow(workflowId: string, newName: string): Promise<Workflow>;
  
  /** Get workflow templates */
  getTemplates(category?: string): Promise<WorkflowTemplate[]>;
}

/** Workflow entity */
export interface Workflow {
  id: string;
  tenantId: string;
  name: string;
  description: string;
  definition: WorkflowDefinition;
  status: WorkflowStatus;
  publishedVersion?: string;
  executions: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

/** Workflow status */
export type WorkflowStatus = 'draft' | 'published' | 'archived' | 'disabled';

/** Workflow definition */
export interface WorkflowDefinition {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  variables: WorkflowVariable[];
  triggers: WorkflowTrigger[];
  config: WorkflowConfig;
}

/** Workflow node */
export interface WorkflowNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: NodeData;
}

/** Node types */
export type NodeType = 
  | 'start' 
  | 'end' 
  | 'task' 
  | 'condition' 
  | 'loop' 
  | 'parallel' 
  | 'wait' 
  | 'subflow'
  | 'ai-assist';

/** Node data */
export interface NodeData {
  label: string;
  config: Record<string, unknown>;
  inputMapping?: Record<string, string>;
  outputMapping?: Record<string, string>;
}

/** Workflow edge */
export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  condition?: string;
  label?: string;
}

/** Workflow variable */
export interface WorkflowVariable {
  name: string;
  type: string;
  defaultValue?: unknown;
  required: boolean;
}

/** Workflow trigger */
export interface WorkflowTrigger {
  type: TriggerType;
  config: Record<string, unknown>;
  enabled: boolean;
}

/** Trigger types */
export type TriggerType = 
  | 'manual' 
  | 'schedule' 
  | 'webhook' 
  | 'event' 
  | 'entity-change';

/** Workflow config */
export interface WorkflowConfig {
  timeout: number;
  retryPolicy: RetryPolicy;
  concurrency: number;
  logLevel: 'debug' | 'info' | 'warn' | 'error';
}

/** Retry policy */
export interface RetryPolicy {
  maxAttempts: number;
  backoffMultiplier: number;
  initialDelay: number;
  maxDelay: number;
}

/** Workflow execution */
export interface WorkflowExecution {
  id: string;
  workflowId: string;
  status: ExecutionStatus;
  startedAt: string;
  completedAt?: string;
  input: Record<string, unknown>;
  output?: Record<string, unknown>;
  steps: ExecutionStep[];
  logs: ExecutionLog[];
}

/** Execution status */
export type ExecutionStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';

/** Execution step */
export interface ExecutionStep {
  id: string;
  nodeId: string;
  nodeType: NodeType;
  status: ExecutionStatus;
  startedAt: string;
  completedAt?: string;
  input: Record<string, unknown>;
  output?: Record<string, unknown>;
  error?: ExecutionError;
}

/** Execution error */
export interface ExecutionError {
  code: string;
  message: string;
  stack?: string;
}

/** Execution log */
export interface ExecutionLog {
  timestamp: string;
  level: 'debug' | 'info' | 'warn' | 'error';
  message: string;
  nodeId?: string;
  metadata?: Record<string, unknown>;
}

/** Query options */
export interface WorkflowQueryOptions {
  search?: string;
  status?: WorkflowStatus;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  page?: number;
  limit?: number;
}

/** Execution query options */
export interface ExecutionQueryOptions {
  status?: ExecutionStatus;
  startDate?: string;
  endDate?: string;
  page?: number;
  limit?: number;
}

/** Create request */
export interface CreateWorkflowRequest {
  tenantId: string;
  name: string;
  description?: string;
  definition: WorkflowDefinition;
}

/** Update request */
export interface UpdateWorkflowRequest {
  name?: string;
  description?: string;
  definition?: Partial<WorkflowDefinition>;
  status?: WorkflowStatus;
}

/** Validation result */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

/** Validation error */
export interface ValidationError {
  code: string;
  message: string;
  nodeId?: string;
}

/** Validation warning */
export interface ValidationWarning {
  code: string;
  message: string;
  nodeId?: string;
}

/** Workflow template */
export interface WorkflowTemplate {
  id: string;
  name: string;
  description: string;
  category: string;
  definition: WorkflowDefinition;
  tags: string[];
}
