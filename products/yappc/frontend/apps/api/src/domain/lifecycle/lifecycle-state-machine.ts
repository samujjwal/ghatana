/**
 * Lifecycle State Machine
 *
 * Implements the lifecycle phase transition logic with:
 * - Allowed transitions
 * - Approval requirements
 * - Evidence requirements
 * - Audit emission
 *
 * @doc.type module
 * @doc.purpose Lifecycle phase state machine
 * @doc.layer domain
 * @doc.pattern State Machine
 */

import {
  type LifecyclePhaseId,
  LIFECYCLE_PHASE_ORDER,
  LIFECYCLE_PHASES_BY_ID,
  getPhaseMetadata,
  isValidPhaseTransition,
} from './lifecycle-taxonomy';

// ============================================================================
// Types
// ============================================================================

/**
 * Transition request with context
 */
export interface TransitionRequest {
  projectId: string;
  fromPhase: LifecyclePhaseId;
  toPhase: LifecyclePhaseId;
  requestedBy: string;
  requestedByRole: string;
  evidence?: Evidence[];
  force?: boolean; // Admin override
  reason?: string;
}

/**
 * Evidence for transition approval
 */
export interface Evidence {
  id: string;
  type: 'document' | 'test_result' | 'approval' | 'review' | 'artifact';
  title: string;
  description?: string;
  url?: string;
  createdBy: string;
  createdAt: Date;
}

/**
 * Approval record
 */
export interface Approval {
  id: string;
  transitionId: string;
  approvedBy: string;
  approvedByRole: string;
  approvedAt: Date;
  comment?: string;
  signature?: string; // Digital signature/hash
}

/**
 * Transition record
 */
export interface Transition {
  id: string;
  projectId: string;
  fromPhase: LifecyclePhaseId;
  toPhase: LifecyclePhaseId;
  requestedBy: string;
  requestedAt: Date;
  status: 'pending' | 'approved' | 'rejected' | 'completed' | 'cancelled';
  evidence: Evidence[];
  approvals: Approval[];
  completedAt?: Date;
  rejectedAt?: Date;
  rejectedReason?: string;
  auditEventId?: string;
}

/**
 * Transition validation result
 */
export interface TransitionValidation {
  valid: boolean;
  canAutoApprove: boolean;
  requiresApproval: boolean;
  requiredRoles: string[];
  minimumEvidence: number;
  currentEvidence: number;
  missingEvidence: string[];
  blockers: string[];
  warnings: string[];
}

/**
 * State machine configuration
 */
export interface StateMachineConfig {
  autoApproveLowRisk: boolean;
  requireApprovalFor: LifecyclePhaseId[];
  evidenceRequiredFor: LifecyclePhaseId[];
  aiAssistEnabled: boolean;
  auditAllTransitions: boolean;
}

// ============================================================================
// Default Configuration
// ============================================================================

const DEFAULT_CONFIG: StateMachineConfig = {
  autoApproveLowRisk: true,
  requireApprovalFor: ['CONTEXT', 'PLAN', 'VERIFY', 'INSTITUTIONALIZE'],
  evidenceRequiredFor: ['PLAN', 'VERIFY', 'INSTITUTIONALIZE'],
  aiAssistEnabled: true,
  auditAllTransitions: true,
};

// ============================================================================
// Validation Functions
// ============================================================================

/**
 * Validate if a transition is allowed
 */
