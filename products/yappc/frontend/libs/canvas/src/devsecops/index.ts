/**
 * DevSecOps Runbook Module
 * 
 * Production-grade runbook execution and automation system.
 * 
 * @module @ghatana/yappc-canvas/devsecops
 * 
 * @example
 * ```typescript
 * import { 
 *   parseAnsiblePlaybook, 
 *   parseTerraformPlan,
 *   runbookToCanvas,
 *   executeStep,
 *   completeStep,
 *   requestApproval,
 *   processApproval
 * } from '@ghatana/yappc-canvas/devsecops';
 * 
 * // Parse a playbook
 * const runbook = parseAnsiblePlaybook(yamlContent);
 * 
 * // Convert to canvas for visualization
 * const canvas = runbookToCanvas(runbook, config);
 * 
 * // Execute with approvals
 * const withApproval = requestApproval(runbook, stepId, approvers, 2);
 * const approved = processApproval(withApproval, approvalId, user, true);
 * ```
 */

// Types
export type {
  Runbook,
  RunbookStep,
  RunbookConfig,
  RunbookType,
  ExecutionStatus,
  StepStatus,
  ApprovalStatus,
  ResourceAction,
  ApprovalGate,
  Approval,
  ResourceChange,
  ExecutionHistory,
} from './types';

// Parsers
export { parseAnsiblePlaybook } from './parsers/ansible';
export { parseTerraformPlan } from './parsers/terraform';

// Canvas Conversion
export { runbookToCanvas } from './canvas-converter';

// Execution & Lifecycle
export {
  executeStep,
  completeStep,
  rollbackRunbook,
  getExecutionHistory,
} from './execution';

// Approval Workflow
export {
  requestApproval,
  processApproval,
  areApprovalsComplete,
  getPendingApprovals,
} from './approval';

// Helpers & Utilities
export {
  createRunbookConfig,
  mapTerraformAction,
  calculateResourceRisk,
  calculateStepRisk,
  calculateStepPositions,
  getStepStyle,
  analyzeRunbook,
} from './helpers';
