/**
 * Tests for billing and teams admin routes (C-Y9, C-Y10)
 */

import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';

// ── Mock hooks ─────────────────────────────────────────────────────────────────

const mockUseCapabilityGate = vi.hoisted(() => vi.fn());

vi.mock('../../../../hooks/useCapabilityGate', () => ({
  useCapabilityGate: mockUseCapabilityGate,
}));

vi.mock('../../../../components/route/LoadingSpinner', () => ({
  RouteLoadingSpinner: () => <div data-testid="loading-spinner" />,
}));

vi.mock('../../../../components/route/ErrorBoundary', () => ({
  RouteErrorBoundary: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// ── Billing route tests ────────────────────────────────────────────────────────

describe('Billing route (C-Y10)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows unavailable placeholder when billing backend is not live', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: false, reason: 'backend-not-live' });

    const { Component } = await import('../billing');
    render(<Component />);

    expect(screen.getByTestId('billing-unavailable')).toBeInTheDocument();
    expect(screen.queryByTestId('billing-page')).not.toBeInTheDocument();
    expect(screen.getByText(/not yet available/i)).toBeInTheDocument();
  });

  it('shows permission denied message when user lacks role', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: false, reason: 'insufficient-role' });

    const { Component } = await import('../billing');
    render(<Component />);

    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument();
  });

  it('renders billing page when granted', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: true, reason: undefined });

    const { Component } = await import('../billing');
    render(<Component />);

    expect(screen.getByTestId('billing-page')).toBeInTheDocument();
  });
});

// ── Teams route tests ─────────────────────────────────────────────────────────

describe('Teams route (C-Y10)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows unavailable placeholder when teams backend is not live', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: false, reason: 'backend-not-live' });

    const { Component } = await import('../teams');
    render(<Component />);

    expect(screen.getByTestId('teams-unavailable')).toBeInTheDocument();
    expect(screen.queryByTestId('teams-page')).not.toBeInTheDocument();
  });

  it('renders teams page when granted', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: true, reason: undefined });

    const { Component } = await import('../teams');
    render(<Component />);

    expect(screen.getByTestId('teams-page')).toBeInTheDocument();
  });
});

// ── Ops alerts route tests (C-Y9) ─────────────────────────────────────────────

describe('Ops alerts route (C-Y9)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows unavailable when ops backend not live', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: false, reason: 'backend-not-live' });

    const { Component } = await import('../../project/ops-alerts');
    render(<Component />);

    expect(screen.getByTestId('ops-unavailable')).toBeInTheDocument();
    expect(screen.queryByTestId('ops-alerts-page')).not.toBeInTheDocument();
    expect(screen.getByText(/not yet available/i)).toBeInTheDocument();
  });

  it('renders ops page when granted', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: true, reason: undefined });

    const { Component } = await import('../../project/ops-alerts');
    render(<Component />);

    expect(screen.getByTestId('ops-alerts-page')).toBeInTheDocument();
  });
});
