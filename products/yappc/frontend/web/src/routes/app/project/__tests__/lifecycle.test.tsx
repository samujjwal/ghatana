import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LifecyclePhase } from '../../../../types/lifecycle';

const { mockExecuteTask } = vi.hoisted(() => ({
  mockExecuteTask: vi.fn(),
}));

const { mockApplyAutomationPlan } = vi.hoisted(() => ({
  mockApplyAutomationPlan: vi.fn(),
}));

const {
  mockSetAgentRuns,
  mockSubmitApproved,
  mockHandleApprovalTransition,
  mockHandleOneClickApproval,
  mockHandleAutomationClick,
  mockClearFeedback,
} = vi.hoisted(() => ({
  mockSetAgentRuns: vi.fn(),
  mockSubmitApproved: vi.fn(),
  mockHandleApprovalTransition: vi.fn(),
  mockHandleOneClickApproval: vi.fn(),
  mockHandleAutomationClick: vi.fn(),
  mockClearFeedback: vi.fn(),
}));

vi.mock('react-router', () => ({
  useParams: () => ({ projectId: 'proj-42' }),
}));

vi.mock('../../../../components/lifecycle', () => ({
  LifecycleExplorer: ({ projectId }: { projectId: string }) => (
    <div data-testid="lifecycle-explorer">Lifecycle Explorer {projectId}</div>
  ),
}));

vi.mock('../../../../components/route/ErrorBoundary', () => ({
  RouteErrorBoundary: () => <div>Error boundary</div>,
}));

vi.mock('../../../../services/canvas/lifecycle', () => ({
  usePhaseGates: () => ({
    currentPhase: LifecyclePhase.LEARN,
  }),
}));

  vi.mock('../../../../hooks/useAgentRunStream', () => ({
    useAgentRunStream: () => ({
      runs: [],
      setRuns: mockSetAgentRuns,
      isConnected: false,
    }),
  }));


  vi.mock('../../../../hooks/useAuth', () => ({
    useAuth: () => ({
      isAuthenticated: true,
      currentUser: { id: 'user-1', email: 'user@example.com' },
      currentSession: null,
      getToken: () => null,
      getAuthHeader: () => null,
      hasPermission: () => false,
      hasRole: () => false,
      logout: vi.fn().mockResolvedValue(undefined),
    }),
  }));

  vi.mock('../../../../hooks/useRequirementOrchestration', () => ({
    useRequirementOrchestration: () => ({
      submitApproved: mockSubmitApproved,
      runRef: undefined,
      isSubmitting: false,
      error: null,
    }),
  }));

  vi.mock('../../../../services/LifecycleWebSocketService', () => ({
    LifecycleWebSocketService: class MockLifecycleWebSocketService {
      onUpdate() {
        return () => {};
      }
      onConnectionChange() {
        return () => {};
      }
      connect() {}
      disconnect() {}
    },
  }));

// Factory-style mock: arrays defined once in the closure so every hook call
// returns the SAME array reference. This prevents the infinite render loop caused
// by recommendations.slice(0,3) → requirementRecords useMemo → seededApprovals
// useEffect → setApprovalRecords → re-render when data references change each render.
vi.mock('../../../../hooks/useLifecycleData', () => {
  const stableRecommendations = [
    {
      id: 'rec-1',
      title: 'Capture enhancement feedback',
      description:
        'Summarize the latest operator learnings into an evolution backlog.',
      confidence: 0.91,
      priority: 'high',
      persona: 'product',
      type: 'enhancement',
    },
  ];
  const stableInsights = [
    {
      id: 'insight-1',
      phase: 'LEARN',
      title: 'Elevated deployment friction',
      description: 'Repeated deploy retries suggest release readiness drift.',
      type: 'insight',
      flowStage: 9,
      timestamp: '2026-04-17T12:00:00.000Z',
    },
  ];
  const stableAnomalies = [
    {
      id: 'alert-1',
      metric: 'deployment_error_rate',
      severity: 'CRITICAL',
      status: 'OPEN',
      expectedValue: 0.1,
      actualValue: 3.5,
      deviation: 3400,
      message: 'Deployment error rate is materially above the readiness baseline.',
      detectedAt: '2026-04-17T12:05:00.000Z',
    },
  ];
  const stableNextTask = {
    id: 'task-1',
    title: 'Automate release checklist',
    description: 'Run the staged release checklist and attach the resulting evidence.',
    phase: 'IMPROVE',
    flowStage: 9,
    persona: 'operator',
    priority: 'high',
    status: 'pending',
  };
  const stableAutomationPlan = {
    projectId: 'proj-42',
    currentPhase: 'LEARN',
    nextPhase: null,
    canAutoAdvance: true,
    readiness: 88,
    blockers: [],
    estimatedReadyIn: '~2 days',
    estimatedReadyInHours: 42,
    predictionConfidence: 0.67,
    decisionSupport: {
      defaults: {
        approvalMode: 'manual_review',
        riskTolerance: 'low',
        validationDepth: 'deep',
        targetEnvironment: 'production',
        ownerRole: 'SRE',
      },
      suggestions: [
        {
          id: 'proceed',
          title: 'Proceed with one-click promotion',
          reasoning: 'No blocking risks detected in current lifecycle context.',
          impact: 'medium',
        },
      ],
      progressiveDisclosure: {
        primaryActions: ['proceed'],
        secondaryActions: [],
      },
    },
    execution: null,
    generatedAt: '2026-04-17T12:10:00.000Z',
  };

  return {
    useAIRecommendations: () => ({ data: stableRecommendations }),
    useAIInsights: () => ({ data: stableInsights }),
    useReadinessAnomalies: () => ({ data: stableAnomalies }),
    useNextBestTask: () => ({ data: stableNextTask }),
    useExecuteTask: () => ({ mutateAsync: mockExecuteTask, isPending: false }),
    useLifecycleAutomationPlan: () => ({ data: stableAutomationPlan }),
    useApplyLifecycleAutomationPlan: () => ({
      mutateAsync: mockApplyAutomationPlan,
      isPending: false,
    }),
  };
});

