/**
 * Tests for ErrorStates components (ErrorBanner, ErrorPage, EmptyState).
 *
 * Validates error display, retry/dismiss actions, accessibility, and
 * error type-specific styling and messaging.
 *
 * @doc.type test
 * @doc.purpose Comprehensive error handling tests for ErrorBanner, ErrorPage, and EmptyState
 * @doc.layer frontend
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorBanner, ErrorPage, EmptyState } from '../../components/core/ErrorStates';

// Suppress console errors in tests
beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

// ─── ErrorBanner Tests ─────────────────────────────────────────────────────

describe('ErrorBanner', () => {
  it('renders nothing when error is null', () => {
    const { container } = render(
      <ErrorBanner error={null} onRetry={vi.fn()} onDismiss={vi.fn()} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders error message when error is provided', () => {
    const error = new Error('Test error message');
    render(<ErrorBanner error={error} />);
    expect(screen.getByText('Test error message')).toBeInTheDocument();
  });

  it('uses custom message when provided', () => {
    const error = new Error('Original error');
    render(<ErrorBanner error={error} message="Custom message" />);
    expect(screen.getByText('Custom message')).toBeInTheDocument();
    expect(screen.queryByText('Original error')).not.toBeInTheDocument();
  });

  it('calls onRetry when retry button is clicked', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const error = new Error('Test error');
    render(<ErrorBanner error={error} onRetry={onRetry} />);
    
    const retryButton = screen.getByLabelText('Retry');
    await user.click(retryButton);
    
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('calls onDismiss when dismiss button is clicked', async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();
    const error = new Error('Test error');
    render(<ErrorBanner error={error} onDismiss={onDismiss} />);
    
    const dismissButton = screen.getByLabelText('Dismiss');
    await user.click(dismissButton);
    
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('does not render retry button when onRetry is not provided', () => {
    const error = new Error('Test error');
    render(<ErrorBanner error={error} />);
    expect(screen.queryByLabelText('Retry')).not.toBeInTheDocument();
  });

  it('does not render dismiss button when onDismiss is not provided', () => {
    const error = new Error('Test error');
    render(<ErrorBanner error={error} />);
    expect(screen.queryByLabelText('Dismiss')).not.toBeInTheDocument();
  });

  it('applies error-specific styling for ValidationError', () => {
    const error = new Error('Validation failed');
    error.name = 'ValidationError';
    const { container } = render(<ErrorBanner error={error} />);
    
    const banner = container.firstChild as HTMLElement;
    expect(banner.className).toContain('bg-amber-50');
    expect(banner.className).toContain('text-amber-700');
  });

  it('applies error-specific styling for RateLimitError', () => {
    const error = new Error('Rate limit exceeded');
    error.name = 'RateLimitError';
    const { container } = render(<ErrorBanner error={error} />);
    
    const banner = container.firstChild as HTMLElement;
    expect(banner.className).toContain('bg-orange-50');
    expect(banner.className).toContain('text-orange-700');
  });

  it('applies error-specific styling for PermissionError', () => {
    const error = new Error('Access denied');
    error.name = 'PermissionError';
    const { container } = render(<ErrorBanner error={error} />);
    
    const banner = container.firstChild as HTMLElement;
    expect(banner.className).toContain('bg-red-50');
    expect(banner.className).toContain('text-red-700');
  });

  it('applies error-specific styling for NetworkError', () => {
    const error = new Error('Network failed');
    error.name = 'NetworkError';
    const { container } = render(<ErrorBanner error={error} />);
    
    const banner = container.firstChild as HTMLElement;
    expect(banner.className).toContain('bg-blue-50');
    expect(banner.className).toContain('text-blue-700');
  });

  it('applies custom className when provided', () => {
    const error = new Error('Test error');
    const { container } = render(<ErrorBanner error={error} className="custom-class" />);
    
    const banner = container.firstChild as HTMLElement;
    expect(banner.className).toContain('custom-class');
  });

  it('has accessible button labels', () => {
    const error = new Error('Test error');
    render(<ErrorBanner error={error} onRetry={vi.fn()} onDismiss={vi.fn()} />);
    
    expect(screen.getByLabelText('Retry')).toBeInTheDocument();
    expect(screen.getByLabelText('Dismiss')).toBeInTheDocument();
  });
});

// ─── ErrorPage Tests ─────────────────────────────────────────────────────

describe('ErrorPage', () => {
  it('renders nothing when error is null', () => {
    const { container } = render(
      <ErrorPage error={null} onRetry={vi.fn()} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders error page when error is provided', () => {
    const error = new Error('Test error');
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
  });

  it('uses custom title when provided', () => {
    const error = new Error('Test error');
    render(<ErrorPage error={error} title="Custom Error Title" />);
    
    expect(screen.getByText('Custom Error Title')).toBeInTheDocument();
    expect(screen.queryByText('Error')).not.toBeInTheDocument();
  });

  it('uses custom subtitle when provided', () => {
    const error = new Error('Test error');
    render(<ErrorPage error={error} subtitle="Custom subtitle message" />);
    
    expect(screen.getByText('Custom subtitle message')).toBeInTheDocument();
  });

  it('calls onRetry when Try Again button is clicked', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const error = new Error('Test error');
    render(<ErrorPage error={error} onRetry={onRetry} />);
    
    const retryButton = screen.getByText('Try Again');
    await user.click(retryButton);
    
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('does not render Try Again button when onRetry is not provided', () => {
    const error = new Error('Test error');
    render(<ErrorPage error={error} />);
    expect(screen.queryByText('Try Again')).not.toBeInTheDocument();
  });

  it('renders Go to Home button by default', () => {
    const error = new Error('Test error');
    render(<ErrorPage error={error} />);
    expect(screen.getByText('Go to Home')).toBeInTheDocument();
  });

  it('does not render Go to Home button when showHomeButton is false', () => {
    const error = new Error('Test error');
    render(<ErrorPage error={error} showHomeButton={false} />);
    expect(screen.queryByText('Go to Home')).not.toBeInTheDocument();
  });

  it('displays error-specific title for ValidationError', () => {
    const error = new Error('Validation failed');
    error.name = 'ValidationError';
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText('Validation Error')).toBeInTheDocument();
  });

  it('displays error-specific title for RateLimitError', () => {
    const error = new Error('Rate limit exceeded');
    error.name = 'RateLimitError';
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText('Rate Limit Exceeded')).toBeInTheDocument();
  });

  it('displays error-specific title for PermissionError', () => {
    const error = new Error('Access denied');
    error.name = 'PermissionError';
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText('Access Denied')).toBeInTheDocument();
  });

  it('displays error-specific title for NotFoundError', () => {
    const error = new Error('Not found');
    error.name = 'NotFoundError';
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText('Not Found')).toBeInTheDocument();
  });

  it('displays error-specific title for NetworkError', () => {
    const error = new Error('Network failed');
    error.name = 'NetworkError';
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText('Network Error')).toBeInTheDocument();
  });

  it('displays error-specific subtitle for RateLimitError', () => {
    const error = new Error('Rate limit exceeded');
    error.name = 'RateLimitError';
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText(/please try again later/i)).toBeInTheDocument();
  });

  it('displays error-specific subtitle for NetworkError', () => {
    const error = new Error('Network failed');
    error.name = 'NetworkError';
    render(<ErrorPage error={error} />);
    
    expect(screen.getByText(/check your internet connection/i)).toBeInTheDocument();
  });

  it('has accessible error icon with proper styling', () => {
    const error = new Error('Test error');
    const { container } = render(<ErrorPage error={error} />);

    const iconContainer = container.querySelector('svg')?.parentElement;
    expect(iconContainer).toHaveClass('bg-red-100');
  });
});

// ─── EmptyState Tests ─────────────────────────────────────────────────────

describe('EmptyState', () => {
  it('renders with title', () => {
    render(<EmptyState title="No data available" />);
    expect(screen.getByText('No data available')).toBeInTheDocument();
  });

  it('renders with description when provided', () => {
    render(
      <EmptyState 
        title="No data available" 
        description="There are no items to display at this time." 
      />
    );
    expect(screen.getByText('There are no items to display at this time.')).toBeInTheDocument();
  });

  it('does not render description when not provided', () => {
    render(<EmptyState title="No data available" />);
    expect(screen.queryByRole('paragraph')).not.toBeInTheDocument();
  });

  it('renders custom icon when provided', () => {
    const customIcon = <div data-testid="custom-icon">Custom Icon</div>;
    render(<EmptyState title="No data" icon={customIcon} />);
    expect(screen.getByTestId('custom-icon')).toBeInTheDocument();
  });

  it('renders default icon when not provided', () => {
    const { container } = render(<EmptyState title="No data" />);
    const icon = container.querySelector('svg');
    expect(icon).toBeInTheDocument();
  });

  it('renders action when provided', () => {
    const actionButton = <button data-testid="action-btn">Create New</button>;
    render(<EmptyState title="No data" action={actionButton} />);
    expect(screen.getByTestId('action-btn')).toBeInTheDocument();
  });

  it('does not render action when not provided', () => {
    render(<EmptyState title="No data" />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('applies custom className when provided', () => {
    const { container } = render(
      <EmptyState title="No data" className="custom-empty-state" />
    );
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain('custom-empty-state');
  });

  it('renders in centered layout by default', () => {
    const { container } = render(<EmptyState title="No data" />);
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain('flex');
    expect(wrapper.className).toContain('items-center');
    expect(wrapper.className).toContain('justify-center');
  });

  it('has accessible heading level', () => {
    render(<EmptyState title="No data available" />);
    const heading = screen.getByRole('heading', { level: 3 });
    expect(heading).toBeInTheDocument();
    expect(heading).toHaveTextContent('No data available');
  });
});
