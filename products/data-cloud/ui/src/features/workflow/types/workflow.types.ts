/**
 * Workflow domain types and interfaces.
 *
 * <p><b>Purpose</b><br>
 * Defines all TypeScript types for workflow definitions, nodes, edges, and execution.
 * Provides type-safe contracts for API communication and state management.
 *
 * <p><b>Architecture</b><br>
 * - Workflow definition types
 * - Node and edge types
 * - Execution status types
 * - API request/response types
 *
 * @doc.type types
 * @doc.purpose Workflow domain types
 * @doc.layer frontend
 * @doc.pattern Domain Types
 */

/**
 * Node type enumeration.
 *
 * @doc.type enum
 */
export enum NodeType {
  START = 'START',
  END = 'END',
  API_CALL = 'API_CALL',
  DECISION = 'DECISION',
  APPROVAL = 'APPROVAL',
  TRANSFORM = 'TRANSFORM',
  QUERY = 'QUERY',
  LOOP = 'LOOP',
  WEBHOOK = 'WEBHOOK',
  FORM = 'FORM',
}

type NodeTypeLowercase =
  | 'start'
  | 'end'
  | 'apiCall'
  | 'decision'
  | 'approval'
  | 'transform'
  | 'query'
  | 'loop'
  | 'webhook'
  | 'form';

type NodeTypeUppercase =
  | 'START'
  | 'END'
  | 'API_CALL'
  | 'DECISION'
  | 'APPROVAL'
  | 'TRANSFORM'
  | 'QUERY'
  | 'LOOP'
  | 'WEBHOOK'
  | 'FORM';

export type NodeTypeValue = NodeType | NodeTypeLowercase | NodeTypeUppercase;

/**
 * Node execution state.
 *
 * @doc.type enum
 */
export enum NodeState {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED',
}

type NodeStateLowercase = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';
type NodeStateUppercase = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';

export type NodeStateValue = NodeState | NodeStateLowercase | NodeStateUppercase;

/**
 * Workflow execution status.
 *
 * @doc.type enum
 */
export enum ExecutionStatus {
  PENDING = 'PENDING',
  PUBLISHED = 'PUBLISHED',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
}

type ExecutionStatusLowercase = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
type ExecutionStatusUppercase = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type ExecutionStatusValue =
  | ExecutionStatus
  | ExecutionStatusLowercase
  | ExecutionStatusUppercase;

/**
 * Workflow edge type enumeration.
 *
 * @doc.type enum
 */
export enum EdgeType {
  DEFAULT = 'DEFAULT',
  CONDITIONAL = 'CONDITIONAL',
  PARALLEL = 'PARALLEL',
  LOOP = 'LOOP',
  ERROR = 'ERROR',
}

type EdgeTypeLowercase =
  | 'default'
  | 'conditional'
  | 'parallel'
  | 'loop'
  | 'error';

type EdgeTypeUppercase =
  | 'DEFAULT'
  | 'CONDITIONAL'
  | 'PARALLEL'
  | 'LOOP'
  | 'ERROR';

export type EdgeTypeValue = EdgeType | EdgeTypeLowercase | EdgeTypeUppercase;

/**
 * Workflow trigger type enumeration.
 *
 * @doc.type enum
 */
export enum TriggerType {
  MANUAL = 'MANUAL',
  SCHEDULED = 'SCHEDULED',
  WEBHOOK = 'WEBHOOK',
}

type TriggerTypeLowercase = 'manual' | 'scheduled' | 'webhook';
type TriggerTypeUppercase = 'MANUAL' | 'SCHEDULED' | 'WEBHOOK';

export type TriggerTypeValue = TriggerType | TriggerTypeLowercase | TriggerTypeUppercase;

/**
 * Workflow trigger configuration.
 *
 * @doc.type interface
 */
export interface WorkflowTrigger {
  id: string;
  type: TriggerTypeValue;
  config?: Record<string, unknown>;
  active?: boolean;
  schedule?: string;
}

/**
 * Base node data interface.
 *
 * @doc.type interface
 */
export interface BaseNodeData {
  label?: string;
  description?: string;
  [key: string]: unknown;
}

/**
 * API call node data.
 *
 * @doc.type interface
 */
export interface ApiCallNodeData extends BaseNodeData {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  headers?: Record<string, string>;
  body?: string;
  authentication?: {
    type: 'none' | 'basic' | 'bearer' | 'oauth2';
    credentials?: string;
  };
}

/**
 * Decision node data.
 *
 * @doc.type interface
 */
export interface DecisionNodeData extends BaseNodeData {
  conditions: Array<{
    id: string;
    label: string;
    expression: string;
  }>;
  defaultBranch?: string;
}

/**
 * Approval node data.
 *
 * @doc.type interface
 */
export interface ApprovalNodeData extends BaseNodeData {
  approvers: string[];
  timeout?: number;
  autoApprove?: boolean;
}

/**
 * Transform node data.
 *
 * @doc.type interface
 */
