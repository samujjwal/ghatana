import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PhaseGateDialog } from '../PhaseGateDialog';
import type { AiReadinessAssessment } from '@/hooks/usePhaseGate';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

function makeAiAssessment(overrides: Partial<AiReadinessAssessment> = {}): AiReadinessAssessment {
  return {
    score: 72,
    missingItems: ['ADR not finalised'],
    nextSteps: ['Complete the ADR', 'Validate requirements'],
    rationale: 'Three artifacts remain incomplete.',
    source: 'MODEL',
    ...overrides,
  };
}

describe('PhaseGateDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the target phase name in the title', () => {
    render(
      <PhaseGateDialog
        targetPhase="shape"
        missingArtifacts={[]}
        aiAssessment={undefined}
        isAiAssessing={false}
        onRequestAiAssessment={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByText(/Phase gate: Shape/i)).toBeInTheDocument();
  });

  it('shows missing artifact chips when artifacts are required', () => {
    const missing: LifecycleArtifactKind[] = ['adr', 'threat_model'];
    render(
      <PhaseGateDialog
        targetPhase="validate"
        missingArtifacts={missing}
        aiAssessment={undefined}
        isAiAssessing={false}
        onRequestAiAssessment={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByTestId('phase-gate-missing-list')).toBeInTheDocument();
    expect(screen.getAllByText('Required').length).toBe(2);
  });

  it('shows "all required" message when no artifacts are missing', () => {
    render(
      <PhaseGateDialog
        targetPhase="generate"
        missingArtifacts={[]}
        aiAssessment={undefined}
        isAiAssessing={false}
        onRequestAiAssessment={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByText(/All required artifacts are complete/i)).toBeInTheDocument();
  });

  it('calls onRequestAiAssessment when the readiness button is clicked', () => {
    const onRequestAiAssessment = vi.fn();
    render(
      <PhaseGateDialog
        targetPhase="shape"
        missingArtifacts={['adr']}
        aiAssessment={undefined}
        isAiAssessing={false}
        onRequestAiAssessment={onRequestAiAssessment}
        onClose={vi.fn()}
      />
    );
    fireEvent.click(screen.getByTestId('phase-gate-ai-assess-btn'));
    expect(onRequestAiAssessment).toHaveBeenCalledOnce();
  });

  it('renders readiness button copy before assessment starts', () => {
    render(
      <PhaseGateDialog
        targetPhase="shape"
        missingArtifacts={['adr']}
        aiAssessment={undefined}
        isAiAssessing={false}
        onRequestAiAssessment={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByText('Get readiness assessment')).toBeInTheDocument();
  });

  it('shows loading spinner while AI is assessing', () => {
    render(
      <PhaseGateDialog
        targetPhase="shape"
        missingArtifacts={['adr']}
        aiAssessment={undefined}
        isAiAssessing={true}
        onRequestAiAssessment={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByText('Assessing readiness…')).toBeInTheDocument();
    expect(screen.queryByTestId('phase-gate-ai-assess-btn')).toBeNull();
  });

  it('shows AI assessment result when available', () => {
    render(
      <PhaseGateDialog
        targetPhase="validate"
        missingArtifacts={['adr']}
        aiAssessment={makeAiAssessment()}
        isAiAssessing={false}
        onRequestAiAssessment={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByTestId('phase-gate-ai-result')).toBeInTheDocument();
    expect(screen.getByText('72')).toBeInTheDocument();
    expect(screen.getByTestId('phase-gate-ai-next-steps')).toBeInTheDocument();
    expect(screen.getByText('Complete the ADR')).toBeInTheDocument();
  });

  it('calls onClose when close button is clicked', () => {
    const onClose = vi.fn();
    render(
      <PhaseGateDialog
        targetPhase="shape"
        missingArtifacts={[]}
        aiAssessment={undefined}
        isAiAssessing={false}
        onRequestAiAssessment={vi.fn()}
        onClose={onClose}
      />
    );
    fireEvent.click(screen.getByTestId('phase-gate-close'));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
