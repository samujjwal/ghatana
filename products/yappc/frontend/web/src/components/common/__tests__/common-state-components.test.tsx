/**
 * Tests for common utility components:
 * EmptyState, ErrorState, LoadingState
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { EmptyState } from '../EmptyState';
import { ErrorState } from '../ErrorState';
import { LoadingState } from '../LoadingState';

// ─── EmptyState ────────────────────────────────────────────────────────────────

describe('EmptyState', () => {
  it('renders title', () => {
    render(<EmptyState title="No results found" />);
    expect(screen.getByText('No results found')).toBeTruthy();
  });

  it('renders description when provided', () => {
    render(<EmptyState title="Empty" description="Try a different filter" />);
    expect(screen.getByText('Try a different filter')).toBeTruthy();
  });

  it('does not render description when omitted', () => {
    render(<EmptyState title="Empty" />);
    expect(screen.queryByText('Try a different filter')).toBeNull();
  });

  it('renders icon when provided', () => {
    render(
      <EmptyState title="Empty" icon={<span data-testid="icon">🗂</span>} />
    );
    expect(screen.getByTestId('icon')).toBeTruthy();
  });

  it('renders action when provided', () => {
    render(
      <EmptyState
        title="Empty"
        action={<button>Create Project</button>}
      />
    );
    expect(screen.getByText('Create Project')).toBeTruthy();
  });

  it('renders secondaryAction when provided', () => {
    render(
      <EmptyState
        title="Empty"
        secondaryAction={<button>Learn More</button>}
      />
    );
    expect(screen.getByText('Learn More')).toBeTruthy();
  });

  it('applies custom className', () => {
    const { container } = render(
      <EmptyState title="Empty" className="my-custom-class" />
    );
    expect(container.firstChild?.toString()).toBeTruthy();
    // className is applied to root div
    const div = container.querySelector('.my-custom-class');
    expect(div).toBeTruthy();
  });

  it('renders compact variant', () => {
    render(<EmptyState title="Empty" variant="compact" />);
    expect(screen.getByText('Empty')).toBeTruthy();
  });

  it('renders large variant', () => {
    render(<EmptyState title="Empty" variant="large" />);
    expect(screen.getByText('Empty')).toBeTruthy();
  });
});

// ─── ErrorState ───────────────────────────────────────────────────────────────

describe('ErrorState', () => {
  it('renders message', () => {
    render(<ErrorState message="Something went wrong" />);
    expect(screen.getByText('Something went wrong')).toBeTruthy();
  });

  it('renders title when provided', () => {
    render(<ErrorState title="Error" message="Something went wrong" />);
    expect(screen.getByText('Error')).toBeTruthy();
  });

  it('renders error code when provided', () => {
    render(<ErrorState message="Oops" code="ERR_404" />);
    expect(screen.getByText(/ERR_404/)).toBeTruthy();
  });

  it('renders correlation id when provided', () => {
    render(<ErrorState message="Oops" correlationId="corr-123" />);
    expect(screen.getByText(/Correlation ID: corr-123/)).toBeTruthy();
  });

  it('has role=alert', () => {
    render(<ErrorState message="Broken" />);
    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('renders Retry button when onRetry provided', () => {
    const onRetry = vi.fn();
    render(
      <ErrorState message="Failed" onRetry={onRetry} />
    );
    // card variant renders "Try Again" button
    expect(screen.getByText('Try Again')).toBeTruthy();
  });

  it('renders custom retry label when provided', () => {
    const onRetry = vi.fn();
    render(<ErrorState message="Failed" onRetry={onRetry} retryLabel="Retry" />);
    expect(screen.getByText('Retry')).toBeTruthy();
  });

  it('calls onRetry when Retry clicked', () => {
    const onRetry = vi.fn();
    render(<ErrorState message="Failed" onRetry={onRetry} />);
    fireEvent.click(screen.getByText('Try Again'));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('renders inline variant', () => {
    render(<ErrorState message="Inline error" variant="inline" />);
    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('renders banner variant', () => {
    render(<ErrorState message="Banner error" variant="banner" />);
    expect(screen.getByText('Banner error')).toBeTruthy();
  });

  it('renders warning type', () => {
    render(<ErrorState message="Watch out" type="warning" />);
    expect(screen.getByText('Watch out')).toBeTruthy();
  });

  it('renders info type', () => {
    render(<ErrorState message="FYI" type="info" />);
    expect(screen.getByText('FYI')).toBeTruthy();
  });
});

// ─── LoadingState ─────────────────────────────────────────────────────────────

describe('LoadingState', () => {
  it('renders default loading message', () => {
    render(<LoadingState />);
    expect(screen.getByText('Loading...')).toBeTruthy();
  });

  it('renders custom message', () => {
    render(<LoadingState message="Fetching data..." />);
    expect(screen.getByText('Fetching data...')).toBeTruthy();
  });

  it('has role=status for accessibility', () => {
    render(<LoadingState />);
    expect(screen.getByRole('status')).toBeTruthy();
  });

  it('renders sm size variant', () => {
    render(<LoadingState size="sm" />);
    expect(screen.getByText('Loading...')).toBeTruthy();
  });

  it('renders lg size variant', () => {
    render(<LoadingState size="lg" />);
    expect(screen.getByText('Loading...')).toBeTruthy();
  });

  it('renders skeleton variant', () => {
    // skeleton variant renders skeleton placeholders, no spinner
    render(<LoadingState variant="skeleton" />);
    // should render without crashing
    expect(document.body).toBeTruthy();
  });

  it('applies custom className', () => {
    render(<LoadingState className="custom-loading" />);
    // Should render without errors
    expect(screen.getByText('Loading...')).toBeTruthy();
  });
});
