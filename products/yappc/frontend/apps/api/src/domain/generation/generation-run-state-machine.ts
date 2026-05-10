/**
 * Canonical Generation Run State Machine
 *
 * Defines the state machine for tracking generation runs through their lifecycle:
 * Intent → Plan → Confirm → GenerateRun → ReviewDiff → PreviewSession → Apply/Reject/Rollback → Export/Deploy
 *
 * @doc.type domain
 * @doc.purpose Track generation run lifecycle states and transitions
 * @doc.layer domain
 */

/**
 * Generation run states in the canonical lifecycle
 */
export type GenerationRunState =
  | 'INTENT'
  | 'PLAN'
  | 'CONFIRM'
  | 'GENERATE_RUN'
  | 'REVIEW_DIFF'
  | 'PREVIEW_SESSION'
  | 'APPLY'
  | 'REJECT'
  | 'ROLLBACK'
  | 'EXPORT'
  | 'DEPLOY'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

/**
 * Generation run transition events
 */
export type GenerationRunTransition =
  | 'START_INTENT'
  | 'GENERATE_PLAN'
  | 'CONFIRM_PLAN'
  | 'START_GENERATION'
  | 'GENERATION_COMPLETE'
  | 'START_REVIEW'
  | 'START_PREVIEW'
  | 'APPLY_CHANGES'
  | 'REJECT_CHANGES'
  | 'ROLLBACK_CHANGES'
  | 'RESTART_PLAN'
  | 'EXPORT_ARTIFACTS'
  | 'DEPLOY_ARTIFACTS'
  | 'COMPLETE'
  | 'FAIL'
  | 'CANCEL';

/**
 * Generation run metadata
 */
export interface GenerationRunMetadata {
  id: string;
  projectId: string;
  userId: string;
  workspaceId: string;
  tenantId: string;
  intent: string;
  plan?: string;
  generatedArtifacts: string[];
  diffSummary?: string;
  previewSessionId?: string;
  appliedAt?: Date;
  rejectedAt?: Date;
  rolledBackAt?: Date;
  exportedAt?: Date;
  deployedAt?: Date;
  confidence?: number;
  error?: string;
  correlationId?: string;
}

/**
 * Generation run state with metadata
 */
export interface GenerationRun {
  state: GenerationRunState;
  metadata: GenerationRunMetadata;
  previousState?: GenerationRunState;
  transitionedAt: Date;
}

/**
 * Valid state transitions
 */
const VALID_TRANSITIONS: Record<GenerationRunState, GenerationRunTransition[]> = {
  INTENT: ['GENERATE_PLAN', 'FAIL', 'CANCEL'],
  PLAN: ['CONFIRM_PLAN', 'FAIL', 'CANCEL'],
  CONFIRM: ['START_GENERATION', 'FAIL', 'CANCEL'],
  GENERATE_RUN: ['GENERATION_COMPLETE', 'FAIL', 'CANCEL'],
  REVIEW_DIFF: ['START_PREVIEW', 'REJECT_CHANGES', 'FAIL', 'CANCEL'],
  PREVIEW_SESSION: ['APPLY_CHANGES', 'REJECT_CHANGES', 'FAIL', 'CANCEL'],
  APPLY: ['EXPORT_ARTIFACTS', 'DEPLOY_ARTIFACTS', 'ROLLBACK_CHANGES', 'COMPLETE', 'FAIL'],
  REJECT: ['ROLLBACK_CHANGES', 'FAIL'],
  ROLLBACK: ['RESTART_PLAN', 'FAIL'],
  EXPORT: ['DEPLOY_ARTIFACTS', 'COMPLETE'],
  DEPLOY: ['COMPLETE', 'ROLLBACK_CHANGES'],
  COMPLETED: [],
  FAILED: ['RESTART_PLAN'],
  CANCELLED: [],
};

/**
 * Check if a transition is valid for the current state
 */
export function isValidTransition(
  currentState: GenerationRunState,
  transition: GenerationRunTransition,
): boolean {
  return VALID_TRANSITIONS[currentState]?.includes(transition) ?? false;
}

/**
 * Get the next state after a transition
 */
export function getNextState(
  currentState: GenerationRunState,
  transition: GenerationRunTransition,
): GenerationRunState | null {
  const stateMap: Record<GenerationRunTransition, GenerationRunState> = {
    START_INTENT: 'INTENT',
    GENERATE_PLAN: 'PLAN',
    CONFIRM_PLAN: 'CONFIRM',
    START_GENERATION: 'GENERATE_RUN',
    GENERATION_COMPLETE: 'REVIEW_DIFF',
    START_REVIEW: 'REVIEW_DIFF',
    START_PREVIEW: 'PREVIEW_SESSION',
    APPLY_CHANGES: 'APPLY',
    REJECT_CHANGES: 'REJECT',
    ROLLBACK_CHANGES: 'ROLLBACK',
    RESTART_PLAN: 'PLAN',
    EXPORT_ARTIFACTS: 'EXPORT',
    DEPLOY_ARTIFACTS: 'DEPLOY',
    COMPLETE: 'COMPLETED',
    FAIL: 'FAILED',
    CANCEL: 'CANCELLED',
  };

  if (!isValidTransition(currentState, transition)) {
    return null;
  }

  return stateMap[transition] ?? null;
}

