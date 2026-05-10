/**
 * Canonical Lifecycle Policy Service
 *
 * Centralizes all lifecycle policy decisions including:
 * - Phase readiness checks
 * - Blocker detection
 * - Transition rules
 * - Bypass rules
 * - Evidence requirements
 * - Approval workflow
 * - Audit logging
 *
 * This is the single source of truth for lifecycle policy enforcement.
 *
 * @doc.type class
 * @doc.purpose Centralized lifecycle policy enforcement
 * @doc.layer domain
 * @doc.pattern Service
 */

import {
  type LifecyclePhaseId,
  getPhaseMetadata,
  isValidPhaseTransition,
} from './lifecycle-taxonomy';

// ============================================================================
// Types
// ============================================================================

/**
 * Policy check result
 */
export interface PolicyCheckResult {
  allowed: boolean;
  phase: LifecyclePhaseId;
  readiness: PhaseReadiness;
  blockers: Blocker[];
  warnings: string[];
  bypassAvailable: boolean;
  bypassReason?: string;
}

/**
 * Phase readiness state
 */
export interface PhaseReadiness {
  phase: LifecyclePhaseId;
  ready: boolean;
  exitCriteriaMet: boolean;
  entryCriteriaMet: boolean;
  requiredArtifactsComplete: boolean;
  requiredApprovalsObtained: boolean;
  evidenceSufficient: boolean;
  score: number; // 0-100 readiness score
}

/**
 * Blocker preventing transition
 */
export interface Blocker {
  id: string;
  type: 'exit_criteria' | 'entry_criteria' | 'artifact' | 'approval' | 'evidence' | 'permission' | 'configuration';
  phase: LifecyclePhaseId;
  severity: 'critical' | 'high' | 'medium' | 'low';
  message: string;
  resolution?: string;
  canBypass: boolean;
  bypassRole?: string;
  evidenceRequired?: string[];
}

/**
 * Transition rule
 */
export interface TransitionRule {
  fromPhase: LifecyclePhaseId;
  toPhase: LifecyclePhaseId;
  allowed: boolean;
  requiresApproval: boolean;
  approverRoles: string[];
  minimumEvidence: number;
  autoApproveConditions: string[];
  bypassConditions: BypassCondition[];
}

/**
 * Bypass condition
 */
export interface BypassCondition {
  condition: string;
  allowedRoles: string[];
  requiresReason: boolean;
  requiresApproval: boolean;
  auditLevel: 'standard' | 'enhanced' | 'critical';
}

/**
 * Evidence requirement
 */
export interface EvidenceRequirement {
  phase: LifecyclePhaseId;
  evidenceTypes: string[];
  minimumCount: number;
  requiredFor: 'entry' | 'exit' | 'both';
  verificationRequired: boolean;
}

/**
 * Approval requirement
 */
export interface ApprovalRequirement {
  phase: LifecyclePhaseId;
  approverRoles: string[];
  minimumApprovals: number;
  quorum: boolean;
  escalationPath?: string[];
}

/**
 * Policy configuration
 */
export interface PolicyConfig {
  autoApproveLowRisk: boolean;
  requireApprovalForPhases: LifecyclePhaseId[];
  evidenceRequiredForPhases: LifecyclePhaseId[];
  bypassEnabled: boolean;
  auditAllTransitions: boolean;
  auditLevel: 'standard' | 'enhanced' | 'critical';
  enforceStrictMode: boolean;
}

// ============================================================================
// Default Configuration
// ============================================================================

const DEFAULT_POLICY_CONFIG: PolicyConfig = {
  autoApproveLowRisk: true,
  requireApprovalForPhases: ['CONTEXT', 'PLAN', 'VERIFY', 'INSTITUTIONALIZE'],
  evidenceRequiredForPhases: ['PLAN', 'VERIFY', 'INSTITUTIONALIZE'],
  bypassEnabled: true,
  auditAllTransitions: true,
  auditLevel: 'standard',
  enforceStrictMode: false,
};

