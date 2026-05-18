/**
 * Type definitions for Data Cloud UI mock data.
 *
 * Extracted from mock-data.ts (P7-3a) so types can be imported without
 * pulling in the large mock data payloads.
 *
 * @doc.type module
 * @doc.purpose Mock data type definitions
 * @doc.layer frontend
 */

export interface MockCollection {
  id: string;
  name: string;
  description: string;
  entityCount: number;
  schema: MockSchema;
  createdAt: string;
  updatedAt: string;
  isActive: boolean;
}

export interface MockSchema {
  id: string;
  name: string;
  fields: MockField[];
  constraints: MockConstraint[];
}

export interface MockField {
  id: string;
  name: string;
  type: "string" | "number" | "boolean" | "date" | "email" | "url" | "text";
  required: boolean;
  maxLength?: number;
  minLength?: number;
  pattern?: string;
  enum?: string[];
  description?: string;
}

export interface MockConstraint {
  id: string;
  name: string;
  type: "unique" | "foreign_key" | "check" | "not_null";
  fields: string[];
  description?: string;
}

export interface MockEntity {
  id: string;
  collectionId: string;
  data: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface MockWorkflow {
  id: string;
  name: string;
  description: string;
  nodes: MockWorkflowNode[];
  edges: MockWorkflowEdge[];
  triggers: MockTrigger[];
  status: "draft" | "active" | "paused" | "archived";
  createdAt: string;
  updatedAt: string;
  executionCount: number;
  lastExecutedAt?: string;
}

export interface MockWorkflowNode {
  id: string;
  type:
    | "start"
    | "end"
    | "query"
    | "transform"
    | "decision"
    | "approval"
    | "api_call"
    | "notification";
  label: string;
  data: Record<string, unknown>;
  position: { x: number; y: number };
  status?: "idle" | "running" | "completed" | "failed";
}

export interface MockWorkflowEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  condition?: string;
}

export interface MockTrigger {
  id: string;
  type: "event" | "schedule" | "webhook" | "manual" | "form";
  name: string;
  config: Record<string, unknown>;
  isActive: boolean;
}

export interface MockExecution {
  id: string;
  workflowId: string;
  status: "completed" | "failed" | "cancelled" | "running" | "pending";
  nodeStatuses: Record<string, "idle" | "running" | "completed" | "failed">;
  startedAt: string;
  completedAt?: string;
  duration?: number;
  logs: MockExecutionLog[];
  error?: string;
}

export interface MockExecutionLog {
  timestamp: string;
  nodeId: string;
  message: string;
  level: "info" | "warn" | "error";
  data?: Record<string, unknown>;
}
