import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { PhaseCockpitPacket } from '../../../../types/phasePacket';
import { usePhaseCockpitViewModel } from '../usePhaseCockpitViewModel';

function packetFixture(overrides: Partial<PhaseCockpitPacket> = {}): PhaseCockpitPacket {
  return {
    phase: 'validate',
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
    lifecyclePhase: 'VALIDATE',
    tenantTier: 'PRO',
    enabledPhaseFlags: ['validate'],
    capabilities: {
      canRead: true,
      canCreate: true,
      canUpdate: true,
      canDelete: false,
      canApprove: true,
      canReject: false,
      canRollback: false,
    },
    blockers: [],
    readiness: {
      canAdvance: true,
      nextPhase: 'GENERATE',
      missingPrerequisites: [],
      completenessScore: 0.9,
      isDegraded: false,
    },
    requiredArtifacts: [],
    completedArtifacts: [],
    activityFeed: [],
    evidence: [],
    governance: [],
    availableActions: [
      {
        actionId: 'validate.approve',
        label: 'phaseAction.validate.approve',
        description: 'Approve validation',
        enabled: true,
        requiredPermission: 'PROJECT_APPROVE',
        category: 'primary',
        severity: 'default',
        confirmationRequired: false,
        idempotencyKey: 'k1',
        auditType: 'a1',
        targetType: 'server',
        parameters: {},
      },
      {
        actionId: 'validate.reject',
        label: 'phaseAction.validate.reject',
        description: 'Reject validation',
        enabled: true,
        requiredPermission: 'PROJECT_APPROVE',
        category: 'review',
        severity: 'warning',
        confirmationRequired: true,
        idempotencyKey: 'k2',
        auditType: 'a2',
        targetType: 'server',
        parameters: {},
      },
    ],
    dashboardActions: {
      primaryAction: 'validate.approve',
      blockedActions: [],
      reviewRequiredActions: [],
      safeToContinueActions: [],
    },
    healthSignals: {
      preview: { isHealthy: true, status: 'healthy', issues: [] },
      generation: { isHealthy: true, status: 'healthy', issues: [] },
      runtime: { isHealthy: true, status: 'healthy', issues: [] },
    },
    timestamp: Date.now(),
    ...overrides,
  };
}

describe('usePhaseCockpitViewModel', () => {
  it('builds actionable primary state for healthy packet', () => {
    const t = vi.fn((key: string) => key);
    const actionText = vi.fn((value: string | undefined) => value?.replace('phaseAction.', ''));

    const { result } = renderHook(() => usePhaseCockpitViewModel({
      phase: 'validate',
      packet: packetFixture(),
      actionText,
      isActionPending: () => false,
      handleSuggestionAction: vi.fn(),
      mutationPending: false,
      t,
    }));

    expect(result.current.isActionAvailable).toBe(true);
    expect(result.current.primaryPacketAction?.actionId).toBe('validate.approve');
    expect(result.current.primaryNextActionLabel).toBe('validate.approve');
  });

  it('exposes degraded disabled reason when packet is degraded', () => {
    const { result } = renderHook(() => usePhaseCockpitViewModel({
      phase: 'validate',
      packet: packetFixture({
        readiness: {
          canAdvance: false,
          nextPhase: 'GENERATE',
          missingPrerequisites: ['missing artifact'],
          completenessScore: 0.5,
          isDegraded: true,
        },
        degradedDetails: {
          dependency: 'Data Cloud',
          reason: 'Dependency unavailable',
          truthSource: 'Data Cloud',
          recoveryAction: 'Retry after dependency recovers',
          impactedFeatures: ['phase-packet'],
        },
      }),
      actionText: (value) => value,
      isActionPending: () => false,
      handleSuggestionAction: vi.fn(),
      mutationPending: false,
      t: (key) => key,
    }));

    expect(result.current.isDependencyDegraded).toBe(true);
    expect(result.current.isActionAvailable).toBe(false);
    expect(result.current.primaryActionDisabledReason).toBe('Retry after dependency recovers');
  });

  it('groups non-primary actions into action sections', () => {
    const { result } = renderHook(() => usePhaseCockpitViewModel({
      phase: 'validate',
      packet: packetFixture(),
      actionText: (value) => value,
      isActionPending: () => false,
      handleSuggestionAction: vi.fn(),
      mutationPending: false,
      t: (key) => key,
    }));

    expect(result.current.actionSections).toHaveLength(1);
    expect(result.current.actionSections[0]?.testId).toBe('generate-review-actions');
    expect(result.current.actionSections[0]?.actions[0]?.actionId).toBe('validate.reject');
  });
});
