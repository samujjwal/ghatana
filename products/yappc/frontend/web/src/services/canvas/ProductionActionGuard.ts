/**
 * Production Action Guard Service
 *
 * @doc.type service
 * @doc.purpose Block production actions from unsynced local-only artifacts
 * @doc.layer product
 * @doc.pattern Guard Pattern
 */

import type { PageArtifactSource } from '../../components/canvas/page/pageArtifactDocument';

export type ProductionAction = 'preview' | 'export' | 'apply' | 'deploy';

export interface ProductionActionGuardContext {
  readonly source: PageArtifactSource;
  readonly syncStatus: 'dirty' | 'saving' | 'synced' | 'error' | 'offline';
  readonly isDevMode: boolean;
  readonly isOfflineRecoveryMode: boolean;
}

export interface ProductionActionGuardResult {
  readonly allowed: boolean;
  readonly reason?: string;
  readonly requiresSync?: boolean;
}

/**
 * Check if a production action is allowed based on artifact source and sync status
 *
 * Production actions (preview, export, apply, deploy) require successful server sync
 * unless in dev mode or offline recovery mode.
 */
export function checkProductionActionAllowed(
  action: ProductionAction,
  context: ProductionActionGuardContext
): ProductionActionGuardResult {
  const { source, syncStatus, isDevMode, isOfflineRecoveryMode } = context;

  // Allow all actions in dev mode
  if (isDevMode) {
    return { allowed: true };
  }

  // Allow all actions in offline recovery mode
  if (isOfflineRecoveryMode) {
    return { allowed: true };
  }

  // Block actions for non-authoritative sources
  if (source === 'local-draft') {
    return {
      allowed: false,
      reason: 'Cannot perform production actions on local drafts. Sync with the server first.',
      requiresSync: true,
    };
  }

  if (source === 'recovered-draft') {
    return {
      allowed: false,
      reason: 'Cannot perform production actions on recovered drafts. Explicit sync with the server is required.',
      requiresSync: true,
    };
  }

  // Block actions when sync status is not synced
  if (syncStatus !== 'synced') {
    const statusMessages: Record<Exclude<typeof syncStatus, 'synced'>, string> = {
      dirty: 'Artifact has unsaved changes. Save and sync before performing production actions.',
      saving: 'Artifact is currently saving. Wait for sync to complete before performing production actions.',
      error: 'Artifact sync failed. Resolve sync errors before performing production actions.',
      offline: 'Working offline. Sync with the server before performing production actions.',
    };

    return {
      allowed: false,
      reason: statusMessages[syncStatus],
      requiresSync: true,
    };
  }

  // Action is allowed
  return { allowed: true };
}

/**
 * Check if an artifact is in a state that requires sync before production actions
 */
export function requiresSyncBeforeProductionActions(
  source: PageArtifactSource,
  syncStatus: ProductionActionGuardContext['syncStatus']
): boolean {
  return (
    source === 'local-draft' ||
    source === 'recovered-draft' ||
    syncStatus !== 'synced'
  );
}

/**
 * Get a user-friendly error message for a blocked production action
 */
export function getProductionActionBlockedMessage(
  action: ProductionAction,
  context: ProductionActionGuardContext
): string {
  const result = checkProductionActionAllowed(action, context);
  return result.reason ?? 'Production action blocked';
}
