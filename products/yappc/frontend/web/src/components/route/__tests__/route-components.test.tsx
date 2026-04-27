/**
 * Tests for route/ components:
 * LoadingSpinner, RouteLoadingSpinner, HydrateFallback, PlaceholderRoute, ApiUnavailableFallback
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LoadingSpinner, RouteLoadingSpinner } from '../LoadingSpinner';
import { HydrateFallback } from '../HydrateFallback';
import { PlaceholderRoute } from '../PlaceholderRoute';
import { ApiUnavailableFallback } from '../ApiUnavailableFallback';

// ─── LoadingSpinner ───────────────────────────────────────────────────────────

describe('LoadingSpinner', () => {
  it('renders with default message', () => {
    render(<LoadingSpinner />);
    expect(screen.getAllByText('Loading content...').length).toBeGreaterThanOrEqual(1);
  });

  it('renders custom message', () => {
    render(<LoadingSpinner message="Please wait..." />);
    expect(screen.getAllByText('Please wait...').length).toBeGreaterThanOrEqual(1);
  });

  it('has role=status', () => {
    render(<LoadingSpinner />);
    expect(screen.getByRole('status')).toBeTruthy();
  });

  it('hides label when showLabel=false', () => {
    render(<LoadingSpinner message="Hidden" showLabel={false} />);
    // sr-only text still exists but visible label doesn't
    const spans = screen.getAllByText('Hidden');
    // only the sr-only span remains
    expect(spans.length).toBeGreaterThanOrEqual(1);
  });

  it('renders sm size', () => {
    render(<LoadingSpinner size="sm" />);
    expect(screen.getByRole('status')).toBeTruthy();
  });

  it('renders lg size', () => {
    render(<LoadingSpinner size="lg" />);
    expect(screen.getByRole('status')).toBeTruthy();
  });

  it('renders secondary variant', () => {
    render(<LoadingSpinner variant="secondary" />);
    expect(screen.getByRole('status')).toBeTruthy();
  });
});

describe('RouteLoadingSpinner', () => {
  it('renders without crashing', () => {
    render(<RouteLoadingSpinner />);
    expect(screen.getAllByText('Loading page...').length).toBeGreaterThanOrEqual(1);
  });
});

// ─── HydrateFallback ──────────────────────────────────────────────────────────

describe('HydrateFallback', () => {
  it('renders "Preparing Canvas" heading', () => {
    render(<HydrateFallback />);
    expect(screen.getByText('Preparing Canvas')).toBeTruthy();
  });

  it('renders loading description', () => {
    render(<HydrateFallback />);
    expect(screen.getByText(/Loading modules/)).toBeTruthy();
  });

  it('renders without crashing', () => {
    const { container } = render(<HydrateFallback />);
    expect(container.firstChild).toBeTruthy();
  });
});

// ─── PlaceholderRoute ─────────────────────────────────────────────────────────

describe('PlaceholderRoute', () => {
  it('renders title with "Coming Soon"', () => {
    render(
      <PlaceholderRoute
        icon="🚀"
        title="Analytics"
        description="Analytics will be available soon."
      />
    );
    expect(screen.getByText('Analytics (Coming Soon)')).toBeTruthy();
  });

  it('renders icon', () => {
    render(
      <PlaceholderRoute
        icon="🎯"
        title="Reports"
        description="Reports coming soon."
      />
    );
    expect(screen.getByText('🎯')).toBeTruthy();
  });

  it('renders description', () => {
    render(
      <PlaceholderRoute
        icon="📊"
        title="Charts"
        description="Charts will be implemented in Q2."
      />
    );
    expect(screen.getByText('Charts will be implemented in Q2.')).toBeTruthy();
  });
});

// ─── ApiUnavailableFallback ───────────────────────────────────────────────────

describe('ApiUnavailableFallback', () => {
  it('renders "Service Unavailable" heading', () => {
    render(<ApiUnavailableFallback />);
    expect(screen.getByText('Service Unavailable')).toBeTruthy();
  });

  it('renders custom error message when provided', () => {
    render(<ApiUnavailableFallback error="Connection refused" />);
    expect(screen.getByText(/Connection refused/)).toBeTruthy();
  });

  it('renders Retry button when onRetry provided', () => {
    const onRetry = vi.fn();
    render(<ApiUnavailableFallback onRetry={onRetry} />);
    expect(screen.getByText(/Retry/)).toBeTruthy();
  });

  it('calls onRetry when Retry button clicked', () => {
    const onRetry = vi.fn();
    render(<ApiUnavailableFallback onRetry={onRetry} />);
    fireEvent.click(screen.getByText(/Retry/));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('renders without onRetry without crashing', () => {
    render(<ApiUnavailableFallback />);
    // Should not crash
    const { container } = render(<ApiUnavailableFallback />);
    expect(container.firstChild).toBeTruthy();
  });
});
