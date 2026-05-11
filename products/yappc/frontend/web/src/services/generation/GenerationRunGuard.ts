/**
 * Generation Run Guard Service
 *
 * @doc.type service
 * @doc.purpose Block export/apply before review is complete
 * @doc.layer product
 * @doc.pattern Service Layer
 *
 * Checks generation run status, review decision, artifact sync status, and authorization
 * before allowing export/apply operations.
 */

import type { GenerationRunState } from './GenerationRunStateMachine';

// =============================================================================
// Export/Apply Guard Types
// =============================================================================

/**
 * Result of an export/apply eligibility check
 */
export interface ExportApplyGuardResult {
  readonly allowed: boolean;
  readonly reason?: string;
  readonly correlationId?: string;
}

/**
 * Required context for checking export/apply eligibility
 */
export interface ExportApplyGuardContext {
  readonly runId?: string;
  readonly projectId: string;
  readonly workspaceId: string;
  readonly tenantId?: string;
  readonly userId?: string;
  readonly currentState?: GenerationRunState;
  readonly reviewDecision?: 'apply' | 'reject' | 'rollback';
  readonly artifactSyncStatus?: 'synced' | 'pending' | 'failed' | 'conflict';
}

/**
 * Authorization check result
 */
export interface AuthorizationCheck {
  readonly authorized: boolean;
  readonly reason?: string;
}

// =============================================================================
// Guard Service
// =============================================================================

/**
 * Check if a user is authorized to perform export/apply on a generation run
 */
export async function checkExportApplyAuthorization(
  context: ExportApplyGuardContext
): Promise<AuthorizationCheck> {
  const { userId, projectId, workspaceId, tenantId } = context;

  if (!userId) {
    return {
      authorized: false,
      reason: 'User authentication required for export/apply operations',
    };
  }

  if (!tenantId) {
    return {
      authorized: false,
      reason: 'Tenant context required for export/apply operations',
    };
  }

  if (!workspaceId) {
    return {
      authorized: false,
      reason: 'Workspace context required for export/apply operations',
    };
  }

  if (!projectId) {
    return {
      authorized: false,
      reason: 'Project context required for export/apply operations',
    };
  }

  // TODO-017: Verify user has required capabilities (read, update) on the project
  // This would call the backend capability contract to check authorization
  // For now, we assume authorization if all context is present
  return { authorized: true };
}

/**
 * Check if the generation run is in a state that allows export/apply
 */
export function checkGenerationRunState(
  context: ExportApplyGuardContext
): ExportApplyGuardResult {
  const { currentState } = context;

  if (!currentState) {
    return {
      allowed: false,
      reason: 'Generation run state unknown. Cannot proceed with export/apply.',
    };
  }

  // Export/apply is only allowed after review is approved
  const allowedStates: readonly GenerationRunState[] = [
    'review_approved',
    'apply_completed',
    'export_completed',
    'deploy_completed',
    'completed',
  ];

  if (!allowedStates.includes(currentState)) {
    const stateLabel = currentState.replace(/_/g, ' ').toLowerCase();
    return {
      allowed: false,
      reason: `Generation run is in "${stateLabel}" state. Export/apply requires review approval.`,
    };
  }

  return { allowed: true };
}

/**
 * Check if the review decision allows export/apply
 */
export function checkReviewDecision(
  context: ExportApplyGuardContext
): ExportApplyGuardResult {
  const { reviewDecision } = context;

  if (!reviewDecision) {
    return {
      allowed: false,
      reason: 'Review decision not recorded. Cannot proceed with export/apply.',
    };
  }

  if (reviewDecision === 'reject') {
    return {
      allowed: false,
      reason: 'Generation run was rejected. Export/apply is not allowed for rejected runs.',
    };
  }

  if (reviewDecision === 'rollback') {
    return {
      allowed: false,
      reason: 'Generation run was rolled back. Export/apply is not allowed for rolled back runs.',
    };
  }

  if (reviewDecision === 'apply') {
    return { allowed: true };
  }

  return {
    allowed: false,
    reason: `Unknown review decision: ${reviewDecision}`,
  };
}

/**
 * Check if artifact sync status allows export/apply
 */
export function checkArtifactSyncStatus(
  context: ExportApplyGuardContext
): ExportApplyGuardResult {
  const { artifactSyncStatus } = context;

  if (!artifactSyncStatus) {
    return {
      allowed: false,
      reason: 'Artifact sync status unknown. Cannot proceed with export/apply.',
    };
  }

  if (artifactSyncStatus === 'pending') {
    return {
      allowed: false,
      reason: 'Artifacts are still syncing to server. Please wait for sync to complete.',
    };
  }

  if (artifactSyncStatus === 'failed') {
    return {
      allowed: false,
      reason: 'Artifact sync failed. Please retry the sync before export/apply.',
    };
  }

  if (artifactSyncStatus === 'conflict') {
    return {
      allowed: false,
      reason: 'Artifact version conflict detected. Please resolve conflicts before export/apply.',
    };
  }

  if (artifactSyncStatus === 'synced') {
    return { allowed: true };
  }

  return {
    allowed: false,
    reason: `Unknown artifact sync status: ${artifactSyncStatus}`,
  };
}

/**
 * Comprehensive check for export/apply eligibility
 *
 * Checks authorization, generation run state, review decision, and artifact sync status
 * in the correct order and returns a combined result.
 */
export async function checkExportApplyEligibility(
  context: ExportApplyGuardContext
): Promise<ExportApplyGuardResult> {
  // 1. Check authorization first
  const authCheck = await checkExportApplyAuthorization(context);
  if (!authCheck.authorized) {
    return {
      allowed: false,
      reason: authCheck.reason,
    };
  }

  // 2. Check generation run state
  const stateCheck = checkGenerationRunState(context);
  if (!stateCheck.allowed) {
    return stateCheck;
  }

  // 3. Check review decision
  const reviewCheck = checkReviewDecision(context);
  if (!reviewCheck.allowed) {
    return reviewCheck;
  }

  // 4. Check artifact sync status
  const syncCheck = checkArtifactSyncStatus(context);
  if (!syncCheck.allowed) {
    return syncCheck;
  }

  // All checks passed
  return {
    allowed: true,
    correlationId: context.runId,
  };
}

/**
 * Throw an error if export/apply is not eligible
 *
 * Convenience function for use in try/catch blocks
 */
export async function requireExportApplyEligibility(
  context: ExportApplyGuardContext
): Promise<void> {
  const result = await checkExportApplyEligibility(context);

  if (!result.allowed) {
    throw new Error(result.reason || 'Export/apply operation not allowed');
  }
}
