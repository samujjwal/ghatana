/**
 * @fileoverview AI visibility contracts and types for the platform.
 */

import type { CorrelationId } from '../events/base.js';

/** Autonomy levels for AI operations. */
export type AIAutonomyLevel = 'AUTONOMOUS' | 'ASSISTED' | 'SUPERVISED' | 'MANUAL';

/** All valid autonomy levels. */
export const AUTONOMY_LEVELS: readonly AIAutonomyLevel[] = [
  'AUTONOMOUS',
  'ASSISTED',
  'SUPERVISED',
  'MANUAL',
] as const;

/** Validates an autonomy level. */
export function isValidAutonomyLevel(level: string): level is AIAutonomyLevel {
  return AUTONOMY_LEVELS.includes(level as AIAutonomyLevel);
}

/** Approval states for AI operations. */
export type AIApprovalState = 'PENDING' | 'APPROVED' | 'REJECTED' | 'BYPASSED';

/** All valid approval states. */
export const APPROVAL_STATES: readonly AIApprovalState[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'BYPASSED',
] as const;

/** Validates an approval state. */
export function isValidApprovalState(state: string): state is AIApprovalState {
  return APPROVAL_STATES.includes(state as AIApprovalState);
}

/** Types of AI changes. */
export type AIChangeKind = 'insert' | 'update' | 'delete' | 'reorder' | 'suggest';

/** All valid AI change kinds. */
export const AI_CHANGE_KINDS: readonly AIChangeKind[] = [
  'insert',
  'update',
  'delete',
  'reorder',
  'suggest',
] as const;

/** Validates a change kind. */
export function isValidAIChangeKind(kind: string): kind is AIChangeKind {
  return AI_CHANGE_KINDS.includes(kind as AIChangeKind);
}

/** Descriptor for a single AI-suggested change. */
export interface AIChangeDescriptor {
  readonly region: string; // component ID, token name, node ID, etc.
  readonly summary: string; // human-readable description
  readonly kind: AIChangeKind;
  readonly diff?: string; // structured diff for code regions
}

/** Full visibility contract for AI operations.
 * This is the canonical type that all AI operations must surface to users.
 */
export interface AIVisibilityContract {
  readonly operationState: 'idle' | 'running' | 'completed' | 'failed';
  readonly operationLabel: string; // human-readable: "Applying layout suggestion"
  readonly suggestedChanges: readonly AIChangeDescriptor[];
  readonly appliedChanges: readonly AIChangeDescriptor[];
  readonly pendingChanges: readonly AIChangeDescriptor[]; // applied but awaiting review
  readonly confidenceBand: { readonly low: number; readonly high: number };
  readonly rationale: string; // why the AI acted
  readonly evidence: readonly string[]; // provenance refs / citations
  readonly approvalState: AIApprovalState;
  readonly reviewRequired: boolean; // policy-gated
  readonly rollbackAvailable: boolean;
  readonly overrideAvailable: boolean;
  readonly autonomyLevel: AIAutonomyLevel;
  readonly correlationId: CorrelationId; // links to audit record
  readonly triggeredBy: 'explicit' | 'implicit'; // user-initiated or system-initiated
}

/** AI suggestion kind. */
export type AISuggestionKind =
  | 'layout'
  | 'component'
  | 'style'
  | 'accessibility'
  | 'performance'
  | 'security'
  | 'custom';

/** AI suggestion structure. */
export interface AISuggestion {
  readonly id: string;
  readonly kind: AISuggestionKind;
  readonly title: string;
  readonly description: string;
  readonly confidence: number; // 0-1
  readonly affectedRegions: readonly string[];
  readonly preview?: string; // code or visual preview
  readonly actionLabel: string; // "Apply", "View", "Configure"
  readonly visibilityContract: AIVisibilityContract;
}

/** AI operation event - extends PlatformEvent with AI-specific fields. */
export interface AIOperationEvent {
  readonly operationId: string;
  readonly operationType: string;
  readonly confidence: number;
  readonly actor: 'user' | 'ai' | 'system';
  readonly reasoning: string;
  readonly policy: AIPolicy;
  readonly affectedRegions: readonly string[];
}

/** AI policy for governing autonomous behavior. */
export interface AIPolicy {
  readonly id: string;
  readonly name: string;
  readonly autonomyThreshold: AutonomyThreshold;
  readonly approvalRequirements: readonly ApprovalRequirement[];
  readonly rollbackWindow: number; // milliseconds
  readonly dataUseConstraints: readonly string[];
}

/** Threshold for autonomous action. */
export interface AutonomyThreshold {
  readonly minConfidence: number; // 0-1
  readonly maxRiskLevel: 'low' | 'medium' | 'high' | 'critical';
  readonly allowedActions: readonly string[];
  readonly blockedActions: readonly string[];
}

/** Requirements for human approval. */
export interface ApprovalRequirement {
  readonly actionKind: string;
  readonly requiresApproval: boolean;
  readonly minReviewers: number;
  readonly requiredRoles?: readonly string[];
  readonly dataClassificationTrigger?: readonly string[];
  readonly externalVisibilityTrigger?: boolean;
}

/** Creates a default AI visibility contract. */
export function createAIVisibilityContract(
  operationLabel: string,
  correlationId: CorrelationId,
  options: Partial<AIVisibilityContract> = {}
): AIVisibilityContract {
  return {
    operationState: 'idle',
    operationLabel,
    suggestedChanges: [],
    appliedChanges: [],
    pendingChanges: [],
    confidenceBand: { low: 0, high: 0 },
    rationale: '',
    evidence: [],
    approvalState: 'PENDING',
    reviewRequired: false,
    rollbackAvailable: true,
    overrideAvailable: true,
    autonomyLevel: 'ASSISTED',
    correlationId,
    triggeredBy: 'explicit',
    ...options,
  };
}