/**
 * Check if a state is terminal (no further transitions)
 */
export function isTerminalState(state: GenerationRunState): boolean {
  return state === 'COMPLETED' || state === 'FAILED' || state === 'CANCELLED';
}

/**
 * Check if a state allows rollback
 */
export function allowsRollback(state: GenerationRunState): boolean {
  return state === 'APPLY' || state === 'DEPLOY';
}

/**
 * Check if a state allows rejection
 */
export function allowsRejection(state: GenerationRunState): boolean {
  return state === 'REVIEW_DIFF' || state === 'PREVIEW_SESSION';
}

/**
 * Check if a state allows export
 */
export function allowsExport(state: GenerationRunState): boolean {
  return state === 'APPLY' || state === 'DEPLOY';
}

/**
 * Check if a state allows deployment
 */
export function allowsDeployment(state: GenerationRunState): boolean {
  return state === 'EXPORT' || state === 'APPLY';
}

/**
 * Get available transitions for a state
 */
export function getAvailableTransitions(state: GenerationRunState): GenerationRunTransition[] {
  return VALID_TRANSITIONS[state] ?? [];
}

/**
 * Get human-readable label for a state
 */
export function getStateLabel(state: GenerationRunState): string {
  const labels: Record<GenerationRunState, string> = {
    INTENT: 'Intent Capture',
    PLAN: 'Plan Generation',
    CONFIRM: 'Plan Confirmation',
    GENERATE_RUN: 'Generation Running',
    REVIEW_DIFF: 'Diff Review',
    PREVIEW_SESSION: 'Preview Session',
    APPLY: 'Applied',
    REJECT: 'Rejected',
    ROLLBACK: 'Rolled Back',
    EXPORT: 'Exported',
    DEPLOY: 'Deployed',
    COMPLETED: 'Completed',
    FAILED: 'Failed',
    CANCELLED: 'Cancelled',
  };
  return labels[state] ?? state;
}

/**
 * Get human-readable label for a transition
 */
export function getTransitionLabel(transition: GenerationRunTransition): string {
  const labels: Record<GenerationRunTransition, string> = {
    START_INTENT: 'Start Intent',
    GENERATE_PLAN: 'Generate Plan',
    CONFIRM_PLAN: 'Confirm Plan',
    START_GENERATION: 'Start Generation',
    GENERATION_COMPLETE: 'Generation Complete',
    START_REVIEW: 'Start Review',
    START_PREVIEW: 'Start Preview',
    APPLY_CHANGES: 'Apply Changes',
    REJECT_CHANGES: 'Reject Changes',
    ROLLBACK_CHANGES: 'Rollback Changes',
    RESTART_PLAN: 'Restart Plan',
    EXPORT_ARTIFACTS: 'Export Artifacts',
    DEPLOY_ARTIFACTS: 'Deploy Artifacts',
    COMPLETE: 'Complete',
    FAIL: 'Fail',
    CANCEL: 'Cancel',
  };
  return labels[transition] ?? transition;
}

/**
 * Generation run state machine class
 */
export class GenerationRunStateMachine {
  private run: GenerationRun;

  constructor(initialState: GenerationRunState, metadata: GenerationRunMetadata) {
    this.run = {
      state: initialState,
      metadata,
      transitionedAt: new Date(),
    };
  }

  /**
   * Get current state
   */
  getCurrentState(): GenerationRunState {
    return this.run.state;
  }

  /**
   * Get current run
   */
  getRun(): GenerationRun {
    return this.run;
  }

  /**
   * Attempt a transition
   */
  transition(transition: GenerationRunTransition): GenerationRun | null {
    const nextState = getNextState(this.run.state, transition);
    if (nextState === null) {
      return null;
    }

    const previousState = this.run.state;
    this.run = {
      state: nextState,
      metadata: this.run.metadata,
      previousState,
      transitionedAt: new Date(),
    };

    return this.run;
  }

  /**
   * Check if a transition is valid
   */
  canTransition(transition: GenerationRunTransition): boolean {
    return isValidTransition(this.run.state, transition);
  }

  /**
   * Get available transitions
   */
  getAvailableTransitions(): GenerationRunTransition[] {
    return getAvailableTransitions(this.run.state);
  }

  /**
   * Check if current state is terminal
   */
  isTerminal(): boolean {
    return isTerminalState(this.run.state);
  }

  /**
   * Check if current state allows rollback
   */
  canRollback(): boolean {
    return allowsRollback(this.run.state);
  }

  /**
   * Check if current state allows rejection
   */
  canReject(): boolean {
    return allowsRejection(this.run.state);
  }

  /**
   * Check if current state allows export
   */
  canExport(): boolean {
    return allowsExport(this.run.state);
  }

  /**
   * Check if current state allows deployment
   */
  canDeploy(): boolean {
    return allowsDeployment(this.run.state);
  }
}
