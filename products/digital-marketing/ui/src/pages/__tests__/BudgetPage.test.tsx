/**
 * Budget Page tests.
 *
 * @doc.type test
 * @doc.purpose Unit tests for BudgetPage behavior
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';
import { AuthProvider } from '@/context/AuthContext';
import { BudgetPage } from '@/pages/BudgetPage';
import type { BudgetRecommendation } from '@/types/budget';

const mockBudget: BudgetRecommendation = {
  recommendationId: 'br-1',
  workspaceId: 'ws-1',
  strategyId: 'strat-1',
  status: 'DRAFT',
  totalMonthlyCap: 5000,
  changeThresholdPct: 10,
  channelAllocations: [
    {
      channelType: 'EMAIL',
      recommendedAmount: 2000,
      dailyCap: 66,
      rationale: 'High ROI channel',
    },
    {
      channelType: 'PAID_SEARCH',
      recommendedAmount: 3000,
      dailyCap: 100,
      rationale: 'Primary acquisition',
    },
  ],
  rationale: 'Balanced mix for Q4',
  assumptions: 'Based on historical data',
  modelVersion: 'budget-v1',
  generatedAt: '2026-01-10T10:00:00Z',
  generatedBy: 'user-1',
  approvedAt: null,
  approvedBy: null,
};

const mockUseBudgetRecommendation = vi.fn();
const mockUseGenerateBudget = vi.fn();
const mockUseSubmitBudgetApproval = vi.fn();
const mockUseApproveBudget = vi.fn();

vi.mock('@/hooks/useBudget', () => ({
  useBudgetRecommendation: (...args: unknown[]) => mockUseBudgetRecommendation(...args),
  useGenerateBudget: (...args: unknown[]) => mockUseGenerateBudget(...args),
  useSubmitBudgetApproval: (...args: unknown[]) => mockUseSubmitBudgetApproval(...args),
  useApproveBudget: (...args: unknown[]) => mockUseApproveBudget(...args),
}));

function buildQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage(
  token: string | null = 'test-token',
  roles: string[] = ['marketing-director'],
): void {
  render(
    <ThemeProvider defaultTheme="light" enableStorage={false} enableSystem={false}>
      <QueryClientProvider client={buildQueryClient()}>
        <AuthProvider
          initialToken={token}
          initialWorkspaceId="ws-1"
          initialTenantId="tenant-1"
          initialRoles={roles}
        >
          <MemoryRouter initialEntries={['/workspaces/ws-1/budget']}>
            <Routes>
              <Route path="/login" element={<div data-testid="login-page" />} />
              <Route path="/workspaces/:workspaceId/budget" element={<BudgetPage />} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

describe('BudgetPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: mockBudget,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseGenerateBudget.mockReturnValue({
      generate: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
    mockUseSubmitBudgetApproval.mockReturnValue({
      submit: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
    mockUseApproveBudget.mockReturnValue({
      approve: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
  });

  it('redirects unauthenticated user to /login', () => {
    renderPage(null);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders budget recommendation', () => {
    renderPage();
    expect(screen.getByTestId('budget-page')).toBeInTheDocument();
    expect(screen.getByTestId('budget-recommendation')).toBeInTheDocument();
    expect(screen.getByText('$5,000')).toBeInTheDocument();
  });

  it('shows empty state when no recommendation', () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: null,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId('budget-empty')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: null,
      isLoading: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId('budget-loading')).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: null,
      isLoading: false,
      isError: true,
      error: new Error('fetch failed'),
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId('budget-error')).toBeInTheDocument();
  });

  it('renders generate form', () => {
    renderPage();
    expect(screen.getByTestId('budget-strategy-id-input')).toBeInTheDocument();
    expect(screen.getByTestId('budget-cap-input')).toBeInTheDocument();
    expect(screen.getByTestId('budget-threshold-input')).toBeInTheDocument();
    expect(screen.getByTestId('generate-budget-btn')).toBeInTheDocument();
  });

  it('shows submit button for draft recommendation', () => {
    renderPage();
    expect(screen.getByTestId('submit-budget-btn')).toBeInTheDocument();
  });

  it('shows approve button for pending approval recommendation', () => {
    mockUseBudgetRecommendation.mockReturnValue({
      recommendation: { ...mockBudget, status: 'PENDING_APPROVAL' },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId('approve-budget-btn')).toBeInTheDocument();
  });

  it('disables mutation actions for viewer role', () => {
    renderPage('test-token', ['viewer']);
    expect(screen.getByTestId('generate-budget-btn')).toBeDisabled();
    expect(screen.getByTestId('submit-budget-btn')).toBeDisabled();
    expect(screen.getByTestId('budget-action-permission-banner')).toBeInTheDocument();
  });
});
