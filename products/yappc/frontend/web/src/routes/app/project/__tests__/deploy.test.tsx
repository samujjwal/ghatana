import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { LifecyclePhase } from '../../../../types/lifecycle';

const { mockTransition, mockGetNextPhase, mockCreateArtifact, mockUpdateArtifact } = vi.hoisted(() => ({
  mockTransition: vi.fn(),
  mockGetNextPhase: vi.fn(),
  mockCreateArtifact: vi.fn(),
  mockUpdateArtifact: vi.fn(),
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

vi.mock('../../../../services/canvas/lifecycle/LifecycleArtifactService', () => ({
  useLifecycleArtifacts: () => ({
    createArtifact: mockCreateArtifact,
    updateArtifact: mockUpdateArtifact,
    artifacts: [],
  }),
}));

vi.mock('../../../../services/canvas/lifecycle/PhaseGateService', () => ({
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
    mockCreateArtifact.mockReset();
    mockUpdateArtifact.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows blockers and disables advancing when the next phase is not ready', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.EXECUTE,
      nextPhase: LifecyclePhase.VERIFY,
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

    expect(screen.getByTestId('legacy-route-compatibility-notice')).toHaveTextContent('Project deploy is a compatibility deep link.');
    expect(screen.getByRole('link', { name: /run phase cockpit/i })).toHaveAttribute('href', '/p/project-1/run');
    expect((await screen.findByTestId('phase-preview-summary')).textContent).toContain(
      'EXECUTE -> VERIFY'
    );
    expect(screen.getByTestId('phase-blockers')).toBeTruthy();
    expect(screen.getAllByText('Missing approved artifact: Documentation').length).toBeGreaterThan(0);
    expect((screen.getByTestId('phase-prediction-summary')).textContent).toContain('Ready in ~2 days');
    expect((screen.getByTestId('phase-prediction-summary')).textContent).toContain('50% confidence');
    expect(screen.getByTestId('release-planning-status-badge').textContent).toContain('Blocked by lifecycle gates');
    expect(screen.getByTestId('release-planning-status-detail').textContent).toContain('Missing approved artifact: Documentation');
    expect(
      (screen.getByRole('button', { name: 'Advance to VERIFY' }) as HTMLButtonElement).disabled
    ).toBe(true);
  });

  it('advances using the API-provided next phase instead of a hardcoded map', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.EXECUTE,
      nextPhase: LifecyclePhase.VERIFY,
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
      newPhase: LifecyclePhase.VERIFY,
      errors: [],
      warnings: [],
    });

    render(<DeployRoute />);

    fireEvent.click(await screen.findByRole('button', { name: 'Advance to VERIFY' }));
    fireEvent.click(await screen.findByTestId('confirm-advance-button'));

    await waitFor(() => {
      expect(mockTransition).toHaveBeenCalledWith(LifecyclePhase.VERIFY, 'user-42', {
        bypass: false,
        bypassReason: undefined,
      });
    });
  });

  it('shows a clear error when the transition call fails', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.EXECUTE,
      nextPhase: LifecyclePhase.VERIFY,
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

    fireEvent.click(await screen.findByRole('button', { name: 'Advance to VERIFY' }));
    fireEvent.click(await screen.findByTestId('confirm-advance-button'));

    expect((await screen.findByTestId('phase-preview-error')).textContent).toContain(
      'Build verification failed.'
    );
  });

  it('surfaces lifecycle readiness loading failures instead of implying a valid promotion path', async () => {
    mockGetNextPhase.mockRejectedValue(new Error('Lifecycle readiness service unavailable'));

    render(<DeployRoute />);

    expect((await screen.findByTestId('phase-preview-error')).textContent).toContain(
      'Lifecycle readiness service unavailable'
    );
    expect(screen.getByTestId('release-planning-status-badge').textContent).toContain('Release planning blocked');
    expect((screen.getByRole('button', { name: 'Advance Phase' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('renders the operator control surface and creates an incident report', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.EXECUTE,
      nextPhase: LifecyclePhase.VERIFY,
      canAdvance: true,
      readiness: 82,
      blockers: [],
      requiredArtifacts: ['Source Code'],
      completedArtifacts: ['Source Code'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.88,
      checkedAt: '2026-04-06T12:00:00.000Z',
    });
    mockCreateArtifact.mockResolvedValue(undefined);

    render(<DeployRoute />);

    expect(await screen.findByTestId('operator-controls-card')).toBeTruthy();
    fireEvent.change(screen.getByTestId('operator-note-input'), {
      target: { value: 'Watch error budget before promoting.' },
    });
    fireEvent.click(screen.getByTestId('operator-create-incident'));

    await waitFor(() => {
      expect(mockCreateArtifact).toHaveBeenCalledWith('incident_report', 'user-42');
    });

    expect(screen.getByTestId('operator-action-feedback').textContent).toContain('Incident report created');
  });

  it('submits operator notes through the lifecycle transition path', async () => {
    mockGetNextPhase.mockResolvedValue({
      projectId: 'project-1',
      currentPhase: LifecyclePhase.EXECUTE,
      nextPhase: LifecyclePhase.VERIFY,
      canAdvance: true,
      readiness: 84,
      blockers: [],
      requiredArtifacts: ['Source Code'],
      completedArtifacts: ['Source Code'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.87,
      checkedAt: '2026-04-06T12:00:00.000Z',
    });
    mockTransition.mockResolvedValue({
      success: true,
      newPhase: LifecyclePhase.VERIFY,
      errors: [],
      warnings: [],
    });

    render(<DeployRoute />);

    fireEvent.change(await screen.findByTestId('operator-note-input'), {
      target: { value: 'Watch the rollout and validate docs before promotion.' },
    });
    fireEvent.click(screen.getByTestId('operator-advance-with-note'));

    await waitFor(() => {
      expect(mockTransition).toHaveBeenCalledWith(LifecyclePhase.VERIFY, 'user-42', {
        bypass: false,
        bypassReason: 'Watch the rollout and validate docs before promotion.',
      });
    });

    expect(screen.getByTestId('operator-action-feedback').textContent).toContain(
      'Operator advance request submitted to the lifecycle gate.'
    );
  });
});
