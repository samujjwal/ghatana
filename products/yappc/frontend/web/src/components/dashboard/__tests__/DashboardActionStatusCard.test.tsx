import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom';

import { DashboardActionStatusCard } from '../DashboardActionStatusCard';
import type { ProjectDashboardAction } from '../../../lib/api';

describe('DashboardActionStatusCard', () => {
  const defaultProps = {
    titleKey: 'dashboard.blockedWork',
    tone: 'warning' as const,
    actions: [],
    loading: false,
    error: null,
    emptyTextKey: 'dashboard.noBlockedWork',
    onOpenProject: vi.fn(),
  };

  const mockAction: ProjectDashboardAction = {
    id: 'action-1',
    projectId: 'proj-1',
    projectName: 'Test Project',
    workspaceId: 'ws-1',
    lifecyclePhase: 'RUN',
    routePhase: 'run',
    kind: 'blocker',
    title: 'Test Action',
    summary: 'Test summary',
    severity: 'critical',
    source: 'project.aiNextActions',
    requiresReview: true,
    safeToRun: false,
    isDegraded: false,
    isFallback: false,
    updatedAt: '2026-05-07T00:00:00.000Z',
  };

  it('renders title and action count', () => {
    render(<DashboardActionStatusCard {...defaultProps} actions={[mockAction]} />);
    expect(screen.getByText(/blocked work/i)).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('renders loading state', () => {
    render(<DashboardActionStatusCard {...defaultProps} loading={true} />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('renders error state with correlation ID', () => {
    const error = { message: 'Test error', correlationId: 'corr-123' };
    const onRetry = vi.fn();
    render(
      <DashboardActionStatusCard
        {...defaultProps}
        error={error}
        onRetry={onRetry}
      />
    );

    expect(screen.getByText(/could not load/i)).toBeInTheDocument();
    expect(screen.getByText(/corr-123/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('renders empty state when no actions', () => {
    render(<DashboardActionStatusCard {...defaultProps} actions={[]} />);
    expect(screen.getByText(/no blocked work/i)).toBeInTheDocument();
  });

  it('renders action buttons for each action', () => {
    render(
      <DashboardActionStatusCard
        {...defaultProps}
        actions={[mockAction, { ...mockAction, id: 'action-2', title: 'Action 2' }]}
      />
    );

    expect(screen.getByText('Test Action')).toBeInTheDocument();
    expect(screen.getByText('Action 2')).toBeInTheDocument();
  });

  it('calls onOpenProject when action button is clicked', async () => {
    const user = userEvent.setup();
    const onOpenProject = vi.fn();
    render(
      <DashboardActionStatusCard
        {...defaultProps}
        actions={[mockAction]}
        onOpenProject={onOpenProject}
      />
    );

    await user.click(screen.getByText('Test Action'));
    expect(onOpenProject).toHaveBeenCalledWith(mockAction);
  });

  it('calls onRetry when retry button is clicked', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const error = { message: 'Test error' };
    render(
      <DashboardActionStatusCard
        {...defaultProps}
        error={error}
        onRetry={onRetry}
      />
    );

    await user.click(screen.getByRole('button', { name: /retry/i }));
    expect(onRetry).toHaveBeenCalled();
  });

  it('renders project name and summary in action button', () => {
    render(<DashboardActionStatusCard {...defaultProps} actions={[mockAction]} />);
    expect(screen.getByText('Test Project')).toBeInTheDocument();
    expect(screen.getByText('Test summary')).toBeInTheDocument();
  });
});