export function validateTransition(
  request: TransitionRequest,
  config: StateMachineConfig = DEFAULT_CONFIG
): TransitionValidation {
  const { fromPhase, toPhase, evidence = [], force } = request;
  const blockers: string[] = [];
  const warnings: string[] = [];

  // Check if transition is valid (sequential or backward)
  if (!isValidPhaseTransition(fromPhase, toPhase)) {
    blockers.push(
      `Invalid transition: ${fromPhase} → ${toPhase}. Can only move forward one step or backward any amount.`
    );
    return {
      valid: false,
      canAutoApprove: false,
      requiresApproval: true,
      requiredRoles: [],
      minimumEvidence: 0,
      currentEvidence: 0,
      missingEvidence: [],
      blockers,
      warnings,
    };
  }

  // Get phase metadata
  const fromMetadata = getPhaseMetadata(fromPhase);
  const toMetadata = getPhaseMetadata(toPhase);

  if (!fromMetadata || !toMetadata) {
    blockers.push('Invalid phase metadata');
    return {
      valid: false,
      canAutoApprove: false,
      requiresApproval: true,
      requiredRoles: [],
      minimumEvidence: 0,
      currentEvidence: 0,
      missingEvidence: [],
      blockers,
      warnings,
    };
  }

  // Check exit requirements for current phase
  const exitRequirements = fromMetadata.exitRequirements;

  // Admin force override
  if (force && request.requestedByRole === 'ADMIN') {
    return {
      valid: true,
      canAutoApprove: true,
      requiresApproval: false,
      requiredRoles: [],
      minimumEvidence: 0,
      currentEvidence: evidence.length,
      missingEvidence: [],
      blockers: [],
      warnings: ['Transition forced by admin override'],
    };
  }

  // Check if approval is required
  const requiresApproval =
    exitRequirements.requiresApproval ||
    config.requireApprovalFor.includes(toPhase);

  // Check evidence requirements
  const minimumEvidence = exitRequirements.minimumEvidenceCount;
  const currentEvidence = evidence.length;
  const missingEvidence: string[] = [];

  if (currentEvidence < minimumEvidence) {
    missingEvidence.push(
      `Need ${minimumEvidence - currentEvidence} more evidence item(s)`
    );
  }

  // Check required roles
  const requiredRoles = exitRequirements.approverRoles;

  // Validate specific phase requirements
  if (toPhase === 'INSTITUTIONALIZE' && currentEvidence < 3) {
    blockers.push('Institutionalize phase requires at least 3 evidence items');
  }

  // Can auto-approve?
  const canAutoApprove =
    !requiresApproval &&
    blockers.length === 0 &&
    currentEvidence >= minimumEvidence &&
    config.autoApproveLowRisk;

  // Warnings
  if (toPhase === 'EXECUTE' && currentEvidence === 0) {
    warnings.push('No test plan evidence provided');
  }

  return {
    valid: blockers.length === 0,
    canAutoApprove,
    requiresApproval,
    requiredRoles,
    minimumEvidence,
    currentEvidence,
    missingEvidence,
    blockers,
    warnings,
  };
}

// ============================================================================
// Transition Execution
// ============================================================================

/**
 * Create a new transition request
 */
export function createTransition(
  request: TransitionRequest,
  config: StateMachineConfig = DEFAULT_CONFIG
): { transition: Transition; validation: TransitionValidation } {
  const validation = validateTransition(request, config);

  const transition: Transition = {
    id: `trans-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    projectId: request.projectId,
    fromPhase: request.fromPhase,
    toPhase: request.toPhase,
    requestedBy: request.requestedBy,
    requestedAt: new Date(),
    status: validation.canAutoApprove ? 'completed' : 'pending',
    evidence: request.evidence || [],
    approvals: [],
  };

  // Auto-complete if valid and auto-approvable
  if (validation.canAutoApprove && validation.valid) {
    transition.status = 'completed';
    transition.completedAt = new Date();
  }

  return { transition, validation };
}

/**
 * Approve a pending transition
 */
export function approveTransition(
  transition: Transition,
  approval: Omit<Approval, 'id' | 'transitionId'>,
  config: StateMachineConfig = DEFAULT_CONFIG
): { success: boolean; transition: Transition; error?: string } {
  if (transition.status !== 'pending') {
    return {
      success: false,
      transition,
      error: `Cannot approve transition with status: ${transition.status}`,
    };
  }

  const newApproval: Approval = {
    id: `appr-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    transitionId: transition.id,
    ...approval,
  };

  transition.approvals.push(newApproval);

  // Check if enough approvals
  const toMetadata = getPhaseMetadata(transition.toPhase);
  const requiredRoles = toMetadata?.exitRequirements.approverRoles || [];

  // Simple approval logic: at least one approval from a required role
  const hasRequiredApproval = requiredRoles.length === 0 ||
    transition.approvals.some(a => requiredRoles.includes(a.approvedByRole));

  // Check evidence
  const minimumEvidence = toMetadata?.exitRequirements.minimumEvidenceCount || 0;
  const hasEnoughEvidence = transition.evidence.length >= minimumEvidence;

  if (hasRequiredApproval && hasEnoughEvidence) {
    transition.status = 'completed';
    transition.completedAt = new Date();
  }

  return { success: true, transition };
}

