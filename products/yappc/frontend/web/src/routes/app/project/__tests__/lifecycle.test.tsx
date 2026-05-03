import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LifecyclePhase } from '../../../../types/lifecycle';

const { mockExecuteTask } = vi.hoisted(() => ({
  mockExecuteTask: vi.fn(),
}));

const { mockApplyAutomationPlan } = vi.hoisted(() => ({
  mockApplyAutomationPlan: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'proj-42' }),
  };
});

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

vi.mock('../../../../hooks/useLifecycleData', () => ({
  useAIRecommendations: () => ({
    data: [
      {
        id: 'rec-1',
        title: 'Capture enhancement feedback',
        description: 'Summarize the latest operator learnings into an evolution backlog.',
        confidence: 0.91,
        priority: 'high',
        persona: 'product',
        type: 'enhancement',
      },
    ],
  }),
  useAIInsights: () => ({
    data: [
      {
        phase: LifecyclePhase.LEARN,
        title: 'Elevated deployment friction',
        description: 'Repeated deploy retries suggest release readiness drift.',
        type: 'insight',
        flowStage: 9,
        timestamp: '2026-04-17T12:00:00.000Z',
      },
    ],
  }),
  useReadinessAnomalies: () => ({
    data: [
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
    ],
  }),
  useNextBestTask: () => ({
    data: {
      id: 'task-1',
      title: 'Automate release checklist',
      description: 'Run the staged release checklist and attach the resulting evidence.',
      phase: 'IMPROVE',
      flowStage: 9,
      persona: 'operator',
      priority: 'high',
      status: 'pending',
    },
  }),
  useExecuteTask: () => ({
    mutateAsync: mockExecuteTask,
    isPending: false,
  }),
  useLifecycleAutomationPlan: () => ({
    data: {
      projectId: 'proj-42',
      currentPhase: LifecyclePhase.LEARN,
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
    },
  }),
  useApplyLifecycleAutomationPlan: () => ({
    mutateAsync: mockApplyAutomationPlan,
    isPending: false,
  }),
}));

import LifecycleRoute from '../lifecycle';

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
    </QueryClientProvider>,
  );
}

describe('Lifecycle route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExecuteTask.mockReset();
    mockApplyAutomationPlan.mockReset();
    mockExecuteTask.mockResolvedValue({ taskId: 'task-1', status: 'completed' });
    mockApplyAutomationPlan.mockResolvedValue({
      execution: null,
      canAutoAdvance: true,
    });
  });

  it('renders the lifecycle explorer and learn/evolve insight surface', () => {
    renderRoute();

    expect(screen.getByTestId('lifecycle-explorer')).toBeDefined();
    expect(screen.getByTestId('lifecycle-insights-section')).toBeDefined();
    expect(screen.getByTestId('lifecycle-summary-status-card')).toBeDefined();
    expect(screen.getByText('Phase summary')).toBeDefined();
    expect(screen.getByText(/readiness is under active risk\./)).toBeDefined();
    expect(screen.getByText('1 critical anomaly signal should be resolved before promotion decisions.')).toBeDefined();
    expect(screen.getByText('Recommended next steps')).toBeDefined();
    expect(screen.getByText('Suggested task')).toBeDefined();
    expect(screen.getByText('Automate release checklist')).toBeDefined();
    expect(screen.getByText('Readiness anomalies')).toBeDefined();
    expect(screen.getByText('Observed evidence')).toBeDefined();
    expect(screen.getByText('Decision support')).toBeDefined();
    expect(screen.getByText('Evidence-based defaults with explicit review thresholds and progressive disclosure.')).toBeDefined();
    expect(screen.getByTestId('decision-review-threshold').textContent).toContain('Review required');
    expect(screen.getByText('Capture enhancement feedback')).toBeDefined();
    expect(screen.getByText('Deployment Error Rate')).toBeDefined();
    expect(screen.getByText('Deployment error rate is materially above the readiness baseline.')).toBeDefined();
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

    expect(screen.getByTestId('workflow-automation-feedback').textContent).toContain('Suggested task started');
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