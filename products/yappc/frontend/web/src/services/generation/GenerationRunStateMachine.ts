/**
 * Canonical Generation Run State Machine
 *
 * @doc.type type
 * @doc.purpose Defines the canonical state machine for generation runs
 * @doc.layer service
 * @doc.pattern State Machine
 *
 * Models the full flow: Intent → Plan → Confirm → GenerateRun → ReviewDiff →
 * PreviewSession → Apply/Reject/Rollback → Export/Deploy
 */

// =============================================================================
// Generation Run States
// =============================================================================

/**
 * Canonical states for a generation run lifecycle
 */
export type GenerationRunState =
  // Initial states
  | 'intent_captured'      // User intent has been captured
  | 'plan_generated'       // AI plan has been generated
  | 'awaiting_confirm'    // Waiting for user to confirm plan
  // Execution states
  | 'generation_started'  // Generation run has been initiated
  | 'generation_running'  // Generation is in progress
  | 'generation_failed'   // Generation encountered an error
  | 'generation_completed' // Generation finished successfully
  // Review states
  | 'diff_ready'          // Diff is ready for review
  | 'awaiting_review'    // Waiting for user review decision
  | 'review_approved'     // Review approved - ready to apply
  | 'review_rejected'     // Review rejected - changes discarded
  // Preview states
  | 'preview_session_created' // Preview session created
  | 'preview_active'      // Preview is active
  | 'preview_expired'     // Preview session expired
  // Apply/rollback states
  | 'apply_started'       // Applying changes to artifacts
  | 'apply_completed'     // Changes applied successfully
  | 'apply_failed'        // Apply encountered an error
  | 'rollback_started'    // Rolling back applied changes
  | 'rollback_completed'  // Rollback completed successfully
  | 'rollback_failed'     // Rollback encountered an error
  // Export/deploy states
  | 'export_started'      // Exporting artifacts
  | 'export_completed'    // Export completed successfully
  | 'export_failed'       // Export encountered an error
  | 'deploy_started'      // Deployment initiated
  | 'deploy_completed'    // Deployment completed successfully
  | 'deploy_failed'       // Deployment encountered an error
  // Terminal states
  | 'completed'           // Full lifecycle completed successfully
  | 'abandoned'           // Run was abandoned
  | 'failed'              // Run failed unrecoverably;

/**
 * State categories for grouping related states
 */
export type GenerationRunStateCategory =
  | 'initial'
  | 'execution'
  | 'review'
  | 'preview'
  | 'apply'
  | 'rollback'
  | 'export'
  | 'deploy'
  | 'terminal';

/**
 * Map each state to its category
 */
export const STATE_CATEGORY_MAP: Readonly<Record<GenerationRunState, GenerationRunStateCategory>> = {
  intent_captured: 'initial',
  plan_generated: 'initial',
  awaiting_confirm: 'initial',
  generation_started: 'execution',
  generation_running: 'execution',
  generation_failed: 'execution',
  generation_completed: 'execution',
  diff_ready: 'review',
  awaiting_review: 'review',
  review_approved: 'review',
  review_rejected: 'review',
  preview_session_created: 'preview',
  preview_active: 'preview',
  preview_expired: 'preview',
  apply_started: 'apply',
  apply_completed: 'apply',
  apply_failed: 'apply',
  rollback_started: 'rollback',
  rollback_completed: 'rollback',
  rollback_failed: 'rollback',
  export_started: 'export',
  export_completed: 'export',
  export_failed: 'export',
  deploy_started: 'deploy',
  deploy_completed: 'deploy',
  deploy_failed: 'deploy',
  completed: 'terminal',
  abandoned: 'terminal',
  failed: 'terminal',
} as const;

/**
 * Check if a state is terminal (no further transitions possible)
 */
export function isTerminalState(state: GenerationRunState): boolean {
  return STATE_CATEGORY_MAP[state] === 'terminal';
}

/**
 * Check if a state is recoverable (can retry from this state)
 */
export function isRecoverableState(state: GenerationRunState): boolean {
  return (
    state === 'generation_failed' ||
    state === 'apply_failed' ||
    state === 'rollback_failed' ||
    state === 'export_failed' ||
    state === 'deploy_failed'
  );
}

// =============================================================================
// State Transitions
// =============================================================================

/**
 * Valid transitions between states
 */