/**
 * Reject a transition
 */
export function rejectTransition(
  transition: Transition,
  reason: string,
  rejectedBy: string
): { success: boolean; transition: Transition } {
  if (transition.status !== 'pending') {
    return {
      success: false,
      transition,
    };
  }

  transition.status = 'rejected';
  transition.rejectedAt = new Date();
  transition.rejectedReason = reason;

  return { success: true, transition };
}

/**
 * Cancel a pending transition
 */
export function cancelTransition(
  transition: Transition,
  cancelledBy: string
): { success: boolean; transition: Transition } {
  if (transition.status !== 'pending') {
    return {
      success: false,
      transition,
    };
  }

  transition.status = 'cancelled';

  return { success: true, transition };
}

// ============================================================================
// AI Assistance
// ============================================================================

/**
 * AI suggestion for transition
 */
export interface TransitionSuggestion {
  recommended: boolean;
  confidence: number;
  reasoning: string;
  risks: string[];
  recommendations: string[];
  suggestedEvidence: string[];
}

/**
 * Get AI suggestion for transition (mock for now)
 * In production, this would call an AI service
 */
export function getTransitionSuggestion(
  request: TransitionRequest
): TransitionSuggestion {
  const { fromPhase, toPhase, evidence = [] } = request;

  // Base confidence on evidence
  const evidenceScore = Math.min(evidence.length / 3, 1);

  // Phase-specific logic
  const risks: string[] = [];
  const recommendations: string[] = [];
  const suggestedEvidence: string[] = [];

  if (toPhase === 'VERIFY') {
    if (evidence.length === 0) {
      risks.push('No test results provided');
      suggestedEvidence.push('Test plan', 'Test results', 'Code coverage report');
    }
    recommendations.push('Ensure all acceptance criteria are met');
  }

  if (toPhase === 'INSTITUTIONALIZE') {
    if (evidence.length < 3) {
      risks.push('Insufficient evidence for institutionalization');
      suggestedEvidence.push('Adoption metrics', 'Standards documentation', 'Training materials');
    }
  }

  const confidence = 0.5 + evidenceScore * 0.5;
  const recommended = risks.length === 0 && confidence > 0.7;

  return {
    recommended,
    confidence,
    reasoning: `Based on ${evidence.length} evidence items with ${(confidence * 100).toFixed(0)}% confidence`,
    risks,
    recommendations,
    suggestedEvidence,
  };
}

// ============================================================================
// Audit Event Generation
// ============================================================================

/**
 * Generate audit event for transition
 */
export interface TransitionAuditEvent {
  action: 'PHASE_TRANSITION_REQUESTED' | 'PHASE_TRANSITION_APPROVED' | 'PHASE_TRANSITION_COMPLETED' | 'PHASE_TRANSITION_REJECTED';
  projectId: string;
  actor: string;
  actorRole: string;
  timestamp: Date;
  details: {
    fromPhase: LifecyclePhaseId;
    toPhase: LifecyclePhaseId;
    transitionId: string;
    evidenceCount: number;
    approvalCount: number;
    autoApproved?: boolean;
    forced?: boolean;
    aiSuggested?: boolean;
    aiConfidence?: number;
  };
  severity: 'info' | 'warn' | 'error';
}

/**
 * Generate audit event from transition
 */
export function generateAuditEvent(
  transition: Transition,
  eventType: TransitionAuditEvent['action'],
  aiSuggestion?: TransitionSuggestion
): TransitionAuditEvent {
  const isAutoApproved = transition.approvals.length === 0 && transition.status === 'completed';

  return {
    action: eventType,
    projectId: transition.projectId,
    actor: transition.requestedBy,
    actorRole: 'USER', // Would be looked up
    timestamp: new Date(),
    details: {
      fromPhase: transition.fromPhase,
      toPhase: transition.toPhase,
      transitionId: transition.id,
      evidenceCount: transition.evidence.length,
      approvalCount: transition.approvals.length,
      autoApproved: isAutoApproved,
      aiSuggested: !!aiSuggestion,
      aiConfidence: aiSuggestion?.confidence,
    },
    severity: isAutoApproved ? 'warn' : 'info',
  };
}
