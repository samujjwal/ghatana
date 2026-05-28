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
    availableActions: [{
      actionId: 'validate-primary',
      label: 'phaseAction.validate.primary',
      description: 'phaseAction.validate.primary.description',
      enabled: true,
      requiredPermission: 'phase:advance',
      category: 'phase-transition',
      severity: 'high',
      confirmationRequired: true,
      idempotencyKey: 'phase.advance',
      auditType: 'phase.advance.requested',
      targetType: 'server',
      requiresPreview: true,
      serverOperation: 'phase.advance',
      postSuccessBehavior: 'refresh-packet',
      parameters: {
        confidence: 0.9,
        evidence: [],
        riskLevel: 'medium',
        applyMode: 'review-required',
        approvalRequired: true,
        rollbackSupported: true,
      },
    }],
    dashboardActions: {
      primaryAction: 'validate-primary',
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

  it('navigates for route-target actions', () => {
    const navigate = vi.fn();
    const routeAction = {
      ...packetFixture().availableActions[0],
      actionId: 'intent.route',
      targetType: 'route',
      targetRoute: 'intent',
      requiresPreview: false,
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'intent',
      projectId: 'proj-1',
      packet: packetFixture({ phase: 'intent', lifecyclePhase: 'INTENT', availableActions: [routeAction] }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1' },
      t: (key: string) => key,
      navigate,
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    act(() => {
      result.current.handlePrimaryAction();
    });

    expect(navigate).toHaveBeenCalledWith('/p/proj-1/intent');
  });

  it('opens drawer for drawer-target actions', () => {
    const navigate = vi.fn();
    const drawerAction = {
      ...packetFixture().availableActions[0],
      actionId: 'intent.drawer',
      targetType: 'drawer',
      targetRoute: 'intent',
      targetDrawer: 'idea',
      requiresPreview: false,
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'intent',
      projectId: 'proj-1',
      packet: packetFixture({ phase: 'intent', lifecyclePhase: 'INTENT', availableActions: [drawerAction] }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1' },
      t: (key: string) => key,
      navigate,
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    act(() => {
      result.current.handlePrimaryAction();
    });

    expect(navigate).toHaveBeenCalledWith('/p/proj-1/intent?drawer=idea');
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

  it('shows review feedback for disabled suggestion actions', () => {
    const scrollToSupportingSurface = vi.fn();
    const action = {
      ...packetFixture().availableActions[0],
      actionId: 'shape.review',
      label: 'Shape review',
      enabled: false,
      disabledReason: 'blocked',
      requiresPreview: false,
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'shape',
      projectId: 'proj-1',
      packet: packetFixture({ phase: 'shape', lifecyclePhase: 'SHAPE', availableActions: [action] }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface,
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    act(() => {
      result.current.handleSuggestionAction(action);
    });

    expect(result.current.feedback).toContain('phaseCockpit.feedback.reviewingAction');
    expect(scrollToSupportingSurface).toHaveBeenCalled();
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

  it('routes server operations to review mutation when run context exists', async () => {
    phaseServiceMocks.executeGenerateReviewDecision.mockResolvedValueOnce({
      kind: 'generate-review',
      message: 'review applied',
      runId: 'run-1',
      reviewRequired: false,
    });

    const primaryAction = {
      ...packetFixture().availableActions[0],
      actionId: 'generate-primary',
      targetType: 'server',
      serverOperation: 'generate.start',
      requiresPreview: false,
    };

    const reviewAction = {
      ...packetFixture().availableActions[0],
      actionId: 'generate.apply',
      targetType: 'server',
      serverOperation: 'generate.apply',
      requiresPreview: false,
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'generate',
      projectId: 'proj-1',
      packet: packetFixture({
        phase: 'generate',
        lifecyclePhase: 'GENERATE',
        availableActions: [primaryAction, reviewAction],
        dashboardActions: {
          ...packetFixture().dashboardActions,
          primaryAction: 'generate-primary',
        },
      }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1', email: 'user-1@example.com' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    await act(async () => {
      phaseServiceMocks.executePhasePrimaryAction.mockResolvedValueOnce({
        kind: 'generate-review',
        message: 'review pending',
        runId: 'run-1',
        reviewRequired: true,
      });
      result.current.handlePrimaryAction();
    });

    await waitFor(() => {
      expect(result.current.actionResult?.runId).toBe('run-1');
    });

    await act(async () => {
      result.current.handleSuggestionAction(reviewAction);
    });

    await waitFor(() => {
      expect(phaseServiceMocks.executeGenerateReviewDecision).toHaveBeenCalled();
      expect(phaseServiceMocks.executeGenerateReviewDecision.mock.calls[0]?.[0]).toEqual(expect.objectContaining({
        runId: 'run-1',
        decision: 'apply',
      }));
    });
  });

  it('uses packet.platformRunStatus.runId when actionResult.runId is missing', async () => {
    const packetWithRunStatus = packetFixture({
      platformRunStatus: {
        runId: 'platform-run-123',
        status: 'RUNNING',
        platform: 'yappc',
        startedAt: new Date().toISOString(),
        traceId: 'trace-123',
        evidenceIds: [],
      },
    });

    const reviewAction = {
      ...packetFixture().availableActions[0],
      actionId: 'generate.apply',
      targetType: 'server',
      serverOperation: 'generate.apply',
      requiresPreview: false,
      parameters: {},
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'generate',
      projectId: 'proj-1',
      packet: packetWithRunStatus,
      currentUser: { id: 'user-1', tenantId: 'tenant-1', email: 'user-1@example.com' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    await act(async () => {
      result.current.handleSuggestionAction(reviewAction);
    });

    await waitFor(() => {
      expect(phaseServiceMocks.executeGenerateReviewDecision).toHaveBeenCalled();
      expect(phaseServiceMocks.executeGenerateReviewDecision.mock.calls[0]?.[0]).toEqual(expect.objectContaining({
        runId: 'platform-run-123',
      }));
    });
  });

  it('uses action.parameters.runId when available', async () => {
    const reviewAction = {
      ...packetFixture().availableActions[0],
      actionId: 'generate.apply',
      targetType: 'server',
      serverOperation: 'generate.apply',
      requiresPreview: false,
      parameters: { runId: 'action-param-run-456' },
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'generate',
      projectId: 'proj-1',
      packet: packetFixture(),
      currentUser: { id: 'user-1', tenantId: 'tenant-1', email: 'user-1@example.com' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    await act(async () => {
      phaseServiceMocks.executePhasePrimaryAction.mockResolvedValueOnce({
        kind: 'generate-review',
        message: 'review pending',
        runId: 'run-1',
        reviewRequired: true,
      });
      result.current.handlePrimaryAction();
    });

    await waitFor(() => {
      expect(result.current.actionResult?.runId).toBe('run-1');
    });

    await act(async () => {
      result.current.handleSuggestionAction(reviewAction);
    });

    await waitFor(() => {
      expect(phaseServiceMocks.executeGenerateReviewDecision).toHaveBeenCalled();
      expect(phaseServiceMocks.executeGenerateReviewDecision.mock.calls[0]?.[0]).toEqual(expect.objectContaining({
        runId: 'action-param-run-456',
      }));
    });
  });

  it('passes targetVersion and targetEnvironment from action parameters for run post-actions', async () => {
    const rollbackAction = {
      ...packetFixture().availableActions[0],
      actionId: 'run.rollback',
      targetType: 'server',
      serverOperation: 'run.rollback',
      requiresPreview: false,
      parameters: { targetVersion: 'v1.2.3', targetEnvironment: 'production' },
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'run',
      projectId: 'proj-1',
      packet: packetFixture({ phase: 'run', lifecyclePhase: 'RUN' }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    await act(async () => {
      phaseServiceMocks.executePhasePrimaryAction.mockResolvedValueOnce({
        kind: 'run-workflow',
        message: 'run started',
        runId: 'run-1',
      });
      result.current.handlePrimaryAction();
    });

    await waitFor(() => {
      expect(result.current.actionResult?.runId).toBe('run-1');
    });

    await act(async () => {
      result.current.handleSuggestionAction(rollbackAction);
    });

    await waitFor(() => {
      expect(phaseServiceMocks.executeRunPostAction).toHaveBeenCalled();
      expect(phaseServiceMocks.executeRunPostAction.mock.calls[0]?.[0]).toEqual(expect.objectContaining({
        targetVersion: 'v1.2.3',
        targetEnvironment: 'production',
      }));
    });
  });

  it('passes undefined targetVersion and targetEnvironment when not in action parameters', async () => {
    const retryAction = {
      ...packetFixture().availableActions[0],
      actionId: 'run.retry',
      targetType: 'server',
      serverOperation: 'run.retry',
      requiresPreview: false,
      parameters: {},
    };

    const { result } = renderHook(() => usePhaseActionHandlers({
      phase: 'run',
      projectId: 'proj-1',
      packet: packetFixture({ phase: 'run', lifecyclePhase: 'RUN' }),
      currentUser: { id: 'user-1', tenantId: 'tenant-1' },
      t: (key: string) => key,
      navigate: vi.fn(),
      refetch: vi.fn(async () => undefined),
      scrollToSupportingSurface: vi.fn(),
      scrollToBlockerPanel: vi.fn(),
    }), { wrapper: wrapperFactory() });

    await act(async () => {
      phaseServiceMocks.executePhasePrimaryAction.mockResolvedValueOnce({
        kind: 'run-workflow',
        message: 'run started',
        runId: 'run-1',
      });
      result.current.handlePrimaryAction();
    });

    await waitFor(() => {
      expect(result.current.actionResult?.runId).toBe('run-1');
    });

    await act(async () => {
      result.current.handleSuggestionAction(retryAction);
    });

    await waitFor(() => {
      expect(phaseServiceMocks.executeRunPostAction).toHaveBeenCalled();
      expect(phaseServiceMocks.executeRunPostAction.mock.calls[0]?.[0]).toEqual(expect.objectContaining({
        targetVersion: undefined,
        targetEnvironment: undefined,
      }));
    });
  });
});
