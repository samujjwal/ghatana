import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PhaseEvidencePanel } from '../PhaseEvidencePanel';

describe('PhaseEvidencePanel', () => {
  it('renders an explicit empty state when no evidence is available', () => {
    render(<PhaseEvidencePanel evidence={[]} />);

    expect(screen.getByText('No evidence available')).toBeInTheDocument();
    expect(
      screen.getByText(/evidence will appear here after validation, generation, run, or learning signals/i),
    ).toBeInTheDocument();
  });

  it('renders evidence records when present', () => {
    render(
      <PhaseEvidencePanel
        evidence={[
          {
            id: 'evidence-1',
            type: 'metric',
            title: 'Assurance score',
            description: 'Generation assurance passed.',
            value: '98%',
            source: 'assurance',
          },
        ]}
      />,
    );

    expect(screen.getByText('Evidence (1)')).toBeInTheDocument();
    expect(screen.getByText('Assurance score')).toBeInTheDocument();
    expect(screen.getByText('98%')).toBeInTheDocument();
  });
});
