import { describe, expect, it } from 'vitest';

import {
  canPhaseAdvance,
  createPhasePacketCorrelationId,
  getBlockedActions,
  getPrimaryAction,
  isActionEnabled,
  normalizePhaseCockpitPacket,
} from '../usePhasePacket';

describe('usePhasePacket helpers', () => {
  it('isActionEnabled returns false when backend action is disabled', () => {
    expect(isActionEnabled({ enabled: false })).toBe(false);
  });

  it('isActionEnabled returns false when backend provides disabled reason', () => {
    expect(isActionEnabled({ enabled: true, disabledReason: 'Policy denied' })).toBe(false);
  });

  it('isActionEnabled returns true only for enabled actions without disable reason', () => {
    expect(isActionEnabled({ enabled: true })).toBe(true);
  });

  it('getPrimaryAction resolves by backend primary action id', () => {
    const primary = getPrimaryAction(
      { primaryAction: 'advance-phase', blockedActions: [] },
      [
        { actionId: 'configure-phase', label: 'Configure', enabled: true },
        { actionId: 'advance-phase', label: 'Advance', enabled: false },
      ]
    );

    expect(primary).not.toBeNull();
    expect(primary?.actionId).toBe('advance-phase');
    expect(primary?.enabled).toBe(false);
  });

  it('getBlockedActions maps only backend blocked action ids', () => {
    const blocked = getBlockedActions(
      { blockedActions: ['advance-phase', 'missing-action'] },
      [
        { actionId: 'advance-phase', label: 'Advance' },
        { actionId: 'configure-phase', label: 'Configure' },
      ]
    );

    expect(blocked).toEqual([{ actionId: 'advance-phase', label: 'Advance' }]);
  });

  it('canPhaseAdvance requires non-degraded readiness', () => {
    expect(canPhaseAdvance({ canAdvance: true, isDegraded: false })).toBe(true);
    expect(canPhaseAdvance({ canAdvance: true, isDegraded: true })).toBe(false);
    expect(canPhaseAdvance({ canAdvance: false, isDegraded: false })).toBe(false);
  });

  it('preserves caller supplied phase packet correlation IDs', () => {
    expect(createPhasePacketCorrelationId({
      phase: 'shape',
      projectId: 'project-1',
      workspaceId: 'workspace-1',
      correlationId: 'corr-user-1',
    })).toBe('corr-user-1');
  });

  it('generates traceable phase packet correlation IDs when absent', () => {
    const correlationId = createPhasePacketCorrelationId({
      phase: 'shape',
      projectId: 'project-1',
      workspaceId: 'workspace-1',
    });

    expect(correlationId).toContain('phase-packet:shape:project-1:');
  });

  it('preserves backend phase panels that are absent from the generated client type', () => {
    const packet = normalizePhaseCockpitPacket({
      phase: 'shape',
      projectId: 'project-1',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      actor: {
        actorId: 'user-1',
        actorName: 'User One',
        role: 'OWNER',
        isOwner: true,
        isAdmin: false,
      },
      tenantTier: 'PRO',
      enabledPhaseFlags: [],
      capabilities: {
        canRead: true,
        canCreate: true,
        canUpdate: true,
        canDelete: false,
        canApprove: true,
        canReject: true,
        canRollback: false,
      },
      blockers: [],
      readiness: {
        canAdvance: true,
        nextPhase: 'validate',
        missingPrerequisites: [],
        completenessScore: 0.9,
        isDegraded: false,
      },
      requiredArtifacts: [],
      completedArtifacts: [],
      activityFeed: [],
      evidence: [],
      governance: [],
      availableActions: [],
      dashboardActions: {
        primaryAction: 'shape.approve',
        blockedActions: [],
        reviewRequiredActions: [],
        safeToContinueActions: [],
      },
      healthSignals: {
        preview: { isHealthy: true, status: 'ready', issues: [] },
        generation: { isHealthy: true, status: 'ready', issues: [] },
        runtime: { isHealthy: true, status: 'ready', issues: [] },
      },
      timestamp: 1,
      phasePanels: [{
        phase: 'shape',
        status: 'ready',
        summary: 'Architecture model is ready',
        recommendation: 'Advance to validation',
        owner: 'architecture',
        confidence: 0.92,
        supportTrace: 'trace-1',
        cards: [],
      }],
    });

    expect(packet.phasePanels).toHaveLength(1);
    expect(packet.phasePanels[0]?.summary).toBe('Architecture model is ready');
  });
});