// ============================================================================
// Canonical Lifecycle Policy Service
// ============================================================================

export class LifecyclePolicyService {
  private config: PolicyConfig;

  constructor(config: PolicyConfig = DEFAULT_POLICY_CONFIG) {
    this.config = config;
  }

  /**
   * Check if a phase is ready for transition
   */
  public checkPhaseReadiness(
    projectId: string,
    phase: LifecyclePhaseId,
    context: PhaseContext
  ): PhaseReadiness {
    const metadata = getPhaseMetadata(phase);
    if (!metadata) {
      return {
        phase,
        ready: false,
        exitCriteriaMet: false,
        entryCriteriaMet: false,
        requiredArtifactsComplete: false,
        requiredApprovalsObtained: false,
        evidenceSufficient: false,
        score: 0,
      };
    }

    // Check exit criteria
    const exitCriteriaMet = this.checkExitCriteria(phase, context);
    
    // Check entry criteria for next phase
    const nextPhaseIndex = this.getPhaseOrder(phase) + 1;
    const phases = this.getAllPhases();
    const nextPhase = nextPhaseIndex < phases.length ? phases[nextPhaseIndex] : null;
    const entryCriteriaMet = nextPhase ? this.checkEntryCriteria(nextPhase, context) : true;
    
    // Check required artifacts
    const requiredArtifactsComplete = this.checkRequiredArtifacts(phase, context.artifacts);
    
    // Check required approvals
    const requiredApprovalsObtained = this.checkRequiredApprovals(phase, context.approvals);
    
    // Check evidence
    const evidenceSufficient = this.checkEvidenceSufficiency(phase, context.evidence);
    
    // Calculate readiness score
    const score = this.calculateReadinessScore({
      exitCriteriaMet,
      entryCriteriaMet,
      requiredArtifactsComplete,
      requiredApprovalsObtained,
      evidenceSufficient,
    });
    
    const ready = exitCriteriaMet && entryCriteriaMet && requiredArtifactsComplete && 
                  requiredApprovalsObtained && evidenceSufficient;
    
    return {
      phase,
      ready,
      exitCriteriaMet,
      entryCriteriaMet,
      requiredArtifactsComplete,
      requiredApprovalsObtained,
      evidenceSufficient,
      score,
    };
  }

  /**
   * Get blockers preventing transition
   */
  public getBlockers(
    fromPhase: LifecyclePhaseId,
    toPhase: LifecyclePhaseId,
    context: PhaseContext
  ): Blocker[] {
    const blockers: Blocker[] = [];
    
    // Validate transition
    if (!isValidPhaseTransition(fromPhase, toPhase)) {
      blockers.push({
        id: `invalid-transition-${fromPhase}-${toPhase}`,
        type: 'permission',
        phase: fromPhase,
        severity: 'critical',
        message: `Invalid transition from ${fromPhase} to ${toPhase}`,
        resolution: 'Transition must follow allowed sequence',
        canBypass: false,
      });
      return blockers;
    }
    
    // Check exit criteria for current phase
    const exitBlockers = this.getExitCriteriaBlockers(fromPhase, context);
    blockers.push(...exitBlockers);
    
    // Check entry criteria for target phase
    const entryBlockers = this.getEntryCriteriaBlockers(toPhase, context);
    blockers.push(...entryBlockers);
    
    // Check artifact blockers
    const artifactBlockers = this.getArtifactBlockers(fromPhase, toPhase, context);
    blockers.push(...artifactBlockers);
    
    // Check approval blockers
    const approvalBlockers = this.getApprovalBlockers(toPhase, context);
    blockers.push(...approvalBlockers);
    
    // Check evidence blockers
    const evidenceBlockers = this.getEvidenceBlockers(toPhase, context);
    blockers.push(...evidenceBlockers);
    
    return blockers;
  }

