import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const phaseServiceMocks = vi.hoisted(() => ({
  executePhasePrimaryAction: vi.fn(),
  executeGenerateReviewDecision: vi.fn(),
  executeRunPostAction: vi.fn(),
  describePhaseActionError: vi.fn(() => 'phase action failed'),
}));

vi.mock('../../../../services/phase', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../services/phase')>();
  return {
    ...actual,
    executePhasePrimaryAction: phaseServiceMocks.executePhasePrimaryAction,
    executeGenerateReviewDecision: phaseServiceMocks.executeGenerateReviewDecision,
    executeRunPostAction: phaseServiceMocks.executeRunPostAction,
    describePhaseActionError: phaseServiceMocks.describePhaseActionError,
  };
});

import type { PhaseCockpitPacket } from '../../../../types/phasePacket';
import { usePhaseActionHandlers } from '../usePhaseActionHandlers';

function wrapperFactory() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: { readonly children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

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
    availableActions: [],
    dashboardActions: {
      primaryAction: '',
      blockedActions: [],
      reviewRequiredActions: [],
      safeToContinueActions: [],
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
    timestamp: Date.now(),
    ...overrides,
  };
}

describe('usePhaseActionHandlers', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    phaseServiceMocks.executePhasePrimaryAction.mockResolvedValue({
      kind: 'surface',
      message: 'ok',
    });
    phaseServiceMocks.executeGenerateReviewDecision.mockResolvedValue({
      kind: 'generate-review',
      message: 'ok',
      runId: 'run-1',
    });
    phaseServiceMocks.executeRunPostAction.mockResolvedValue({
      kind: 'run-workflow',
      message: 'ok',
      runId: 'run-1',
    });
  });

  it('sets missing project context error when primary action is triggered without projectId', () => {
    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'shape',
      projectId: undefined,
      packet: packetFixture({ phase: 'shape', lifecyclePhase: 'SHAPE' }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    act(() => {
      result.current.handlePrimaryAction();
    });

    expect(result.current.actionError).toBe('phaseCockpit.errors.missingProjectContext');
  });

  it('sets validate lifecycle preview unavailable error when packet lacks lifecycle preview data', () => {
    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'validate',
      projectId: 'proj-1',
      packet: packetFixture({ lifecyclePhase: undefined, readiness: { ...packetFixture().readiness, nextPhase: null } }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    act(() => {
      result.current.handlePrimaryAction();
    });

    expect(result.current.actionError).toBe('phaseCockpit.errors.lifecyclePreviewUnavailable');
  });

  it('requires authenticated reviewer for generate review decisions', async () => {
    phaseServiceMocks.executePhasePrimaryAction.mockResolvedValueOnce({
      kind: 'generate-review',
      message: 'review pending',
      runId: 'run-1',
      reviewRequired: true,
    });

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'generate',
      projectId: 'proj-1',
      packet: packetFixture({ phase: 'generate', lifecyclePhase: 'GENERATE' }),
      currentUser: null,
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    await act(async () => {
      result.current.handlePrimaryAction();
    });

    await waitFor(() => {
      expect(result.current.actionResult?.runId).toBe('run-1');
    });

    act(() => {
      result.current.handleGenerateReviewDecision('apply');
    });

    expect(result.current.actionError).toBe('phaseCockpit.errors.authenticatedReviewerRequired');
  });
});
