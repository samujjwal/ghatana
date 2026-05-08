import React from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EvidenceAutomationCard } from '@/components/evidence-first';

describe('EvidenceAutomationCard', () => {
  it('renders all required evidence-first fields', () => {
    render(
      <EvidenceAutomationCard
        title="Rollout Decision"
        why="Policy and runtime gates indicate safe rollout"
        dataUsed={['capability snapshot', 'audit report', 'durable provider status']}
        confidence={0.88}
        policy="Require compliance score >= 70"
        audit="Report generated at 2026-05-05T00:00:00Z"
        overrideControl={<button type="button">Manual Override</button>}
      />,
    );

    expect(screen.getByText('Rollout Decision')).toBeInTheDocument();
    expect(screen.getByText('Why')).toBeInTheDocument();
    expect(screen.getByText('Data Used')).toBeInTheDocument();
    expect(screen.getByText('Policy')).toBeInTheDocument();
    expect(screen.getByText('Audit')).toBeInTheDocument();
    expect(screen.getByText('Override Control')).toBeInTheDocument();
    expect(screen.getByText('Manual Override')).toBeInTheDocument();
    expect(screen.getByText('Confidence: 88% (medium)')).toBeInTheDocument();
  });
});
