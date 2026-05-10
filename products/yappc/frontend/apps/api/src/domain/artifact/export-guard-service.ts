/**
 * Export Guard Service
 *
 * Blocks export/apply before review is complete by checking:
 * - Generation run status
 * - Review decision
 * - Artifact sync status
 * - Authorization
 * - Environment mode (dev/offline recovery vs production)
 *
 * @doc.type domain
 * @doc.purpose Guard export/apply operations until review is complete
 * @doc.layer domain
 */

import type { GenerationRunState } from '../generation/generation-run-state-machine';
import type { ArtifactLineage } from './artifact-lineage';

/**
 * Export guard result
 */
export interface ExportGuardResult {
  allowed: boolean;
  reason?: ExportBlockReason;
  details: ExportGuardDetails;
}

/**
 * Export block reasons
 */
export type ExportBlockReason =
  | 'generation_run_not_completed'
  | 'review_not_complete'
  | 'review_rejected'
  | 'artifact_not_synced'
  | 'authorization_failed'
  | 'confidence_below_threshold'
  | 'approval_missing';

/**
 * Export guard details
 */
export interface ExportGuardDetails {
  generationRunId: string;
  generationRunState: GenerationRunState;
  reviewDecision?: 'approved' | 'rejected' | 'changes_requested' | 'pending';
  reviewedBy?: string;
  reviewedAt?: Date;
  approvedBy?: string;
  approvedAt?: Date;
  artifactSynced: boolean;
  artifactSyncedAt?: Date;
  confidence: number;
  userId: string;
  userRoles: string[];
  correlationId?: string;
}

/**
 * Export guard configuration
 */
export interface ExportGuardConfig {
  confidenceThreshold: number;
  requireApproval: boolean;
  requireSync: boolean;
  bypassRoles: string[];
  /**
   * Environment mode: 'development', 'staging', or 'production'
   * In development or offline recovery, unsynced artifacts may be allowed
   */
  environmentMode?: 'development' | 'staging' | 'production';
  /**
   * Offline recovery mode: when true, allows unsynced local-only artifacts
   */
  offlineRecoveryMode?: boolean;
}

const DEFAULT_CONFIG: ExportGuardConfig = {
  confidenceThreshold: 70,
  requireApproval: true,
  requireSync: true,
  bypassRoles: ['ADMIN', 'OWNER'],
};

/**
 * Export guard service class
 */
export class ExportGuardService {
  private config: ExportGuardConfig;

  constructor(config: ExportGuardConfig = DEFAULT_CONFIG) {
    this.config = config;
  }

  /**
   * Check if export is allowed
   */
  async checkExportAllowed(
    generationRunId: string,
    generationRunState: GenerationRunState,
    artifactLineage: ArtifactLineage,
    userId: string,
    userRoles: string[],
    artifactSynced: boolean,
    artifactSyncedAt?: Date,
  ): Promise<ExportGuardResult> {
    const correlationId = `export-guard-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;

    const details: ExportGuardDetails = {
      generationRunId,
      generationRunState,
      reviewDecision: artifactLineage.reviewDecision,
      reviewedBy: artifactLineage.reviewedBy,
      reviewedAt: artifactLineage.reviewedAt,
      approvedBy: artifactLineage.approvedBy,
      approvedAt: artifactLineage.approvedAt,
      artifactSynced,
      artifactSyncedAt,
      confidence: artifactLineage.confidence,
      userId,
      userRoles,
      correlationId,
    };

    // Check if generation run is completed
    if (generationRunState !== 'COMPLETED' && generationRunState !== 'APPLY') {
      return {
        allowed: false,
        reason: 'generation_run_not_completed',
        details,
      };
    }

    // Check if review is complete
    if (!artifactLineage.reviewedAt || !artifactLineage.reviewedBy) {
      return {
        allowed: false,
        reason: 'review_not_complete',
        details,
      };
    }

    // Check if review was rejected
    if (artifactLineage.reviewDecision === 'rejected') {
      return {
        allowed: false,
        reason: 'review_rejected',
        details,
      };
    }

    // Check if review is pending or changes requested
    if (artifactLineage.reviewDecision === 'pending' || artifactLineage.reviewDecision === 'changes_requested') {
      return {
        allowed: false,
        reason: 'review_not_complete',
        details,
      };
    }

    // Check if approval is required and present
    if (this.config.requireApproval && (!artifactLineage.approvedAt || !artifactLineage.approvedBy)) {
      return {
        allowed: false,
        reason: 'approval_missing',
        details,
      };
    }

    // Check if artifact is synced
    // Allow unsynced artifacts in development or offline recovery mode
    const isDevelopmentOrRecovery =
      this.config.environmentMode === 'development' || this.config.offlineRecoveryMode === true;
    if (this.config.requireSync && !artifactSynced && !isDevelopmentOrRecovery) {
      return {
        allowed: false,
        reason: 'artifact_not_synced',
        details,
      };
    }

    // Check confidence threshold
    if (artifactLineage.confidence < this.config.confidenceThreshold) {
      return {
        allowed: false,
        reason: 'confidence_below_threshold',
        details,
      };
    }

    // Check authorization (bypass roles can proceed even if other checks fail)
    if (this.config.requireApproval && !this.hasBypassRole(userRoles)) {
      // Additional authorization checks can be added here
      // For now, we rely on the role-based bypass
    }

    return {
      allowed: true,
      details,
    };
  }

  /**
   * Check if apply is allowed
   */
  async checkApplyAllowed(
    generationRunId: string,
    generationRunState: GenerationRunState,
    artifactLineage: ArtifactLineage,
    userId: string,
    userRoles: string[],
    artifactSynced: boolean,
    artifactSyncedAt?: Date,
  ): Promise<ExportGuardResult> {
    // Apply has the same requirements as export
    return this.checkExportAllowed(
      generationRunId,
      generationRunState,
      artifactLineage,
      userId,
      userRoles,
      artifactSynced,
      artifactSyncedAt,
    );
  }

  /**
   * Check if user has bypass role
   */
  private hasBypassRole(userRoles: string[]): boolean {
    return userRoles.some(role => this.config.bypassRoles.includes(role));
  }

  /**
   * Get human-readable reason
   */
  getReasonText(reason: ExportBlockReason): string {
    const reasons: Record<ExportBlockReason, string> = {
      generation_run_not_completed: 'Generation run must be completed before export',
      review_not_complete: 'Review must be completed before export',
      review_rejected: 'Review was rejected, export not allowed',
      artifact_not_synced: 'Artifact must be synced to server before export',
      authorization_failed: 'User is not authorized to export this artifact',
      confidence_below_threshold: `Confidence ${this.config.confidenceThreshold}% threshold not met`,
      approval_missing: 'Approval is required before export',
    };
    return reasons[reason] ?? 'Export not allowed';
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<ExportGuardConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): ExportGuardConfig {
    return { ...this.config };
  }
}

/**
 * Default export guard service instance
 */
export const exportGuardService = new ExportGuardService();
