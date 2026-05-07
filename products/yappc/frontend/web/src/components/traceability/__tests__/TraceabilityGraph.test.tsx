/**
 * TraceabilityGraph tests (F-Y027 / R-ST-15)
 */

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import React from 'react';

import { TraceabilityGraph } from '../TraceabilityGraph';

describe('TraceabilityGraph (F-Y027)', () => {
  it('renders the traceability graph header', () => {
    render(<TraceabilityGraph projectId="proj-1" />);
    expect(screen.getByText(/End-to-End Traceability/i)).toBeInTheDocument();
  });

  it('renders all 8 lifecycle phases', () => {
    render(<TraceabilityGraph projectId="proj-1" />);
    expect(screen.getByText('Intent')).toBeInTheDocument();
    expect(screen.getByText('Shape')).toBeInTheDocument();
    expect(screen.getByText('Validate')).toBeInTheDocument();
    expect(screen.getByText('Generate')).toBeInTheDocument();
    expect(screen.getByText('Run')).toBeInTheDocument();
    expect(screen.getByText('Observe')).toBeInTheDocument();
    expect(screen.getByText('Learn')).toBeInTheDocument();
    expect(screen.getByText('Evolve')).toBeInTheDocument();
  });

  it('renders artifact entries for each phase', () => {
    render(<TraceabilityGraph projectId="proj-1" />);
    expect(screen.getByText('Idea Brief')).toBeInTheDocument();
    expect(screen.getByText('Threat Model')).toBeInTheDocument();
    expect(screen.getByText('Evidence Pack')).toBeInTheDocument();
  });

  it('accepts an optional className', () => {
    const { container } = render(
      <TraceabilityGraph projectId="proj-1" className="my-custom-class" />
    );
    expect(container.firstChild).toHaveClass('my-custom-class');
  });
});
