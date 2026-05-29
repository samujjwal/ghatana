import type { Blocker } from '../../../components/phase/PhaseBlockerPanel';
import type { EvidenceItem } from '../../../components/phase/PhaseEvidencePanel';
import type { GovernanceRecord as GovernanceTraceRecord } from '../../../components/phase/PhaseGovernanceTrace';
import type { SuggestedStep } from '../../../components/phase/PhaseSuggestedNextStep';
import type {
  PhaseAction,
  PhaseCockpitPacket,
} from '../../../types/phasePacket';
import type {
  PhaseActivityEvent,
  PhaseTransitionPreviewSnapshot,
} from '../../../services/phase';

export function phaseBlockerSeverity(severity: string): Blocker['severity'] {
  if (severity === 'CRITICAL') {
    return 'critical';
  }
  if (severity === 'ERROR' || severity === 'HIGH') {
    return 'high';
  }
  if (severity === 'WARNING' || severity === 'MEDIUM') {
    return 'medium';
  }
  return 'low';
}

export function phasePacketToPreview(packet: PhaseCockpitPacket): PhaseTransitionPreviewSnapshot {
  return {
    projectId: packet.projectId,
    currentPhase: packet.lifecyclePhase ?? '',
    nextPhase: packet.readiness.nextPhase ?? null,
    canAdvance: packet.readiness.canAdvance,
    readiness: Math.round(packet.readiness.completenessScore * 100),
    blockers: packet.blockers.map((blocker) => blocker.title),
    requiredArtifacts: packet.requiredArtifacts.map((artifact) => artifact.title),
    completedArtifacts: packet.completedArtifacts.map((artifact) => artifact.title),
    estimatedReadyIn: packet.readiness.estimatedReadyIn ?? null,
    estimatedReadyInHours: packet.readiness.estimatedReadyInHours ?? null,
    predictionConfidence: packet.readiness.predictionConfidence ?? null,
    checkedAt: new Date(packet.timestamp).toISOString(),
  };
}

export function mapPacketBlockers(packet: PhaseCockpitPacket): Blocker[] {
  return packet.blockers.map((blocker) => ({
    id: blocker.id,
    title: blocker.title,
    severity: phaseBlockerSeverity(blocker.severity),
    description: blocker.description,
    source: blocker.type,
  }));
}

export function mapPacketEvidence(packet: PhaseCockpitPacket): EvidenceItem[] {
  return packet.evidence.map((evidence) => ({
    id: evidence.id,
    type: (evidence.type as EvidenceItem['type']) || 'observation',
    title: evidence.title,
    description: evidence.description,
    timestamp: new Date(evidence.timestamp).toISOString(),
    source: evidence.evidenceId,
  }));
}

export function mapPacketGovernance(packet: PhaseCockpitPacket): GovernanceTraceRecord[] {
  return packet.governance.map((governance) => ({
    id: governance.id,
    artifactId: governance.id,
    action: governance.type,
    actor: governance.actor,
    source: (governance.type as GovernanceTraceRecord['source']) || 'derived',
    timestamp: new Date(governance.timestamp).toISOString(),
    metadata: governance.metadata as GovernanceTraceRecord['metadata'],
    reviewState: undefined,
    system: undefined,
    confidence: undefined,
  }));
}

export function mapPacketSuggestions(
  packet: PhaseCockpitPacket,
  actionText: (value: string | undefined) => string | undefined,
  onAccept: (action: PhaseAction) => void,
): SuggestedStep[] {
  // FE-02: Type-safe validation helper functions
  const isValidRiskLevel = (value: unknown): value is 'low' | 'medium' | 'high' =>
    value === 'low' || value === 'medium' || value === 'high';

  const isValidApplyMode = (value: unknown): value is 'one-click' | 'manual' | 'review-required' =>
    value === 'one-click' || value === 'manual' || value === 'review-required';

  const isValidStepType = (value: unknown): value is 'automation' | 'manual' | 'review' =>
    value === 'automation' || value === 'manual' || value === 'review';

  const parseSuggestionMetadata = (action: PhaseAction): Omit<SuggestedStep, 'id' | 'title' | 'description' | 'type' | 'onAccept'> | null => {
    const confidence = Number.isFinite(action.parameters?.confidence)
      ? Number(action.parameters?.confidence)
      : 0.5; // Default to 0.5 if not provided
    // FE-01: Fix key mismatch - backend sends evidenceIds, not evidence
    const evidence = Array.isArray(action.parameters?.evidenceIds)
      ? action.parameters.evidenceIds.filter((item): item is string => typeof item === 'string')
      : null;
    // FE-01: Fix key mismatch - backend sends riskReason, not riskLevel
    const riskReason = action.parameters?.riskReason;
    const applyMode = action.parameters?.applyMode;
    const approvalRequired = action.parameters?.approvalRequired;
    const rollbackSupported = action.parameters?.rollbackSupported;

    // Make validation more lenient - only require evidence to be present
    if (evidence == null) {
      return null;
    }

    // FE-02: Type-safe validation with proper type guards
    // riskReason from backend maps to riskLevel in frontend
    const validatedRiskLevel = isValidRiskLevel(riskReason) ? riskReason : 'medium';
    const validatedApplyMode = isValidApplyMode(applyMode) ? applyMode : 'manual';
    const validatedApprovalRequired = typeof approvalRequired === 'boolean' ? approvalRequired : false;
    const validatedRollbackSupported = typeof rollbackSupported === 'boolean' ? rollbackSupported : false;

    return {
      confidence,
      evidence,
      riskLevel: validatedRiskLevel,
      applyMode: validatedApplyMode,
      approvalRequired: validatedApprovalRequired,
      rollbackSupported: validatedRollbackSupported,
      estimatedTime: undefined,
    };
  };

  return packet.availableActions
    .map((action): SuggestedStep | null => {
      const metadata = parseSuggestionMetadata(action);
      if (!metadata) {
        return null;
      }

      return {
        id: action.actionId,
        title: actionText(action.label) ?? action.actionId,
        // FE-02: Type-safe step type validation
        type: metadata.approvalRequired ? 'review' : (isValidStepType(metadata.applyMode) ? metadata.applyMode : 'manual'),
        description: actionText(action.description) ?? '',
        ...metadata,
        onAccept: () => {
          onAccept(action);
        },
      };
    })
    .filter((step): step is SuggestedStep => step != null);
}

export function mapPacketActivity(packet: PhaseCockpitPacket): PhaseActivityEvent[] {
  return packet.activityFeed.map((entry) => ({
    id: entry.id,
    source: 'lifecycle',
    action: entry.action,
    summary: entry.summary,
    timestamp: new Date(entry.timestamp).toISOString(),
    actor: entry.actor,
    severity: entry.severity,
    success: entry.success ?? null,
    eventType: entry.eventType ?? entry.type,
    outcome: entry.outcome ?? (entry.success === false ? 'FAILURE' : 'SUCCESS'),
    correlationId: entry.correlationId ?? null,
  }));
}
