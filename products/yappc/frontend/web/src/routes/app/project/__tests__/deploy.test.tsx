import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { LifecyclePhase } from '../../../../types/lifecycle';

const { mockTransition, mockGetNextPhase } = vi.hoisted(() => ({
  mockTransition: vi.fn(),
  mockGetNextPhase: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'project-1' }),
  };
});

vi.mock('jotai', () => ({
  useAtomValue: () => ({ id: 'user-42' }),
}));

vi.mock('../../../../stores/user.store', () => ({
  currentUserAtom: {},
}));

vi.mock('../../../../components/route/ErrorBoundary', () => ({
  RouteErrorBoundary: () => <div>Error boundary</div>,
}));

vi.mock('../../../../components/deploy/DeployPanelHost', () => ({
  DeployPanelHost: () => <div data-testid="deploy-panel-host">Deploy panel</div>,
}));

vi.mock('../../../../services/canvas/lifecycle', () => ({
  useLifecycleArtifacts: () => ({
    createArtifact: vi.fn(),
    updateArtifact: vi.fn(),
    artifacts: [],
  }),
  usePhaseGates: () => ({
    currentPhase: LifecyclePhase.GENERATE,
    transition: mockTransition,
  }),
}));

vi.mock('@/services/lifecycle/phase-transition-api', () => ({
  phaseTransitionAPI: {
    getNextPhase: mockGetNextPhase,
  },
}));

import DeployRoute from '../deploy';

describe('deploy route', () => {
  beforeEach(() => {
    mockTransition.mockReset();
    mockGetNextPhase.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows blockers and disables advancing when the next phase is not ready', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.GENERATE,
      nextPhase: LifecyclePhase.RUN,
      canAdvance: false,
      readiness: 50,
      blockers: [
        'Missing approved artifact: Documentation',
        'At least 2 approved artifacts are required before advancing from GENERATE to RUN.',
      ],
      requiredArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      completedArtifacts: ['Source Code'],
      estimatedReadyIn: '~2 days',
      estimatedReadyInHours: 50,
      predictionConfidence: 0.5,
      checkedAt: '2026-04-06T12:00:00.000Z',
    });

    render(<DeployRoute />);

    expect((await screen.findByTestId('phase-preview-summary')).textContent).toContain(
      'GENERATE -> RUN'
    );
    expect(screen.getByTestId('phase-blockers')).toBeTruthy();
    expect(screen.getByText('Missing approved artifact: Documentation')).toBeTruthy();
    expect((screen.getByTestId('phase-prediction-summary')).textContent).toContain('Ready in ~2 days');
    expect((screen.getByTestId('phase-prediction-summary')).textContent).toContain('50% confidence');
    expect(
      (screen.getByRole('button', { name: 'Advance to RUN' }) as HTMLButtonElement).disabled
    ).toBe(true);
  });

  it('advances using the API-provided next phase instead of a hardcoded map', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.GENERATE,
      nextPhase: LifecyclePhase.RUN,
      canAdvance: true,
      readiness: 100,
      blockers: [],
      requiredArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      completedArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.95,
      checkedAt: '2026-04-06T12:00:00.000Z',
    });
    mockTransition.mockResolvedValue({
      success: true,
      newPhase: LifecyclePhase.RUN,
      errors: [],
      warnings: [],
    });

    render(<DeployRoute />);

    fireEvent.click(await screen.findByRole('button', { name: 'Advance to RUN' }));

    await waitFor(() => {
      expect(mockTransition).toHaveBeenCalledWith(LifecyclePhase.RUN, 'user-42', {
        bypass: false,
        bypassReason: undefined,
      });
    });
  });

  it('shows a clear error when the transition call fails', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.GENERATE,
      nextPhase: LifecyclePhase.RUN,
      canAdvance: true,
      readiness: 100,
      blockers: [],
      requiredArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      completedArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.95,
      checkedAt: '2026-04-06T12:00:00.000Z',
    });
    mockTransition.mockResolvedValue({
      success: false,
      errors: ['Build verification failed.'],
      warnings: [],
    });

    render(<DeployRoute />);

    fireEvent.click(await screen.findByRole('button', { name: 'Advance to RUN' }));

    expect((await screen.findByTestId('phase-preview-error')).textContent).toContain(
      'Build verification failed.'
    );
  });
});
