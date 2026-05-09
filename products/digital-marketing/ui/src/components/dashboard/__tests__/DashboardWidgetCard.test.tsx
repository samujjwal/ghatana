import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DashboardWidgetCard } from '../DashboardWidgetCard';

describe('DashboardWidgetCard', () => {
  it('renders loading state', () => {
    render(<DashboardWidgetCard testId="widget-card" title="Widget" state="loading" />);

    expect(screen.getByTestId('widget-card')).toBeInTheDocument();
    expect(screen.getByText('Widget')).toBeInTheDocument();
  });

  it('renders error state with message', () => {
    render(
      <DashboardWidgetCard
        testId="widget-card"
        title="Widget"
        state="error"
        message="Failed to load"
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load');
  });

  it('renders unavailable state with message', () => {
    render(
      <DashboardWidgetCard
        testId="widget-card"
        title="Widget"
        state="unavailable"
        message="Unavailable for this tenant"
      />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Unavailable for this tenant');
  });

  it('renders ready state children and footer', () => {
    render(
      <DashboardWidgetCard
        testId="widget-card"
        title="Widget"
        footer={<span>Footer content</span>}
      >
        <div>Body content</div>
      </DashboardWidgetCard>,
    );

    expect(screen.getByText('Body content')).toBeInTheDocument();
    expect(screen.getByText('Footer content')).toBeInTheDocument();
  });
});
