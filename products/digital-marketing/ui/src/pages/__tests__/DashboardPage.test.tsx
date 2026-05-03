/**
 * Dashboard Page tests (F1-024).
 *
 * @doc.type test
 * @doc.purpose Unit tests for DashboardPage behavior
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { DashboardPage } from '@/pages/DashboardPage';
import type { ApprovalRequest } from '@/types/approval';

vi.mock('@/hooks/useAiActionLog', () => ({
  useAiActionLog: () => ({
    entries: [],
    isLoading: false,
    isError: false,
    error: null,
  }),
}));

vi.mock('@/lib/feature-flags', () => ({
  isFeatureEnabled: () => false,
  featureFlags: {},
}));

const mockListPending = vi.fn();

vi.mock('@/api/approvals', () => ({
  listPendingApprovals: (...args: unknown[]) => mockListPending(...args),
  getApprovalStatus: vi.fn(),
  getApprovalSnapshot: vi.fn(),
  decideApproval: vi.fn(),
  submitApproval: vi.fn(),
}));

const PENDING: ApprovalRequest = {
  requestId: 'req-5',
  workspaceId: 'ws-1',
  tenantId: 'tenant-1',
  targetType: 'CAMPAIGN',
  targetId: 'c-1',
  description: 'Q4 campaign',
  riskLevel: 3,
  status: 'PENDING',
  requiredApproverRole: 'admin',
  submittedAt: '2026-01-10T10:00:00Z',
  decidedAt: null,
  decidedBy: null,
  comment: null,
};

function buildQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage(token: string | null = 'test-token'): void {
  render(
    <QueryClientProvider client={buildQueryClient()}>
      <AuthProvider
        initialToken={token}
        initialWorkspaceId="ws-1"
        initialTenantId="tenant-1"
        initialRoles={[]}
      >
        <MemoryRouter initialEntries={['/workspaces/ws-1/dashboard']}>
          <Routes>
            <Route path="/login" element={<div data-testid="login-page" />} />
            <Route
              path="/workspaces/:workspaceId/dashboard"
              element={<DashboardPage />}
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListPending.mockResolvedValue([]);
  });

  it('redirects unauthenticated user to /login', () => {
    renderPage(null);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders all dashboard widgets', async () => {
    renderPage();
    expect(await screen.findByTestId('approval-widget')).toBeInTheDocument();
    expect(screen.getByTestId('workflow-status-widget')).toBeInTheDocument();
    expect(screen.getByTestId('growth-goal-widget')).toBeInTheDocument();
    expect(screen.getByTestId('risk-compliance-widget')).toBeInTheDocument();
    expect(screen.getByTestId('ai-action-log-widget')).toBeInTheDocument();
  });

  it('shows pending approval alert when there are pending approvals', async () => {
    mockListPending.mockResolvedValue([PENDING]);
    renderPage();
    const alert = await screen.findByTestId('dashboard-approval-alert');
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent('1');
  });

  it('does not show alert when no pending approvals', async () => {
    mockListPending.mockResolvedValue([]);
    renderPage();
    await screen.findByTestId('approval-widget');
    expect(
      screen.queryByTestId('dashboard-approval-alert'),
    ).not.toBeInTheDocument();
  });

  it('approval widget shows link to queue', async () => {
    mockListPending.mockResolvedValue([]);
    renderPage();
    const link = await screen.findByTestId('approval-widget-link');
    expect(link).toHaveAttribute('href', '/workspaces/ws-1/approvals');
  });

  it('shows loading state in approval widget while fetching', () => {
    mockListPending.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(
      screen.getByTestId('approval-widget-loading'),
    ).toBeInTheDocument();
  });

  it('shows error in approval widget on failure', async () => {
    mockListPending.mockRejectedValue(new Error('fetch failed'));
    renderPage();
    expect(
      await screen.findByTestId('approval-widget-error'),
    ).toBeInTheDocument();
  });

  it('dashboard page title is present', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: /dashboard/i })).toBeInTheDocument();
  });
});
