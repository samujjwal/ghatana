import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PhaseStatusPanels } from '../PhaseStatusPanels';

describe('PhaseStatusPanels', () => {
  it('renders validation gates with required approvals when validate phase data exists', () => {
    render(
      <PhaseStatusPanels
        phase="validate"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'VALIDATE',
          nextPhase: 'GENERATE',
          canAdvance: true,
          readiness: 90,
          blockers: [],
          requiredArtifacts: ['Security review'],
          completedArtifacts: ['Requirements packet'],
          estimatedReadyIn: 'Ready now',
          estimatedReadyInHours: 0,
          predictionConfidence: 0.8,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('validation-status')).toHaveTextContent(/passed/i);
    expect(screen.getByTestId('approval-gates')).toBeInTheDocument();
    expect(screen.getByTestId('required-approval')).toHaveTextContent('Security review');
  });

  it('renders observe summary panels when no activity or incidents are available', () => {
    render(
      <PhaseStatusPanels
        phase="observe"
        preview={null}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('metrics-panel')).toHaveTextContent('No recent backed events have been recorded yet.');
    expect(screen.getByTestId('incidents-panel')).toHaveTextContent('No active incident-level blockers are surfaced here.');
  });
});
