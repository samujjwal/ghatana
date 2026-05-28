import { describe, expect, it, vi } from 'vitest';

import type { PhaseAction } from '../../../../types/phasePacket';
import type { PhaseCockpitPacket } from '../../../../types/phasePacket';
import {
  mapPacketActivity,
  mapPacketBlockers,
  mapPacketEvidence,
  mapPacketGovernance,
  mapPacketSuggestions,
  phasePacketToPreview,
} from '../phasePacketMappers';

function packetFixture(): PhaseCockpitPacket {
  return {
    phase: 'shape',
    projectId: 'proj-1',
    projectName: 'Project One',
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    workspaceName: 'Workspace One',
    actor: {
      actorId: 'user-1',
      actorName: 'User One',
      role: 'owner',
      isOwner: true,
      isAdmin: true,
    },
    lifecyclePhase: 'SHAPE',
    tenantTier: 'PRO',
    enabledPhaseFlags: ['shape', 'validate'],
    capabilities: {
      canRead: true,
      canCreate: true,
      canUpdate: true,
      canDelete: false,
      canApprove: true,
      canReject: false,
      canRollback: false,
    },
    blockers: [{
      id: 'blocker-1',
      type: 'policy',
      title: 'Policy blocker',
      description: 'Pending policy review',
      severity: 'WARNING',
      resourceId: 'resource-1',
      resolvable: true,
    }],
    readiness: {
      canAdvance: false,
      nextPhase: 'VALIDATE',
      missingPrerequisites: ['Policy review'],
      completenessScore: 0.65,
      isDegraded: false,
      estimatedReadyIn: '2h',
      estimatedReadyInHours: 2,
      predictionConfidence: 0.7,
    },
    requiredArtifacts: [],
    completedArtifacts: [],
    activityFeed: [{
      id: 'activity-1',
      type: 'audit',
      action: 'shape.review',
      summary: 'Shape review completed',
      actor: 'user-1',
      timestamp: '2026-05-26T11:00:00.000Z',
      severity: 'INFO',
      eventType: 'PHASE_ACTION_EXECUTED',
      success: true,
      outcome: 'SUCCESS',
      correlationId: 'corr-1',
    }],
    evidence: [{
      id: 'evidence-1',
      type: 'artifact',
      title: 'Shape Evidence',
      description: 'Shape evidence item',
      timestamp: '2026-05-26T11:00:00.000Z',
      metadata: {},
      evidenceId: 'evidence-1',
    }],
    governance: [{
      id: 'gov-1',
      type: 'derived',
      outcome: 'approved',
      actor: 'system',
      timestamp: '2026-05-26T11:00:00.000Z',
      metadata: {},
      policyDecisionId: 'policy-1',
    }],
    platformRunStatus: {
      runId: 'run-1',
      status: 'RUNNING',
      platform: 'kernel',
      startedAt: '2026-05-26T11:00:00.000Z',
      traceId: 'trace-1',
      evidenceIds: [],
    },
    availableActions: [{
      actionId: 'shape-primary',
      label: 'phaseAction.shape.primary',
      description: 'phaseAction.shape.primary.description',
      enabled: true,
      requiredPermission: 'shape:update',
      category: 'phase-transition',
      severity: 'medium',
      confirmationRequired: false,
      idempotencyKey: 'shape-primary',
      auditType: 'shape.primary.requested',
      parameters: {
        confidence: 0.91,
        evidence: ['shape-evidence-1'],
        riskLevel: 'low',
        applyMode: 'one-click',
        approvalRequired: false,
        rollbackSupported: true,
      },
    }],
    dashboardActions: {
      primaryAction: 'shape-primary',
      blockedActions: [],
      reviewRequiredActions: [],
      safeToContinueActions: ['shape-primary'],
    },
    phasePanels: [],
    healthSignals: {
      preview: {
        isHealthy: true,
        status: 'healthy',
        issues: [],
      },
      generation: {
        isHealthy: true,
        status: 'healthy',
        issues: [],
      },
      runtime: {
        isHealthy: true,
        status: 'healthy',
        issues: [],
      },
    },
    timestamp: Date.parse('2026-05-26T11:00:00.000Z'),
    correlationId: 'corr-1',
  };
}

describe('phasePacketMappers', () => {
  it('maps packet preview and activity payloads', () => {
    const packet = packetFixture();

    const preview = phasePacketToPreview(packet);
    const activity = mapPacketActivity(packet);

    expect(preview.projectId).toBe('proj-1');
    expect(preview.currentPhase).toBe('SHAPE');
    expect(preview.readiness).toBe(65);
    expect(activity[0]?.eventType).toBe('PHASE_ACTION_EXECUTED');
    expect(activity[0]?.correlationId).toBe('corr-1');
  });

  it('maps blockers, evidence, and governance records for cockpit components', () => {
    const packet = packetFixture();

    const blockers = mapPacketBlockers(packet);
    const evidence = mapPacketEvidence(packet);
    const governance = mapPacketGovernance(packet);

    expect(blockers[0]?.severity).toBe('medium');
    expect(evidence[0]?.type).toBe('artifact');
    expect(governance[0]?.source).toBe('derived');
  });

  it('maps suggestions and preserves callback wiring for actions', () => {
    const packet = packetFixture();
    const onAccept = vi.fn<(action: PhaseAction) => void>();

    const suggestions = mapPacketSuggestions(packet, (value) => value, onAccept);

    expect(suggestions[0]?.title).toBe('phaseAction.shape.primary');
    expect(suggestions[0]?.confidence).toBe(0.91);
    expect(suggestions[0]?.riskLevel).toBe('low');
    expect(suggestions[0]?.applyMode).toBe('one-click');
    suggestions[0]?.onAccept(suggestions[0]);
    expect(onAccept).toHaveBeenCalledWith(packet.availableActions[0]);
  });

  it('does not synthesize suggestion metadata when backend metadata is missing', () => {
    const packet = packetFixture();
    const onAccept = vi.fn<(action: PhaseAction) => void>();
    const actionWithoutMetadata: PhaseAction = {
      ...packet.availableActions[0],
      actionId: 'shape-no-metadata',
      parameters: {},
    };

    const suggestions = mapPacketSuggestions(
      { ...packet, availableActions: [actionWithoutMetadata] },
      (value) => value,
      onAccept,
    );

    expect(suggestions).toHaveLength(0);
    expect(onAccept).not.toHaveBeenCalled();
  });
});