  /**
   * Get transition rules
   */
  public getTransitionRule(fromPhase: LifecyclePhaseId, toPhase: LifecyclePhaseId): TransitionRule {
    const allowed = isValidPhaseTransition(fromPhase, toPhase);
    const toMetadata = getPhaseMetadata(toPhase);
    
    return {
      fromPhase,
      toPhase,
      allowed,
      requiresApproval: toMetadata?.exitRequirements.requiresApproval || 
                        this.config.requireApprovalForPhases.includes(toPhase),
      approverRoles: toMetadata?.exitRequirements.approverRoles || [],
      minimumEvidence: toMetadata?.exitRequirements.minimumEvidenceCount || 0,
      autoApproveConditions: this.getAutoApproveConditions(fromPhase, toPhase),
      bypassConditions: this.getBypassConditions(fromPhase, toPhase),
    };
  }

  /**
   * Get bypass conditions for a transition
   */
  public getBypassConditions(fromPhase: LifecyclePhaseId, toPhase: LifecyclePhaseId): BypassCondition[] {
    if (!this.config.bypassEnabled) {
      return [];
    }
    
    const conditions: BypassCondition[] = [];
    
    // Admin bypass for any transition
    conditions.push({
      condition: 'admin_override',
      allowedRoles: ['ADMIN', 'OWNER'],
      requiresReason: true,
      requiresApproval: false,
      auditLevel: 'enhanced',
    });
    
    // Emergency bypass for critical phases
    if (['VERIFY', 'INSTITUTIONALIZE'].includes(toPhase)) {
      conditions.push({
        condition: 'emergency_override',
        allowedRoles: ['OWNER'],
        requiresReason: true,
        requiresApproval: true,
        auditLevel: 'critical',
      });
    }
    
    return conditions;
  }

  /**
   * Get evidence requirements for a phase
   */
  public getEvidenceRequirements(phase: LifecyclePhaseId): EvidenceRequirement {
    const metadata = getPhaseMetadata(phase);
    
    return {
      phase,
      evidenceTypes: metadata?.keyArtifacts || [],
      minimumCount: metadata?.exitRequirements.minimumEvidenceCount || 0,
      requiredFor: 'exit',
      verificationRequired: ['PLAN', 'VERIFY', 'INSTITUTIONALIZE'].includes(phase),
    };
  }

  /**
   * Get approval requirements for a phase
   */
  public getApprovalRequirements(phase: LifecyclePhaseId): ApprovalRequirement {
    const metadata = getPhaseMetadata(phase);
    
    return {
      phase,
      approverRoles: metadata?.exitRequirements.approverRoles || [],
      minimumApprovals: 1,
      quorum: false,
      escalationPath: phase === 'INSTITUTIONALIZE' ? ['ADMIN', 'OWNER'] : undefined,
    };
  }

  /**
   * Check if bypass is available for a transition
   */
  public canBypass(
    fromPhase: LifecyclePhaseId,
    toPhase: LifecyclePhaseId,
    userRole: string
  ): { allowed: boolean; reason?: string } {
    if (!this.config.bypassEnabled) {
      return { allowed: false, reason: 'Bypass is disabled' };
    }
    
    const bypassConditions = this.getBypassConditions(fromPhase, toPhase);
    const applicableBypass = bypassConditions.find(bc => bc.allowedRoles.includes(userRole));
    
    if (applicableBypass) {
      return { allowed: true, reason: `Bypass available via ${applicableBypass.condition}` };
    }
    
    return { allowed: false, reason: 'No bypass available for your role' };
  }

