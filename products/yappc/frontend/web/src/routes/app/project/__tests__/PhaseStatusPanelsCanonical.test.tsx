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

  it('renders typed learning insight and evolution plan payloads when provided', () => {
    const { rerender } = render(
      <PhaseStatusPanelsCanonical
        phase="learn"
        phasePanels={[
          {
            phase: 'learn',
            status: 'ready',
            summary: 'Learning insights are available.',
            recommendation: 'Review and approve insight.',
            owner: 'Learn Pipeline',
            confidence: 0.82,
            supportTrace: 'trace-learn-1',
            cards: [],
            learningInsight: {
              learnedSignal: 'Regression risk increased in payments flow',
              sourceEvent: 'observe.risk-detected',
              confidence: 0.82,
              recommendation: 'Require approval before promoting',
              approvalRequired: true,
              rollbackPath: 'Revert to previous approved baseline',
            },
          },
        ]}
      />,
    );

    expect(screen.getByTestId('learn-learning-insight')).toBeInTheDocument();
    expect(screen.getByTestId('learn-learning-checklist')).toBeInTheDocument();
    expect(screen.getByText('Regression risk increased in payments flow')).toBeInTheDocument();

    rerender(
      <PhaseStatusPanelsCanonical
        phase="evolve"
        phasePanels={[
          {
            phase: 'evolve',
            status: 'needs-review',
            summary: 'Evolution proposal requires review.',
            recommendation: 'Review plan details before approval.',
            owner: 'Evolve Pipeline',
            confidence: 0.74,
            supportTrace: 'trace-evolve-1',
            cards: [],
            evolutionPlan: {
              proposal: 'Refactor orchestration service boundaries',
              impactSummary: 'Low runtime impact, medium integration impact',
              diffSummary: '12 files changed',
              validationRequirements: 'Run integration + policy checks',
              approvalState: 'PENDING_REMEDIATION',
              rollbackPath: 'Revert to previous release candidate',
              rerunTarget: 'observe',
            },
          },
        ]}
      />,
    );

    expect(screen.getByTestId('evolve-evolution-plan')).toBeInTheDocument();
    expect(screen.getByTestId('evolve-evolution-stepper')).toBeInTheDocument();
    expect(screen.getByText('Refactor orchestration service boundaries')).toBeInTheDocument();
  });
});
