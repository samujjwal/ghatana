/**
 * Runbook Types and Interfaces
 * 
 * Core type definitions for runbook execution, automation, and workflow management.
 */

// Type Aliases
/**
 *
 */
export type RunbookType = 'ansible' | 'terraform' | 'script' | 'manual';
/**
 *
 */
export type ExecutionStatus = 'pending' | 'running' | 'paused' | 'completed' | 'failed' | 'rolled-back';
/**
 *
 */
export type StepStatus = 'pending' | 'running' | 'success' | 'failed' | 'skipped' | 'rolled-back';
/**
 *
 */
export type ApprovalStatus = 'pending' | 'approved' | 'rejected';
/**
 *
 */
export type ResourceAction = 'create' | 'update' | 'delete' | 'no-change';

// Configuration
/**
 *
 */
export interface RunbookConfig {
  layout: 'sequential' | 'parallel' | 'dag';
  showTiming: boolean;
  showApprovals: boolean;
  enableRollback: boolean;
  highlightFailures: boolean;
  groupByStage: boolean;
  maxRetries: number;
}

// Core Entities
/**
 *
 */
export interface RunbookStep {
  id: string;
  name: string;
  type: 'task' | 'approval' | 'checkpoint' | 'notification';
  command?: string;
  module?: string;
  args?: Record<string, unknown>;
  dependsOn: string[];
  retryCount?: number;
  timeout?: number; // seconds
  rollbackStep?: string; // ID of step to execute on rollback
  condition?: string; // Expression to evaluate
  metadata: {
    status?: StepStatus;
    startTime?: Date;
    endTime?: Date;
    duration?: number; // milliseconds
    error?: string;
    output?: string;
    changes?: ResourceChange[];
    retries?: number;
  };
}

/**
 *
 */
export interface ApprovalGate {
  id: string;
  stepId: string;
  approvers: string[];
  requiredApprovals: number;
  approvals: Approval[];
  status: ApprovalStatus;
  createdAt: Date;
  resolvedAt?: Date;
  metadata: {
    resourceChanges: ResourceChange[];
    impactAnalysis: string;
    riskLevel: 'low' | 'medium' | 'high' | 'critical';
  };
}

/**
 *
 */
export interface Approval {
  approver: string;
  approved: boolean;
  timestamp: Date;
  comment?: string;
}

/**
 *
 */
export interface ResourceChange {
  resource: string;
  type: string;
  action: ResourceAction;
  before?: Record<string, unknown>;
  after?: Record<string, unknown>;
  diff?: string;
  risk: 'low' | 'medium' | 'high';
}

/**
 *
 */
export interface Runbook {
  id: string;
  name: string;
  type: RunbookType;
  description?: string;
  version: string;
  steps: RunbookStep[];
  approvalGates: ApprovalGate[];
  variables: Record<string, unknown>;
  metadata: {
    status?: ExecutionStatus;
    executionId?: string;
    startTime?: Date;
    endTime?: Date;
    duration?: number;
    totalSteps?: number;
    completedSteps?: number;
    failedSteps?: number;
    skippedSteps?: number;
    rollbackSteps?: number;
    author?: string;
    tags?: string[];
  };
}

/**
 *
 */
export interface ExecutionHistory {
  id: string;
  runbookId: string;
  runbookName: string;
  status: ExecutionStatus;
  startTime: Date;
  endTime?: Date;
  duration?: number;
  triggeredBy: string;
  steps: RunbookStep[];
  approvals: ApprovalGate[];
  variables: Record<string, unknown>;
  changes: ResourceChange[];
  error?: string;
  rollbackExecutionId?: string; // If this execution was rolled back
}
