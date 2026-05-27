import React from 'react';
import { render, screen } from '@/test-utils/test-utils';
import { describe, expect, it } from 'vitest';

import { PhaseStatusPanelsCanonical } from '../PhaseStatusPanelsCanonical';

describe('PhaseStatusPanelsCanonical', () => {
  it('renders backend-owned panel summary and cards for the selected phase', () => {
    render(
      <PhaseStatusPanelsCanonical
        phase="intent"
        phasePanels={[
          {
            phase: 'intent',
            status: 'ready',
            summary: 'Intent is ready for lifecycle promotion.',
            recommendation: 'Proceed to Shape once owners approve.',
            owner: 'Intent Council',
            confidence: 0.84,
            supportTrace: 'trace-intent-1',
            cards: [
              {
                id: 'intent-coverage',
                title: 'Coverage',
                detail: 'Requirements and constraints are complete.',
                status: 'healthy',
                trace: 'trace-card-1',
                metadata: {},
              },
            ],
          },
        ]}
      />,
    );

    expect(screen.getByTestId('intent-panel-summary-text')).toHaveTextContent('Intent is ready for lifecycle promotion.');
    expect(screen.getByTestId('intent-panel-status')).toHaveTextContent('ready');
    expect(screen.getByTestId('intent-panel-owner')).toHaveTextContent('Intent Council');
    expect(screen.getByTestId('intent-panel-cards')).toBeInTheDocument();
    expect(screen.getByTestId('intent-panel-card')).toHaveTextContent('Coverage');
  });

  it('renders explicit fallback when the current phase has no backend panel', () => {
    render(
      <PhaseStatusPanelsCanonical
        phase="validate"
        phasePanels={[
          {
            phase: 'intent',
            status: 'ready',
            summary: 'Intent panel only',
            recommendation: 'N/A',
            owner: 'Intent Owner',
            confidence: 0.75,
            supportTrace: 'trace-intent-2',
            cards: [],
          },
        ]}
      />,
    );

    expect(screen.getByTestId('phase-panel-missing')).toBeInTheDocument();
    expect(screen.queryByTestId('validate-panel-summary')).not.toBeInTheDocument();
  });
});