  /**
   * Generate audit event for lifecycle action
   */
  public generateAuditEvent(
    action: string,
    projectId: string,
    actor: string,
    details: Record<string, unknown>
  ): AuditEvent {
    return {
      id: `audit-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      action,
      projectId,
      actor,
      timestamp: new Date(),
      details,
      severity: this.config.auditLevel === 'critical' ? 'error' : 'info',
    };
  }

  // ============================================================================
  // Private Helper Methods
  // ============================================================================

  private checkExitCriteria(phase: LifecyclePhaseId, context: PhaseContext): boolean {
    const metadata = getPhaseMetadata(phase);
    if (!metadata) return false;
    
    // Check each gate (exit criteria)
    for (const gate of metadata.gates) {
      if (!context.completedCriteria.includes(gate)) {
        return false;
      }
    }
    
    return true;
  }

  private checkEntryCriteria(phase: LifecyclePhaseId, context: PhaseContext): boolean {
    // Entry criteria are the previous phase's gates
    const phaseOrder = this.getPhaseOrder(phase);
    if (phaseOrder === 0) return true; // First phase has no entry criteria
    
    const phases = this.getAllPhases();
    const previousPhase = phases[phaseOrder - 1];
    const metadata = getPhaseMetadata(previousPhase);
    
    if (!metadata) return false;
    
    // Check each gate from previous phase as entry criteria
    for (const gate of metadata.gates) {
      if (!context.completedCriteria.includes(gate)) {
        return false;
      }
    }
    
    return true;
  }

  private checkRequiredArtifacts(phase: LifecyclePhaseId, artifacts: string[]): boolean {
    const metadata = getPhaseMetadata(phase);
    if (!metadata) return false;
    
    const keyArtifacts = metadata.keyArtifacts || [];
    return keyArtifacts.every((artifact: string) => artifacts.includes(artifact));
  }

  private checkRequiredApprovals(phase: LifecyclePhaseId, approvals: string[]): boolean {
    const metadata = getPhaseMetadata(phase);
    if (!metadata) return false;
    
    const requiredRoles = metadata.exitRequirements.approverRoles || [];
    return requiredRoles.some(role => approvals.includes(role));
  }

  private checkEvidenceSufficiency(phase: LifecyclePhaseId, evidence: Evidence[]): boolean {
    const requirement = this.getEvidenceRequirements(phase);
    return evidence.length >= requirement.minimumCount;
  }

  private calculateReadinessScore(metrics: {
    exitCriteriaMet: boolean;
    entryCriteriaMet: boolean;
    requiredArtifactsComplete: boolean;
    requiredApprovalsObtained: boolean;
    evidenceSufficient: boolean;
  }): number {
    const weights = {
      exitCriteriaMet: 0.3,
      entryCriteriaMet: 0.2,
      requiredArtifactsComplete: 0.2,
      requiredApprovalsObtained: 0.15,
      evidenceSufficient: 0.15,
    };
    
    let score = 0;
    for (const [key, value] of Object.entries(metrics)) {
      if (value) {
        score += weights[key as keyof typeof weights] * 100;
      }
    }
    
    return Math.round(score);
  }

  private getExitCriteriaBlockers(phase: LifecyclePhaseId, context: PhaseContext): Blocker[] {
    const blockers: Blocker[] = [];
    const metadata = getPhaseMetadata(phase);
    
    if (!metadata) return blockers;
    
    for (const gate of metadata.gates) {
      if (!context.completedCriteria.includes(gate)) {
        blockers.push({
          id: `exit-gate-${gate}`,
          type: 'exit_criteria',
          phase,
          severity: 'high',
          message: `Exit gate not met: ${gate}`,
          resolution: `Complete the ${gate} requirement`,
          canBypass: true,
          bypassRole: 'ADMIN',
        });
      }
    }
    
    return blockers;
  }

  private getEntryCriteriaBlockers(phase: LifecyclePhaseId, context: PhaseContext): Blocker[] {
    const blockers: Blocker[] = [];
    
    // Entry criteria are the previous phase's gates
    const phaseOrder = this.getPhaseOrder(phase);
    if (phaseOrder === 0) return blockers; // First phase has no entry criteria
    
    const phases = this.getAllPhases();
    const previousPhase = phases[phaseOrder - 1];
    const metadata = getPhaseMetadata(previousPhase);
    
    if (!metadata) return blockers;
    
    for (const gate of metadata.gates) {
      if (!context.completedCriteria.includes(gate)) {
        blockers.push({
          id: `entry-gate-${gate}`,
          type: 'entry_criteria',
          phase,
          severity: 'high',
          message: `Entry gate not met: ${gate}`,
          resolution: `Complete the ${gate} requirement from previous phase`,
          canBypass: true,
          bypassRole: 'ADMIN',
        });
      }
    }
    
    return blockers;
  }

  private getArtifactBlockers(fromPhase: LifecyclePhaseId, toPhase: LifecyclePhaseId, context: PhaseContext): Blocker[] {
    const blockers: Blocker[] = [];
    const toMetadata = getPhaseMetadata(toPhase);
    
    if (!toMetadata) return blockers;
    
    const keyArtifacts = toMetadata.keyArtifacts || [];
    for (const artifact of keyArtifacts) {
      if (!context.artifacts.includes(artifact)) {
        blockers.push({
          id: `artifact-${artifact}`,
          type: 'artifact',
          phase: toPhase,
          severity: 'critical',
          message: `Required artifact missing: ${artifact}`,
          resolution: `Create or complete the ${artifact}`,
          canBypass: false,
        });
      }
    }
    
    return blockers;
  }

  private getApprovalBlockers(phase: LifecyclePhaseId, context: PhaseContext): Blocker[] {
    const blockers: Blocker[] = [];
    const metadata = getPhaseMetadata(phase);
    
    if (!metadata) return blockers;
    
    const requiredRoles = metadata.exitRequirements.approverRoles || [];
    for (const role of requiredRoles) {
      if (!context.approvals.includes(role)) {
        blockers.push({
          id: `approval-${role}`,
          type: 'approval',
          phase,
          severity: 'high',
          message: `Approval required from ${role}`,
          resolution: `Obtain approval from a user with ${role} role`,
          canBypass: true,
          bypassRole: 'OWNER',
        });
      }
    }
    
    return blockers;
  }

  private getEvidenceBlockers(phase: LifecyclePhaseId, context: PhaseContext): Blocker[] {
    const blockers: Blocker[] = [];
    const requirement = this.getEvidenceRequirements(phase);
    
    if (context.evidence.length < requirement.minimumCount) {
      blockers.push({
        id: `evidence-count`,
        type: 'evidence',
        phase,
        severity: 'high',
        message: `Insufficient evidence: ${context.evidence.length}/${requirement.minimumCount} required`,
        resolution: `Add ${requirement.minimumCount - context.evidence.length} more evidence items`,
        canBypass: true,
        bypassRole: 'ADMIN',
        evidenceRequired: requirement.evidenceTypes,
      });
    }
    
    return blockers;
  }

  private getAutoApproveConditions(fromPhase: LifecyclePhaseId, toPhase: LifecyclePhaseId): string[] {
    const conditions: string[] = [];
    
    if (this.config.autoApproveLowRisk) {
      conditions.push('low_risk_transition');
    }
    
    if (!this.config.requireApprovalForPhases.includes(toPhase)) {
      conditions.push('no_approval_required');
    }
    
    return conditions;
  }

  private getPhaseOrder(phase: LifecyclePhaseId): number {
    const phases = this.getAllPhases();
    return phases.indexOf(phase);
  }

  private getAllPhases(): LifecyclePhaseId[] {
    return ['INTENT', 'CONTEXT', 'PLAN', 'EXECUTE', 'VERIFY', 'OBSERVE', 'LEARN', 'INSTITUTIONALIZE'];
  }
}

// ============================================================================
// Supporting Types
// ============================================================================

export interface PhaseContext {
  artifacts: string[];
  completedCriteria: string[];
  approvals: string[];
  evidence: Evidence[];
  userId: string;
  userRole: string;
}

export interface Evidence {
  id: string;
  type: string;
  title: string;
  url?: string;
}

export interface AuditEvent {
  id: string;
  action: string;
  projectId: string;
  actor: string;
  timestamp: Date;
  details: Record<string, unknown>;
  severity: 'info' | 'warn' | 'error';
}

// ============================================================================
// Singleton Instance
// ============================================================================

export const lifecyclePolicyService = new LifecyclePolicyService();
