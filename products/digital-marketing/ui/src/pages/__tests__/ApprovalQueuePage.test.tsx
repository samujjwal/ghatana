/**
 * Approval Queue Page tests (F1-023).
 *
 * @doc.type test
 * @doc.purpose Unit tests for ApprovalQueuePage behavior
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { ApprovalQueuePage } from '@/pages/ApprovalQueuePage';
import type { ApprovalRecordResponse } from '@/types/approval';

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

const PENDING_APPROVALS: ApprovalRecordResponse[] = [
  {
    requestId: 'req-1',
    tenantId: 'tenant-1',
    workspaceId: 'ws-1',
    subjectId: 'campaign-abc',
    requestedBy: 'user-1',
    action: 'campaign-launch',
    targetType: 'CAMPAIGN_LAUNCH',
    targetId: 'campaign-abc',
    description: null,
    riskLevel: 2,
    requiredApproverRole: 'brand-manager',
    status: 'PENDING',
    submittedAt: '2026-01-01T10:00:00Z',
    submittedBy: 'user-1',
    requestedAt: '2026-01-01T10:00:00Z',
    expiresAt: null,
    decidedAt: null,
    decidedBy: null,
    reviewerId: null,
    reviewerNotes: null,
    comment: null,
    snapshotSummary: null,
    validationResultId: null,
    snapshotAt: null,
  },
  {
    requestId: 'req-2',
    tenantId: 'tenant-1',
    workspaceId: 'ws-1',
    subjectId: 'strategy-xyz',
    requestedBy: 'user-2',
    action: 'budget-approved',
    targetType: 'BUDGET',
    targetId: 'strategy-xyz',
    description: null,
    riskLevel: 2,
    requiredApproverRole: 'brand-manager',
    status: 'PENDING',
    submittedAt: '2026-01-02T09:00:00Z',
    submittedBy: 'user-2',
    requestedAt: '2026-01-02T09:00:00Z',
    expiresAt: null,
    decidedAt: null,
    decidedBy: null,
    reviewerId: null,
    reviewerNotes: null,
    comment: null,
    snapshotSummary: null,
    validationResultId: null,
    snapshotAt: null,
  },
];

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
        initialRoles={['marketing-approver']}
      >
        <MemoryRouter initialEntries={['/workspaces/ws-1/approvals']}>
          <Routes>
            <Route path="/login" element={<div data-testid="login-page" />} />
            <Route
              path="/workspaces/:workspaceId/approvals"
              element={<ApprovalQueuePage />}
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('ApprovalQueuePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListPending.mockResolvedValue(PENDING_APPROVALS);
  });

  it('redirects unauthenticated user to /login', () => {
    renderPage(null);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders approval queue with loaded rows', async () => {
    renderPage();
    expect(
      await screen.findByTestId('approval-queue-table'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('approval-row-req-1')).toBeInTheDocument();
    expect(screen.getByTestId('approval-row-req-2')).toBeInTheDocument();
  });

  it('shows empty state when no approvals', async () => {
    mockListPending.mockResolvedValue([]);
    renderPage();
    expect(
      await screen.findByTestId('approval-queue-empty'),
    ).toBeInTheDocument();
  });

  it('shows loading state initially', () => {
    mockListPending.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByTestId('approval-queue-loading')).toBeInTheDocument();
  });

  it('shows error state on API failure', async () => {
    mockListPending.mockRejectedValue(new Error('Network error'));
    renderPage();
    expect(await screen.findByTestId('approval-queue-error')).toBeInTheDocument();
    expect(screen.getByText(/Network error/i)).toBeInTheDocument();
  });

  it('renders PENDING status badge for pending approval', async () => {
    renderPage();
    const badge = await screen.findByTestId('status-badge-req-1');
    expect(badge).toHaveTextContent('PENDING');
  });

  it('filters by action type — BUDGET matches budget-approved only', async () => {
    renderPage();
    await screen.findByTestId('approval-queue-table');

    const user = userEvent.setup();
    await user.selectOptions(screen.getByTestId('filter-type'), 'BUDGET');

    expect(screen.queryByTestId('approval-row-req-1')).not.toBeInTheDocument();
    expect(screen.getByTestId('approval-row-req-2')).toBeInTheDocument();
  });

  it('shows permission-denied banner when user has no approver role', () => {
    render(
      <QueryClientProvider client={buildQueryClient()}>
        <AuthProvider
          initialToken="test-token"
          initialWorkspaceId="ws-1"
          initialTenantId="tenant-1"
          initialRoles={[]}
        >
          <MemoryRouter initialEntries={['/workspaces/ws-1/approvals']}>
            <Routes>
              <Route
                path="/workspaces/:workspaceId/approvals"
                element={<ApprovalQueuePage />}
              />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByTestId('permission-denied-banner')).toBeInTheDocument();
  });

  it('does NOT show permission-denied banner when user has approver role', async () => {
    renderPage();
    await screen.findByTestId('approval-queue-page');
    expect(
      screen.queryByTestId('permission-denied-banner'),
    ).not.toBeInTheDocument();
  });

  it('renders review links pointing to detail page', async () => {
    renderPage();
    await screen.findByTestId('approval-queue-table');
    const link = screen.getByTestId('review-link-req-1');
    expect(link).toHaveAttribute(
      'href',
      '/workspaces/ws-1/approvals/req-1',
    );
  });
});
