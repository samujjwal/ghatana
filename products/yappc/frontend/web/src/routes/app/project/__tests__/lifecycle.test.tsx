import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

const { mockExecuteTask } = vi.hoisted(() => ({
  mockExecuteTask: vi.fn(),
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
    currentPhase: 'IMPROVE',
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
        id: 'insight-1',
        title: 'Elevated deployment friction',
        description: 'Repeated deploy retries suggest release readiness drift.',
        type: 'insight',
        fowStage: 9,
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
      fowStage: 9,
      persona: 'operator',
      priority: 'high',
      status: 'pending',
    },
  }),
  useExecuteTask: () => ({
    mutateAsync: mockExecuteTask,
    isPending: false,
  }),
}));

import LifecycleRoute from '../lifecycle';

describe('Lifecycle route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExecuteTask.mockReset();
    mockExecuteTask.mockResolvedValue({ taskId: 'task-1', status: 'completed' });
  });

  it('renders the lifecycle explorer and learn/evolve insight surface', () => {
    render(<LifecycleRoute />);

    expect(screen.getByTestId('lifecycle-explorer')).toBeDefined();
    expect(screen.getByTestId('lifecycle-insights-section')).toBeDefined();
    expect(screen.getByTestId('lifecycle-phase-summary-card')).toBeDefined();
    expect(screen.getByText('AI phase summary')).toBeDefined();
    expect(screen.getByText('AI phase summary: Enhance & Evolve readiness is under active risk.')).toBeDefined();
    expect(screen.getByText('1 critical anomaly signal should be resolved before promotion decisions.')).toBeDefined();
    expect(screen.getByText('AI recommendations')).toBeDefined();
    expect(screen.getByText('Workflow automation')).toBeDefined();
    expect(screen.getByText('Automate release checklist')).toBeDefined();
    expect(screen.getByText('Readiness anomalies')).toBeDefined();
    expect(screen.getByText('Observed evidence')).toBeDefined();
    expect(screen.getByText('Capture enhancement feedback')).toBeDefined();
    expect(screen.getByText('Deployment Error Rate')).toBeDefined();
    expect(screen.getByText('Deployment error rate is materially above the readiness baseline.')).toBeDefined();
    expect(screen.getByText('Elevated deployment friction')).toBeDefined();
  });

  it('starts the next workflow automation task from the lifecycle route', async () => {
    render(<LifecycleRoute />);

    fireEvent.click(screen.getByTestId('workflow-automation-trigger'));

    await waitFor(() => {
      expect(mockExecuteTask).toHaveBeenCalledWith({
        taskId: 'task-1',
        input: {
          projectId: 'proj-42',
          source: 'lifecycle-route',
          phase: 'IMPROVE',
        },
      });
    });

    expect(screen.getByTestId('workflow-automation-feedback').textContent).toContain('Automation started');
  });
});