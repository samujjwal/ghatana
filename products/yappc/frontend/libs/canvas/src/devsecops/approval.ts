/**
 * Approval Workflow Management
 * 
 * Functions for managing approval gates, processing approvals, and tracking approval status.
 */

import type { Runbook, ApprovalGate, ResourceChange } from './types';

/**
 * Request approval for a runbook step
 * 
 * Creates an approval gate that must be satisfied before the step can proceed.
 * 
 * @param runbook - The runbook containing the step
 * @param stepId - ID of the step requiring approval
 * @param approvers - List of user IDs who can approve
 * @param requiredApprovals - Number of approvals required (default: 1)
 * @returns Updated runbook with approval gate added
 * 
 * @example
 * ```typescript
 * const withApproval = requestApproval(
 *   runbook,
 *   'step-1',
 *   ['ops-team', 'security-team'],
 *   2
 * );
 * ```
 */
export function requestApproval(
  runbook: Runbook,
  stepId: string,
  approvers: string[],
  requiredApprovals: number = 1
): Runbook {
  const step = runbook.steps.find(s => s.id === stepId);
  
  if (!step) {
    throw new Error(`Step ${stepId} not found`);
  }
  
  // Analyze changes for impact
  const changes = step.metadata.changes || [];
  const hasHighRisk = changes.some(c => c.risk === 'high');
  const hasDelete = changes.some(c => c.action === 'delete');
  
  const riskLevel: 'low' | 'medium' | 'high' | 'critical' = 
    hasHighRisk && hasDelete ? 'critical' :
    hasHighRisk ? 'high' :
    hasDelete ? 'high' :
    changes.some(c => c.risk === 'medium') ? 'medium' : 'low';
  
  const approvalGate: ApprovalGate = {
    id: `approval-${stepId}-${Date.now()}`,
    stepId,
    approvers,
    requiredApprovals,
    approvals: [],
    status: 'pending',
    createdAt: new Date(),
    metadata: {
      resourceChanges: changes,
      impactAnalysis: `Step "${step.name}" requires approval`,
      riskLevel,
    },
  };
  
  return {
    ...runbook,
    approvalGates: [...runbook.approvalGates, approvalGate],
  };
}

/**
 * Process an approval for an approval gate
 * 
 * Records an approval or rejection and updates the gate status if threshold is met.
 * 
 * @param runbook - The runbook containing the approval gate
 * @param approvalId - ID of the approval gate
 * @param approver - User ID of the approver
 * @param approved - Whether the approval is granted or rejected
 * @param comment - Optional comment from approver
 * @returns Updated runbook with approval recorded
 * 
 * @example
 * ```typescript
 * const approved = processApproval(
 *   runbook,
 *   'approval-step-1-123',
 *   'user@example.com',
 *   true,
 *   'Looks good to proceed'
 * );
 * ```
 */
export function processApproval(
  runbook: Runbook,
  approvalId: string,
  approver: string,
  approved: boolean,
  comment?: string
): Runbook {
  const gateIndex = runbook.approvalGates.findIndex(g => g.id === approvalId);
  
  if (gateIndex === -1) {
    throw new Error(`Approval gate ${approvalId} not found`);
  }
  
  const gate = runbook.approvalGates[gateIndex];
  
  // Check if approver is authorized
  if (!gate.approvers.includes(approver)) {
    throw new Error(`${approver} is not authorized to approve this gate`);
  }
  
  // Check if already approved/rejected by this approver
  if (gate.approvals.some(a => a.approver === approver)) {
    throw new Error(`${approver} has already provided approval for this gate`);
  }
  
  // Add approval
  const newApproval = {
    approver,
    approved,
    timestamp: new Date(),
    comment,
  };
  
  const updatedApprovals = [...gate.approvals, newApproval];
  
  // Determine gate status
  let newStatus = gate.status;
  const approvalCount = updatedApprovals.filter(a => a.approved).length;
  const rejectionCount = updatedApprovals.filter(a => !a.approved).length;
  
  if (rejectionCount > 0) {
    newStatus = 'rejected';
  } else if (approvalCount >= gate.requiredApprovals) {
    newStatus = 'approved';
  }
  
  const updatedGate: ApprovalGate = {
    ...gate,
    approvals: updatedApprovals,
    status: newStatus,
    resolvedAt: newStatus !== 'pending' ? new Date() : undefined,
  };
  
  const updatedGates = [...runbook.approvalGates];
  updatedGates[gateIndex] = updatedGate;
  
  return {
    ...runbook,
    approvalGates: updatedGates,
  };
}

/**
 * Check if all approval gates for a step are satisfied
 * 
 * @param runbook - The runbook to check
 * @param stepId - ID of the step to check
 * @returns True if all gates are approved, false otherwise
 */
export function areApprovalsComplete(runbook: Runbook, stepId: string): boolean {
  const gates = runbook.approvalGates.filter(g => g.stepId === stepId);
  
  if (gates.length === 0) {
    return true; // No gates required
  }
  
  return gates.every(g => g.status === 'approved');
}

/**
 * Get pending approvals for a user
 * 
 * @param runbook - The runbook to check
 * @param userId - User ID to check for pending approvals
 * @returns Array of approval gates pending this user's approval
 */
export function getPendingApprovals(runbook: Runbook, userId: string): ApprovalGate[] {
  return runbook.approvalGates.filter(gate => 
    gate.status === 'pending' &&
    gate.approvers.includes(userId) &&
    !gate.approvals.some(a => a.approver === userId)
  );
}
