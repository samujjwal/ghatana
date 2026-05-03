/**
 * Approval Detail Page tests (F1-023).
 *
 * @doc.type test
 * @doc.purpose Unit tests for ApprovalDetailPage behavior
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { ApprovalDetailPage } from '@/pages/ApprovalDetailPage';
import type { ApprovalRequest, ApprovalSnapshot } from '@/types/approval';

vi.mock('@/lib/feature-flags', () => ({
  isFeatureEnabled: () => false,
  featureFlags: {},
}));

const mockGetStatus = vi.fn();
const mockGetSnapshot = vi.fn();
const mockDecide = vi.fn();

vi.mock('@/api/approvals', () => ({
  listPendingApprovals: vi.fn().mockResolvedValue([]),
  getApprovalStatus: (...args: unknown[]) => mockGetStatus(...args),
  getApprovalSnapshot: (...args: unknown[]) => mockGetSnapshot(...args),
  decideApproval: (...args: unknown[]) => mockDecide(...args),
  submitApproval: vi.fn(),
}));

const APPROVAL: ApprovalRequest = {
  requestId: 'req-42',
  workspaceId: 'ws-1',
  tenantId: 'tenant-1',
  targetType: 'CONTENT',
  targetId: 'content-99',
  description: 'Blog post for product launch',
  riskLevel: 2,
  status: 'PENDING',
  requiredApproverRole: 'content-approver',
  submittedAt: '2026-01-05T08:00:00Z',
  decidedAt: null,
  decidedBy: null,
  comment: null,
};

const HIGH_RISK_APPROVAL: ApprovalRequest = {
  ...APPROVAL,
  requestId: 'req-99',
  riskLevel: 5,
};

const SNAPSHOT: ApprovalSnapshot = {
  requestId: 'req-42',
  capturedAt: '2026-01-05T08:00:01Z',
  fields: [
    { key: 'title', value: 'Product Launch Blog' },
    { key: 'wordCount', value: 800 },
  ],
};

function buildQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage(
  requestId: string,
  roles: string[] = ['content-approver'],
  token: string | null = 'test-token',
): void {
  render(
    <QueryClientProvider client={buildQueryClient()}>
      <AuthProvider
        initialToken={token}
        initialWorkspaceId="ws-1"
        initialTenantId="tenant-1"
        initialRoles={roles}
      >
        <MemoryRouter
          initialEntries={[`/workspaces/ws-1/approvals/${requestId}`]}
        >
          <Routes>
            <Route path="/login" element={<div data-testid="login-page" />} />
            <Route
              path="/workspaces/:workspaceId/approvals/:requestId"
              element={<ApprovalDetailPage />}
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('ApprovalDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetStatus.mockResolvedValue(APPROVAL);
    mockGetSnapshot.mockResolvedValue(SNAPSHOT);
    mockDecide.mockResolvedValue({ ...APPROVAL, status: 'APPROVED' });
  });

  it('redirects unauthenticated user to /login', () => {
    renderPage('req-42', [], null);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders approval detail with status badge', async () => {
    renderPage('req-42');
    expect(
      await screen.findByTestId('approval-status-badge'),
    ).toHaveTextContent('PENDING');
  });

  it('renders snapshot fields', async () => {
    renderPage('req-42');
    await screen.findByTestId('approval-snapshot-panel');
    expect(screen.getByTestId('snapshot-field-title')).toHaveTextContent(
      'Product Launch Blog',
    );
    expect(screen.getByTestId('snapshot-field-wordCount')).toHaveTextContent(
      '800',
    );
  });

  it('shows loading state initially', () => {
    mockGetStatus.mockReturnValue(new Promise(() => {}));
    mockGetSnapshot.mockReturnValue(new Promise(() => {}));
    renderPage('req-42');
    expect(screen.getByTestId('approval-detail-loading')).toBeInTheDocument();
  });

  it('shows error on API failure', async () => {
    mockGetStatus.mockRejectedValue(new Error('Not found'));
    renderPage('req-42');
    await screen.findByTestId('approval-detail-error');
    expect(screen.getByText(/Not found/i)).toBeInTheDocument();
  });

  it('shows decide button for authorised approver', async () => {
    renderPage('req-42');
    expect(await screen.findByTestId('open-decide-dialog')).toBeInTheDocument();
  });

  it('hides decide button for unauthorised viewer', async () => {
    renderPage('req-42', ['viewer']);
    await screen.findByTestId('approval-status-badge');
    expect(screen.queryByTestId('open-decide-dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('approval-permission-denied')).toBeInTheDocument();
  });

  it('opens decide dialog on button click', async () => {
    renderPage('req-42');
    const user = userEvent.setup();
    await user.click(await screen.findByTestId('open-decide-dialog'));
    expect(screen.getByTestId('decide-dialog')).toBeInTheDocument();
  });

  it('submits approval decision', async () => {
    renderPage('req-42');
    const user = userEvent.setup();
    await user.click(await screen.findByTestId('open-decide-dialog'));

    await user.click(screen.getByTestId('decide-submit'));

    await waitFor(() =>
      expect(mockDecide).toHaveBeenCalledWith('ws-1', 'req-42', {
        decision: 'APPROVE',
        comment: undefined,
      }),
    );
  });

  it('requires comment for high-risk approval rejection', async () => {
    mockGetStatus.mockResolvedValue(HIGH_RISK_APPROVAL);
    renderPage('req-99');
    const user = userEvent.setup();
    await user.click(await screen.findByTestId('open-decide-dialog'));

    // Submit is disabled until comment is entered
    const submitBtn = screen.getByTestId('decide-submit');
    expect(submitBtn).toBeDisabled();

    await user.type(screen.getByTestId('decide-comment'), 'Needs revision');
    expect(submitBtn).not.toBeDisabled();
  });

  it('dismisses decide dialog on cancel', async () => {
    renderPage('req-42');
    const user = userEvent.setup();
    await user.click(await screen.findByTestId('open-decide-dialog'));
    await user.click(screen.getByTestId('decide-cancel'));
    expect(screen.queryByTestId('decide-dialog')).not.toBeInTheDocument();
  });
});