import LifecycleRoute from '../lifecycle';

// Mock the specific file path used by lifecycle.tsx to import usePhaseGates.
// The barrel mock above covers re-exports but not direct file imports.
vi.mock('../../../../services/canvas/lifecycle/PhaseGateService', () => ({
  usePhaseGates: () => ({
    currentPhase: LifecyclePhase.LEARN,
    gateStatuses: {},
    canTransition: false,
    getLifecycleProgress: () => [],
  }),
}));

function renderRoute() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <LifecycleRoute />
    </QueryClientProvider>
  );
}

describe('Lifecycle route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExecuteTask.mockReset();
    mockApplyAutomationPlan.mockReset();
      // Prevent TCP timeout from any unguarded fetch calls (e.g. PhaseGateService
      // making real HTTP requests on Windows). Without this, each call hangs 30-60s.
      vi.spyOn(global, 'fetch').mockRejectedValue(
        new Error('Network unavailable in test environment')
      );
    mockExecuteTask.mockResolvedValue({
      taskId: 'task-1',
      status: 'completed',
    });
    mockApplyAutomationPlan.mockResolvedValue({
      execution: null,
      canAutoAdvance: true,
    });
  });

    afterEach(() => {
      vi.restoreAllMocks();
    });

  it('renders the lifecycle explorer and learn/evolve insight surface', () => {
    renderRoute();

    expect(screen.getByTestId('legacy-route-compatibility-notice')).toHaveTextContent('Lifecycle explorer is a compatibility deep link.');
    expect(screen.getByRole('link', { name: /learn phase cockpit/i })).toHaveAttribute('href', '/p/proj-42/learn');
    expect(screen.getByTestId('lifecycle-explorer')).toBeDefined();
    expect(screen.getByTestId('lifecycle-insights-section')).toBeDefined();
    expect(screen.getByTestId('lifecycle-summary-status-card')).toBeDefined();
    expect(screen.getByText('Stage readiness')).toBeDefined();
    expect(screen.getByText(/readiness is under active risk\./)).toBeDefined();
    expect(
      screen.getByText(
        '1 critical anomaly signal should be resolved before promotion decisions.'
      )
    ).toBeDefined();
    expect(screen.getByText('Recommended next steps')).toBeDefined();
    expect(screen.getByText('Suggested task')).toBeDefined();
    expect(screen.getByText('Automate release checklist')).toBeDefined();
    expect(screen.getByText('Readiness anomalies')).toBeDefined();
    expect(screen.getByText('Observed evidence')).toBeDefined();
    expect(screen.getByText('Decision support')).toBeDefined();
    expect(
      screen.getByText(
        'Evidence-based defaults with explicit review thresholds and progressive disclosure.'
      )
    ).toBeDefined();
    expect(
      screen.getByTestId('decision-review-threshold').textContent
    ).toContain('Review required');
    expect(screen.getByText('Capture enhancement feedback')).toBeDefined();
    expect(screen.getByText('Deployment Error Rate')).toBeDefined();
    expect(
      screen.getByText(
        'Deployment error rate is materially above the readiness baseline.'
      )
    ).toBeDefined();
    expect(screen.getByText('Elevated deployment friction')).toBeDefined();
  });

  it('starts the next workflow automation task from the lifecycle route', async () => {
    renderRoute();

    fireEvent.click(screen.getByTestId('workflow-automation-trigger'));

    await waitFor(() => {
      expect(mockExecuteTask).toHaveBeenCalledWith({
        taskId: 'task-1',
        input: {
          projectId: 'proj-42',
          source: 'lifecycle-route',
          phase: LifecyclePhase.LEARN,
        },
      });
    });

    expect(
      screen.getByTestId('workflow-automation-feedback').textContent
    ).toContain('Suggested task started');
  });

  it('applies one-click automation plan from decision support panel', async () => {
    renderRoute();

    fireEvent.click(screen.getByTestId('ai-one-click-approval-trigger'));

    await waitFor(() => {
      expect(mockApplyAutomationPlan).toHaveBeenCalledWith({
        projectId: 'proj-42',
        request: {
          phase: LifecyclePhase.LEARN,
          oneClickApprove: true,
          reason: 'Applied from lifecycle route decision support panel',
        },
      });
    });
  });
});
