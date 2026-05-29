import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom';

import { DashboardDecisionBrief } from '../DashboardDecisionBrief';
import type { ProjectDashboardAction } from '../../../lib/api';

describe('DashboardDecisionBrief', () => {
  const defaultProps = {
    headline: 'Test Headline',
    description: 'Test description',
    action: null,
    ctaLabel: null,
    isDegraded: false,
    retryAvailable: false,
    blockedCount: 0,
    reviewCount: 0,
    safeCount: 1,
    onActionClick: vi.fn(),
  };

  it('renders headline and description', () => {
    render(<DashboardDecisionBrief {...defaultProps} />);
    expect(screen.getByText('Test Headline')).toBeInTheDocument();
    expect(screen.getByText('Test description')).toBeInTheDocument();
  });

  it('renders action button when action and ctaLabel are provided', () => {
    const action: ProjectDashboardAction = {
      id: 'action-1',
      projectId: 'proj-1',
      projectName: 'Test Project',
      workspaceId: 'ws-1',
      lifecyclePhase: 'GENERATE',
      routePhase: 'generate',
      kind: 'safe-to-continue',
      title: 'Test Action',
      summary: 'Test summary',
      severity: 'info',
      source: 'project.lifecyclePhase',
      requiresReview: false,
      safeToRun: true,
      isDegraded: false,
      isFallback: false,
      updatedAt: '2026-05-07T00:00:00.000Z',
    };

    render(
      <DashboardDecisionBrief
        {...defaultProps}
        action={action}
        ctaLabel="Open Next Step"
      />
    );

    expect(screen.getByRole('button', { name: 'Open Next Step' })).toBeInTheDocument();
  });

  it('renders retry button when degraded and retry available', () => {
    const onRetry = vi.fn();
    render(
      <DashboardDecisionBrief
        {...defaultProps}
        isDegraded={true}
        retryAvailable={true}
        correlationId="corr-123"
        onRetry={onRetry}
      />
    );

    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
    expect(screen.getByText(/corr-123/i)).toBeInTheDocument();
  });

  it('does not render retry button when retry not available', () => {
    render(
      <DashboardDecisionBrief
        {...defaultProps}
        isDegraded={true}
        retryAvailable={false}
        correlationId="corr-123"
      />
    );

    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
  });

  it('calls onActionClick when action button is clicked', async () => {
    const user = userEvent.setup();
    const onActionClick = vi.fn();
    const action: ProjectDashboardAction = {
      id: 'action-1',
      projectId: 'proj-1',
      projectName: 'Test Project',
      workspaceId: 'ws-1',
      lifecyclePhase: 'GENERATE',
      routePhase: 'generate',
      kind: 'safe-to-continue',
      title: 'Test Action',
      summary: 'Test summary',
      severity: 'info',
      source: 'project.lifecyclePhase',
      requiresReview: false,
      safeToRun: true,
      isDegraded: false,
      isFallback: false,
      updatedAt: '2026-05-07T00:00:00.000Z',
    };

    render(
      <DashboardDecisionBrief
        {...defaultProps}
        action={action}
        ctaLabel="Open Next Step"
        onActionClick={onActionClick}
      />
    );

    await user.click(screen.getByRole('button', { name: 'Open Next Step' }));
    expect(onActionClick).toHaveBeenCalledWith(action);
  });

  it('calls onRetry when retry button is clicked', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <DashboardDecisionBrief
        {...defaultProps}
        isDegraded={true}
        retryAvailable={true}
        onRetry={onRetry}
      />
    );

    await user.click(screen.getByRole('button', { name: /retry/i }));
    expect(onRetry).toHaveBeenCalled();
  });

  it('displays pluralized counts correctly', () => {
    render(
      <DashboardDecisionBrief
        {...defaultProps}
        blockedCount={2}
        reviewCount={1}
        safeCount={3}
      />
    );

    expect(screen.getByText(/2 blocked items/i)).toBeInTheDocument();
    expect(screen.getByText(/1 review item/i)).toBeInTheDocument();
    expect(screen.getByText(/3 safe continuations/i)).toBeInTheDocument();
  });
});
