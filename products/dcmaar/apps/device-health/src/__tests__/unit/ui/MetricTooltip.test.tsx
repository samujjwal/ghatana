/**
 * @fileoverview Unit tests for MetricTooltip component.
 */

import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { MetricTooltip } from '../../../ui/components/common/MetricTooltip';

describe('MetricTooltip', () => {
  it('renders children without tooltip by default', () => {
    render(
      <MetricTooltip metricKey="lcp">
        <span>LCP</span>
      </MetricTooltip>
    );

    expect(screen.getByText('LCP')).toBeInTheDocument();
    expect(screen.queryByText('Largest Contentful Paint')).not.toBeInTheDocument();
  });

  it('shows glossary content on hover', () => {
    render(
      <MetricTooltip metricKey="lcp">
        <span>LCP</span>
      </MetricTooltip>
    );

    const trigger = screen.getByText('LCP');
    fireEvent.mouseEnter(trigger);

    expect(screen.getByText('Largest Contentful Paint')).toBeInTheDocument();
    expect(screen.getByText(/Why it matters/)).toBeInTheDocument();
    expect(screen.getByText(/Good:/)).toBeInTheDocument();
    expect(screen.getByText(/Poor:/)).toBeInTheDocument();

    const learnMore = screen.getByText(/Learn more/).closest('a');
    expect(learnMore).toHaveAttribute('href', 'https://web.dev/articles/lcp');
    expect(learnMore).toHaveAttribute('target', '_blank');
  });

  it('fails silently when metric key missing', () => {
    render(
      <MetricTooltip metricKey="unknown">
        <span>Unknown</span>
      </MetricTooltip>
    );

    const trigger = screen.getByText('Unknown');
    fireEvent.mouseEnter(trigger);

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });
});