export type GenerationRunTransition =
  | 'capture_intent'
  | 'generate_plan'
  | 'confirm_plan'
  | 'start_generation'
  | 'fail_generation'
  | 'complete_generation'
  | 'generate_diff'
  | 'request_review'
  | 'approve_review'
  | 'reject_review'
  | 'create_preview'
  | 'activate_preview'
  | 'expire_preview'
  | 'start_apply'
  | 'complete_apply'
  | 'fail_apply'
  | 'start_rollback'
  | 'complete_rollback'
  | 'fail_rollback'
  | 'start_export'
  | 'complete_export'
  | 'fail_export'
  | 'start_deploy'
  | 'complete_deploy'
  | 'fail_deploy'
  | 'complete_lifecycle'
  | 'abandon'
  | 'fail_lifecycle';

/**
 * State transition matrix: maps from state to valid transitions
 */
export const TRANSITION_MATRIX: Readonly<Record<GenerationRunState, readonly GenerationRunTransition[]>> = {
  intent_captured: ['generate_plan', 'abandon'],
  plan_generated: ['confirm_plan', 'abandon'],
  awaiting_confirm: ['start_generation', 'abandon'],
  generation_started: ['complete_generation', 'fail_generation'],
  generation_running: ['complete_generation', 'fail_generation'],
  generation_failed: ['start_generation', 'abandon', 'fail_lifecycle'],
  generation_completed: ['generate_diff'],
  diff_ready: ['request_review'],
  awaiting_review: ['approve_review', 'reject_review', 'abandon'],
  review_approved: ['create_preview', 'start_apply'],
  review_rejected: ['abandon', 'start_generation'],
  preview_session_created: ['activate_preview', 'expire_preview'],
  preview_active: ['start_apply', 'start_rollback', 'expire_preview'],
  preview_expired: ['create_preview', 'start_apply', 'start_rollback', 'abandon'],
  apply_started: ['complete_apply', 'fail_apply'],
  apply_completed: ['start_export', 'start_deploy', 'complete_lifecycle'],
  apply_failed: ['start_apply', 'start_rollback', 'abandon', 'fail_lifecycle'],
  rollback_started: ['complete_rollback', 'fail_rollback'],
  rollback_completed: ['abandon', 'start_generation'],
  rollback_failed: ['start_rollback', 'abandon', 'fail_lifecycle'],
  export_started: ['complete_export', 'fail_export'],
  export_completed: ['start_deploy', 'complete_lifecycle'],
  export_failed: ['start_export', 'abandon', 'fail_lifecycle'],
  deploy_started: ['complete_deploy', 'fail_deploy'],
  deploy_completed: ['complete_lifecycle'],
  deploy_failed: ['start_deploy', 'start_rollback', 'abandon', 'fail_lifecycle'],
  completed: [],
  abandoned: [],
  failed: [],
} as const;

/**
 * Check if a transition is valid from a given state
 */
export function isValidTransition(
  from: GenerationRunState,
  transition: GenerationRunTransition
): boolean {
  return TRANSITION_MATRIX[from].includes(transition);
}

/**
 * Get the next state after a transition
 */
export function getNextState(
  from: GenerationRunState,
  transition: GenerationRunTransition
): GenerationRunState | null {
  const nextStateMap: Partial<Record<GenerationRunTransition, GenerationRunState>> = {
    capture_intent: 'intent_captured',
    generate_plan: 'plan_generated',
    confirm_plan: 'awaiting_confirm',
    start_generation: 'generation_started',
    fail_generation: 'generation_failed',
    complete_generation: 'generation_completed',
    generate_diff: 'diff_ready',
    request_review: 'awaiting_review',
    approve_review: 'review_approved',
    reject_review: 'review_rejected',
    create_preview: 'preview_session_created',
    activate_preview: 'preview_active',
    expire_preview: 'preview_expired',
    start_apply: 'apply_started',
    complete_apply: 'apply_completed',
    fail_apply: 'apply_failed',
    start_rollback: 'rollback_started',
    complete_rollback: 'rollback_completed',
    fail_rollback: 'rollback_failed',
    start_export: 'export_started',
    complete_export: 'export_completed',
    fail_export: 'export_failed',
    start_deploy: 'deploy_started',
    complete_deploy: 'deploy_completed',
    fail_deploy: 'deploy_failed',
    complete_lifecycle: 'completed',
    abandon: 'abandoned',
    fail_lifecycle: 'failed',
  };

  if (!isValidTransition(from, transition)) {
    return null;
  }

  return nextStateMap[transition] ?? null;
}

// =============================================================================
// Generation Run Metadata
// =============================================================================

/**
 * Core metadata for a generation run
 */
