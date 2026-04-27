/**
 * RateLimitStatusWidget + ThrottleAlertBanner tests
 *
 * All network calls are intercepted by mocking @tanstack/react-query so
 * no real fetch requests are made.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { RateLimitStatusWidget } from '../RateLimitStatusWidget';
import { ThrottleAlertBanner } from '../ThrottleAlertBanner';

// --------------------------------------------------------------------------
// Mock @tanstack/react-query
// --------------------------------------------------------------------------
const useQueryMock = vi.hoisted(() => vi.fn());

vi.mock('@tanstack/react-query', () => ({
  useQuery: useQueryMock,
}));

// --------------------------------------------------------------------------
// Mock http helpers so the component doesn't call real fetch
// --------------------------------------------------------------------------
vi.mock('@/lib/http', () => ({
  parseJsonResponse: vi.fn(),
  readErrorResponse: vi.fn(),
}));

// --------------------------------------------------------------------------
// Fixtures
// --------------------------------------------------------------------------
const lowUsageStatus = {
  identifier: 'user-123',
  tier: 'pro',
  used: 100,
  limit: 1000,
  remaining: 900,
  percentage: 10,
  resetTime: new Date(Date.now() + 3_600_000),
  isLimited: false,
};

const highUsageStatus = {
  ...lowUsageStatus,
  used: 870,
  remaining: 130,
  percentage: 87,
};

const limitExceededStatus = {
  ...lowUsageStatus,
  used: 1000,
  remaining: 0,
  percentage: 100,
  isLimited: true,
};

const enterpriseStatus = {
  ...lowUsageStatus,
  tier: 'enterprise',
};

// --------------------------------------------------------------------------
// RateLimitStatusWidget
// --------------------------------------------------------------------------
describe('RateLimitStatusWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton while fetching', () => {
    useQueryMock.mockReturnValue({ data: undefined, isLoading: true, refetch: vi.fn() });
    const { container } = render(<RateLimitStatusWidget />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders error state when no data', () => {
    useQueryMock.mockReturnValue({ data: undefined, isLoading: false, refetch: vi.fn() });
    render(<RateLimitStatusWidget />);
    expect(screen.getByText(/failed to load rate limit status/i)).toBeInTheDocument();
  });

  it('renders tier badge and usage when data is available', () => {
    useQueryMock.mockReturnValue({ data: lowUsageStatus, isLoading: false, refetch: vi.fn() });
    render(<RateLimitStatusWidget />);
    expect(screen.getByText(/PRO TIER/)).toBeInTheDocument();
    expect(screen.getByText('100')).toBeInTheDocument();
  });

  it('shows upgrade button for non-enterprise tiers', () => {
    useQueryMock.mockReturnValue({ data: lowUsageStatus, isLoading: false, refetch: vi.fn() });
    const onUpgrade = vi.fn();
    render(<RateLimitStatusWidget onUpgrade={onUpgrade} />);
    expect(screen.getByRole('button', { name: /upgrade/i })).toBeInTheDocument();
  });

  it('does not show upgrade button for enterprise tier', () => {
    useQueryMock.mockReturnValue({ data: enterpriseStatus, isLoading: false, refetch: vi.fn() });
    render(<RateLimitStatusWidget onUpgrade={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /upgrade/i })).toBeNull();
  });

  it('calls onUpgrade when upgrade button is clicked', () => {
    useQueryMock.mockReturnValue({ data: lowUsageStatus, isLoading: false, refetch: vi.fn() });
    const onUpgrade = vi.fn();
    render(<RateLimitStatusWidget onUpgrade={onUpgrade} />);
    fireEvent.click(screen.getByRole('button', { name: /upgrade/i }));
    expect(onUpgrade).toHaveBeenCalledTimes(1);
  });

  it('shows rate limit exceeded warning when isLimited is true', () => {
    useQueryMock.mockReturnValue({ data: limitExceededStatus, isLoading: false, refetch: vi.fn() });
    render(<RateLimitStatusWidget />);
    expect(screen.getByText(/rate limit exceeded/i)).toBeInTheDocument();
  });

  it('shows approaching-limit warning when percentage >= 80 and not limited', () => {
    useQueryMock.mockReturnValue({ data: highUsageStatus, isLoading: false, refetch: vi.fn() });
    render(<RateLimitStatusWidget />);
    expect(screen.getByText(/approaching limit/i)).toBeInTheDocument();
  });

  it('shows view details button when onViewDetails is provided', () => {
    useQueryMock.mockReturnValue({ data: lowUsageStatus, isLoading: false, refetch: vi.fn() });
    const onViewDetails = vi.fn();
    render(<RateLimitStatusWidget onViewDetails={onViewDetails} />);
    const link = screen.getByRole('button', { name: /view detailed usage/i });
    fireEvent.click(link);
    expect(onViewDetails).toHaveBeenCalledTimes(1);
  });

  it('renders remaining requests count', () => {
    useQueryMock.mockReturnValue({ data: lowUsageStatus, isLoading: false, refetch: vi.fn() });
    render(<RateLimitStatusWidget />);
    // remaining = 900 formatted as "900"
    expect(screen.getByText('900')).toBeInTheDocument();
  });
});

// --------------------------------------------------------------------------
// ThrottleAlertBanner
// --------------------------------------------------------------------------
describe('ThrottleAlertBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when usage is below threshold', () => {
    useQueryMock.mockReturnValue({ data: lowUsageStatus });
    const { container } = render(<ThrottleAlertBanner threshold={80} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders alert when usage meets threshold', () => {
    useQueryMock.mockReturnValue({ data: highUsageStatus });
    render(<ThrottleAlertBanner threshold={80} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('renders alert when rate limit is exceeded', () => {
    useQueryMock.mockReturnValue({ data: limitExceededStatus });
    render(<ThrottleAlertBanner />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/rate limit exceeded/i)).toBeInTheDocument();
  });

  it('dismisses alert when close button is clicked', () => {
    useQueryMock.mockReturnValue({ data: highUsageStatus });
    render(<ThrottleAlertBanner threshold={80} />);
    const dismiss = screen.getByLabelText(/dismiss alert/i);
    fireEvent.click(dismiss);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('calls onDismiss callback when dismissed', () => {
    useQueryMock.mockReturnValue({ data: highUsageStatus });
    const onDismiss = vi.fn();
    render(<ThrottleAlertBanner threshold={80} onDismiss={onDismiss} />);
    fireEvent.click(screen.getByLabelText(/dismiss alert/i));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('shows upgrade button for non-enterprise tier', () => {
    useQueryMock.mockReturnValue({ data: highUsageStatus });
    render(<ThrottleAlertBanner threshold={80} onUpgrade={vi.fn()} />);
    expect(screen.getByRole('button', { name: /upgrade plan/i })).toBeInTheDocument();
  });

  it('does not show upgrade button for enterprise tier', () => {
    useQueryMock.mockReturnValue({ data: { ...highUsageStatus, tier: 'enterprise' } });
    render(<ThrottleAlertBanner threshold={80} onUpgrade={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /upgrade plan/i })).toBeNull();
  });

  it('calls onUpgrade when upgrade plan button is clicked', () => {
    useQueryMock.mockReturnValue({ data: highUsageStatus });
    const onUpgrade = vi.fn();
    render(<ThrottleAlertBanner threshold={80} onUpgrade={onUpgrade} />);
    fireEvent.click(screen.getByRole('button', { name: /upgrade plan/i }));
    expect(onUpgrade).toHaveBeenCalledTimes(1);
  });

  it('shows view details button when onViewDetails is provided', () => {
    useQueryMock.mockReturnValue({ data: highUsageStatus });
    const onViewDetails = vi.fn();
    render(<ThrottleAlertBanner threshold={80} onViewDetails={onViewDetails} />);
    fireEvent.click(screen.getByRole('button', { name: /view details/i }));
    expect(onViewDetails).toHaveBeenCalledTimes(1);
  });

  it('renders nothing when no data is available', () => {
    useQueryMock.mockReturnValue({ data: undefined });
    const { container } = render(<ThrottleAlertBanner />);
    expect(container.firstChild).toBeNull();
  });
});
