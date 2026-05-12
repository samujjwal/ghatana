import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import {
  AccessDeniedState,
  AsyncStateBoundary,
  ErrorState,
  FeatureUnavailableState,
  LoadingState,
  SuccessState,
} from '../AsyncState';

describe('AsyncState primitives', () => {
  it('renders ready children for idle state', () => {
    render(
      <AsyncStateBoundary status="idle">
        <p>Ready content</p>
      </AsyncStateBoundary>
    );

    expect(screen.getByText('Ready content')).toBeInTheDocument();
  });

  it('renders loading state with polite live region', () => {
    render(<LoadingState title="Loading accounts" description="Fetching latest balances." />);

    expect(screen.getByRole('status')).toHaveAccessibleName('Loading');
    expect(screen.getByText('Loading accounts')).toBeInTheDocument();
    expect(screen.getByText('Fetching latest balances.')).toBeInTheDocument();
  });

  it('renders empty state through boundary', () => {
    render(
      <AsyncStateBoundary
        status="empty"
        empty={{
          title: 'No campaigns',
          description: 'Create a campaign to start collecting evidence.',
        }}
      >
        <p>Hidden content</p>
      </AsyncStateBoundary>
    );

    expect(screen.getByText('No campaigns')).toBeInTheDocument();
    expect(screen.queryByText('Hidden content')).not.toBeInTheDocument();
  });

  it('renders error state and retry action', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();

    render(
      <ErrorState
        title="Load failed"
        description="The report could not be loaded."
        error={new Error('Correlation id missing')}
        retryAction={{ label: 'Try again', onClick: onRetry }}
      />
    );

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Correlation id missing')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders access denied and feature unavailable states', () => {
    const { rerender } = render(
      <AccessDeniedState title="Permission denied" description="Request an elevated role." />
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Permission denied');

    rerender(
      <FeatureUnavailableState
        title="Connector unavailable"
        description="The connector is not configured for this workspace."
      />
    );

    expect(screen.getByRole('status', { name: 'Connector unavailable' })).toHaveTextContent('Connector unavailable');
  });

  it('renders success state when requested', () => {
    render(
      <AsyncStateBoundary
        status="success"
        success={{ title: 'Saved', description: 'The policy update is active.' }}
      >
        <p>Ready content</p>
      </AsyncStateBoundary>
    );

    expect(screen.getByRole('status', { name: 'Saved' })).toHaveTextContent('Saved');
    expect(screen.queryByText('Ready content')).not.toBeInTheDocument();
  });

  it('falls through to children for success without state copy', () => {
    render(
      <AsyncStateBoundary status="success">
        <p>Ready content</p>
      </AsyncStateBoundary>
    );

    expect(screen.getByText('Ready content')).toBeInTheDocument();
  });
});