export interface GenerationRunMetadata {
  readonly runId: string;
  readonly projectId: string;
  readonly workspaceId: string;
  readonly tenantId?: string;
  readonly actorId?: string;
  readonly currentState: GenerationRunState;
  readonly previousState?: GenerationRunState;
  readonly enteredCurrentStateAt: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Execution context for a generation run
 */
export interface GenerationRunExecutionContext {
  readonly intent?: string;
  readonly planId?: string;
  readonly planSummary?: string;
  readonly generationConfig?: Record<string, unknown>;
  readonly previewSessionId?: string;
  readonly previewUrl?: string;
  readonly previewExpiresAt?: string;
  readonly appliedArtifacts?: readonly string[];
  readonly rollbackVersion?: string;
  readonly exportPath?: string;
  readonly deploymentId?: string;
  readonly deploymentEnvironment?: string;
  readonly correlationId?: string;
}

/**
 * Error information for failed states
 */
export interface GenerationRunError {
  readonly code: string;
  readonly message: string;
  readonly details?: Record<string, unknown>;
  readonly correlationId?: string;
  readonly occurredAt: string;
}

/**
 * Full generation run record
 */
export interface GenerationRun {
  readonly metadata: GenerationRunMetadata;
  readonly context: GenerationRunExecutionContext;
  readonly error?: GenerationRunError;
}

// =============================================================================
// State Machine Utilities
// =============================================================================

/**
 * Get human-readable label for a state
 */
export function getStateLabel(state: GenerationRunState): string {
  const labels: Record<GenerationRunState, string> = {
    intent_captured: 'Intent Captured',
    plan_generated: 'Plan Generated',
    awaiting_confirm: 'Awaiting Confirmation',
    generation_started: 'Generation Started',
    generation_running: 'Generation Running',
    generation_failed: 'Generation Failed',
    generation_completed: 'Generation Completed',
    diff_ready: 'Diff Ready',
    awaiting_review: 'Awaiting Review',
    review_approved: 'Review Approved',
    review_rejected: 'Review Rejected',
    preview_session_created: 'Preview Session Created',
    preview_active: 'Preview Active',
    preview_expired: 'Preview Expired',
    apply_started: 'Apply Started',
    apply_completed: 'Apply Completed',
    apply_failed: 'Apply Failed',
    rollback_started: 'Rollback Started',
    rollback_completed: 'Rollback Completed',
    rollback_failed: 'Rollback Failed',
    export_started: 'Export Started',
    export_completed: 'Export Completed',
    export_failed: 'Export Failed',
    deploy_started: 'Deploy Started',
    deploy_completed: 'Deploy Completed',
    deploy_failed: 'Deploy Failed',
    completed: 'Completed',
    abandoned: 'Abandoned',
    failed: 'Failed',
  };
  return labels[state];
}

/**
 * Get human-readable label for a transition
 */
export function getTransitionLabel(transition: GenerationRunTransition): string {
  const labels: Record<GenerationRunTransition, string> = {
    capture_intent: 'Capture Intent',
    generate_plan: 'Generate Plan',
    confirm_plan: 'Confirm Plan',
    start_generation: 'Start Generation',
    fail_generation: 'Fail Generation',
    complete_generation: 'Complete Generation',
    generate_diff: 'Generate Diff',
    request_review: 'Request Review',
    approve_review: 'Approve Review',
    reject_review: 'Reject Review',
    create_preview: 'Create Preview',
    activate_preview: 'Activate Preview',
    expire_preview: 'Expire Preview',
    start_apply: 'Start Apply',
    complete_apply: 'Complete Apply',
    fail_apply: 'Fail Apply',
    start_rollback: 'Start Rollback',
    complete_rollback: 'Complete Rollback',
    fail_rollback: 'Fail Rollback',
    start_export: 'Start Export',
    complete_export: 'Complete Export',
    fail_export: 'Fail Export',
    start_deploy: 'Start Deploy',
    complete_deploy: 'Complete Deploy',
    fail_deploy: 'Fail Deploy',
    complete_lifecycle: 'Complete Lifecycle',
    abandon: 'Abandon',
    fail_lifecycle: 'Fail Lifecycle',
  };
  return labels[transition];
}

/**
 * Check if a state requires user intervention
 */
export function requiresUserIntervention(state: GenerationRunState): boolean {
  return (
    state === 'awaiting_confirm' ||
    state === 'awaiting_review' ||
    state === 'preview_active'
  );
}

/**
 * Get valid next states for a given current state
 */
export function getValidNextStates(state: GenerationRunState): readonly GenerationRunState[] {
  const transitions = TRANSITION_MATRIX[state];
  const nextStates: GenerationRunState[] = [];

  for (const transition of transitions) {
    const nextState = getNextState(state, transition);
    if (nextState) {
      nextStates.push(nextState);
    }
  }

  return nextStates;
}