export interface TransformNodeData extends BaseNodeData {
  mapping: Record<string, string>;
  scriptLanguage?: 'javascript' | 'jq' | 'jsonpath';
  script?: string;
}

/**
 * Query node data.
 *
 * @doc.type interface
 */
export interface QueryNodeData extends BaseNodeData {
  collectionId: string;
  filter?: Record<string, unknown>;
  sort?: Array<{ field: string; direction: 'asc' | 'desc' }>;
  limit?: number;
}

/**
 * Workflow node definition.
 *
 * @doc.type interface
 */
export interface WorkflowNode<T = Record<string, unknown>> {
  id: string;
  type: NodeTypeValue;
  position: { x: number; y: number };
  data: T & BaseNodeData;
  /**
   * Node display and configuration
   */
  label?: string;
  config?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  selected?: boolean;
  dragging?: boolean;
  width?: number | null;
  height?: number | null;
}

/**
 * Workflow edge definition.
 *
 * @doc.type interface
 */
export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  label?: string;
  animated?: boolean;
  type?: EdgeTypeValue;
  condition?: string;
  data?: {
    [key: string]: unknown;
  };
}

/**
 * Workflow definition.
 *
 * @doc.type interface
 */
export interface WorkflowDefinition {
  id: string;
  tenantId: string;
  collectionId: string;
  name: string;
  description?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  version: number;
  active: boolean;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  triggers: Array<string | WorkflowTrigger>;
  variables: Record<string, unknown>;
  tags?: string[];
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Node execution status.
 *
 * @doc.type interface
 */
export interface NodeExecutionStatus {
  nodeId: string;
  nodeName: string;
  state: NodeStateValue;
  startedAt?: string;
  completedAt?: string;
  duration?: number;
  error?: string;
  output?: unknown;
}

/**
 * Workflow execution result.
 *
 * @doc.type interface
 */
export interface WorkflowExecution {
  id: string;
  workflowId: string;
  status: ExecutionStatusValue;
  progress: number;
  startedAt: string;
  completedAt?: string;
  duration?: number;
  nodeStatuses: NodeExecutionStatus[];
  nodeExecutions?: NodeExecutionStatus[];
  output?: unknown;
  error?: string;
  tenantId: string;
}

export type NodeExecution = NodeExecutionStatus;

/**
 *
 * @doc.type interface
 */
export interface ExecutionUpdate {
  executionId: string;
  status?: ExecutionStatusValue;
  progress?: number;
  nodeStatus?: NodeExecutionStatus;
  error?: string;
}

/**
 * Workflow suggestion from AI.
 *
 * @doc.type interface
 */
export interface WorkflowSuggestion {
  id: string;
  name: string;
  description: string;
  collectionId: string;
  confidence: number;
  workflow: WorkflowDefinition;
}

/**
 * Workflow template.
 *
 * @doc.type interface
 */
export interface WorkflowTemplate {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  workflow: WorkflowDefinition;
  preview?: string;
}

/**
 * Validation error.
 *
 * @doc.type interface
 */
export interface ValidationError {
  id?: string;
  code: string;
  message: string;
  description?: string;
  severity?: 'error' | 'warning';
  nodeId?: string;
  edgeId?: string;
  suggestion?: string;
  suggestedFix?: {
    description: string;
    changes: Record<string, unknown>;
  };
}

export type ValidationWarning = ValidationError;

/**
 * Workflow validation result.
 *
 * @doc.type interface
 */
export interface ValidationResult {
  isValid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
  /**
   * Backwards compatibility shim - deprecated.
   */
  valid?: boolean;
}

/**
 * API request: Create workflow.
 *
 * @doc.type interface
 */
export interface CreateWorkflowRequest {
  name: string;
  description?: string;
  collectionId: string;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  tags?: string[];
}

/**
 * API request: Update workflow.
 *
 * @doc.type interface
 */
export interface UpdateWorkflowRequest {
  name?: string;
  description?: string;
  nodes?: WorkflowNode[];
  edges?: WorkflowEdge[];
  tags?: string[];
  isPublished?: boolean;
}

/**
 * API request: Execute workflow.
 *
 * @doc.type interface
 */
export interface ExecuteWorkflowRequest {
  input?: Record<string, unknown>;
  timeout?: number;
}

/**
 * API response: Workflow list.
 *
 * @doc.type interface
 */
export interface WorkflowListResponse {
  workflows: WorkflowDefinition[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * API response: Execution created.
 *
 * @doc.type interface
 */
export interface ExecutionCreatedResponse {
  executionId: string;
  workflowId: string;
  status: ExecutionStatus;
}

/**
 * API response: Suggestions.
 *
 * @doc.type interface
 */
export interface SuggestionsResponse {
  suggestions: WorkflowSuggestion[];
}

/**
 * API response: Templates.
 *
 * @doc.type interface
 */
export interface TemplatesResponse {
  templates: WorkflowTemplate[];
}

/**
 * API response: Validation.
 *
 * @doc.type interface
 */
export interface ValidateWorkflowResponse {
  result: ValidationResult;
}
