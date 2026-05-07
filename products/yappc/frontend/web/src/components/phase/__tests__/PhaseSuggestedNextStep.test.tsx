import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { render } from '@/test-utils/test-utils';
import { PhaseSuggestedNextStep, type SuggestedStep } from '../PhaseSuggestedNextStep';

function makeStep(overrides: Partial<SuggestedStep> = {}): SuggestedStep {
  return {
    id: 'step-1',
    title: 'Move to Observe',
    description: 'Lifecycle preview indicates the gate can advance with the current evidence.',
    type: 'automation',
    confidence: 0.8,
    evidence: ['Source: gate', 'Lifecycle readiness confidence: 80%'],
    riskLevel: 'medium',
    applyMode: 'one-click',
    approvalRequired: false,
    rollbackSupported: true,
    estimatedTime: '3 min',
    onAccept: vi.fn(),
    ...overrides,
  };
}

describe('PhaseSuggestedNextStep', () => {
  it('groups automation, review, and manual suggestions into explicit lanes', () => {
    render(
      <PhaseSuggestedNextStep
        steps={[
          makeStep(),
          makeStep({
            id: 'step-2',
            title: 'Request owner review',
            type: 'review',
            riskLevel: 'high',
            confidence: 0.92,
            applyMode: 'review-required',
            approvalRequired: true,
            rollbackSupported: false,
          }),
          makeStep({
            id: 'step-3',
            title: 'Review details below',
            type: 'manual',
            riskLevel: 'low',
            confidence: 0.7,
            applyMode: 'manual',
            approvalRequired: false,
            rollbackSupported: false,
          }),
        ]}
      />,
    );

    expect(screen.getByTestId('suggestion-section-automation')).toHaveTextContent('Automation suggestions');
    expect(screen.getByTestId('suggestion-section-review')).toHaveTextContent('Review gates');
    expect(screen.getByTestId('suggestion-section-manual')).toHaveTextContent('Manual suggestions');
  });

  it('renders confidence, evidence, apply mode, approval, rollback, and risk metadata', () => {
    const onAccept = vi.fn();
    render(<PhaseSuggestedNextStep steps={[makeStep({ onAccept })]} />);

    expect(screen.getByText('medium risk')).toBeInTheDocument();
    expect(screen.getByText('80% confidence')).toBeInTheDocument();
    expect(screen.getByText('one-click')).toBeInTheDocument();
    expect(screen.getByText('Not required')).toBeInTheDocument();
    expect(screen.getByText('Supported')).toBeInTheDocument();
    expect(screen.getByText('Source: gate')).toBeInTheDocument();
    expect(screen.getByText('Lifecycle readiness confidence: 80%')).toBeInTheDocument();
    expect(screen.getByTestId('suggestion-accessibility-explanation')).toHaveTextContent(
      /confidence explains how strongly/i,
    );
    expect(screen.getByTestId('suggestion-accessibility-explanation')).toHaveTextContent(
      /review gates mean a human approval step is required/i,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Run guided action' }));
    expect(onAccept).toHaveBeenCalledTimes(1);
  });

  it('uses review-specific action language when approval is required', () => {
    const onAccept = vi.fn();
    render(
      <PhaseSuggestedNextStep
        steps={[
          makeStep({
            type: 'review',
            applyMode: 'review-required',
            approvalRequired: true,
            rollbackSupported: false,
            onAccept,
          }),
        ]}
      />,
    );

    expect(screen.getByText('Required')).toBeInTheDocument();
    expect(screen.getByText('Not available')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Open review' }));
    expect(onAccept).toHaveBeenCalledTimes(1);
  });
});
