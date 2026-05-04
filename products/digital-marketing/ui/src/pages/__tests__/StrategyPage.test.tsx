/**
 * Strategy Page tests.
 *
 * @doc.type test
 * @doc.purpose Unit tests for StrategyPage behavior
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { StrategyPage } from '@/pages/StrategyPage';
import type { MarketingStrategy } from '@/types/strategy';

const mockStrategy: MarketingStrategy = {
  strategyId: 'strat-1',
  workspaceId: 'ws-1',
  status: 'DRAFT',
  goals: [
    {
      goalType: 'AWARENESS',
      description: 'Increase brand awareness',
      targetMetric: 'impressions',
      measurementMethod: 'Google Analytics',
    },
  ],
  channelPlans: [
    {
      channelType: 'EMAIL',
      objective: 'Nurture leads',
      estimatedBudget: 2000,
      keyMessages: ['Welcome offer'],
      targetKeywords: [],
    },
  ],
  budgetCap: 5000,
  rationale: 'Data-driven approach',
  assumptions: 'Based on Q3 performance',
  measurementPlan: 'Weekly KPI reviews',
  contentPlan: 'Blog + email sequence',
  modelVersion: 'strategy-v1',
  generatedAt: '2026-01-10T10:00:00Z',
  generatedBy: 'user-1',
  approvedAt: null,
  approvedBy: null,
};

const mockUseStrategy = vi.fn();
const mockUseGenerateStrategy = vi.fn();
const mockUseSubmitStrategyApproval = vi.fn();
const mockUseApproveStrategy = vi.fn();

vi.mock('@/hooks/useStrategy', () => ({
  useStrategy: (...args: unknown[]) => mockUseStrategy(...args),
  useGenerateStrategy: (...args: unknown[]) => mockUseGenerateStrategy(...args),
  useSubmitStrategyApproval: (...args: unknown[]) => mockUseSubmitStrategyApproval(...args),
  useApproveStrategy: (...args: unknown[]) => mockUseApproveStrategy(...args),
}));

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
        <MemoryRouter initialEntries={['/workspaces/ws-1/strategy']}>
          <Routes>
            <Route path="/login" element={<div data-testid="login-page" />} />
            <Route path="/workspaces/:workspaceId/strategy" element={<StrategyPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('StrategyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseStrategy.mockReturnValue({
      strategy: mockStrategy,
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseGenerateStrategy.mockReturnValue({
      generate: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
    mockUseSubmitStrategyApproval.mockReturnValue({
      submit: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
    mockUseApproveStrategy.mockReturnValue({
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

  it('renders strategy detail', () => {
    renderPage();
    expect(screen.getByTestId('strategy-page')).toBeInTheDocument();
    expect(screen.getByTestId('strategy-detail')).toBeInTheDocument();
    expect(screen.getByText('$5,000')).toBeInTheDocument();
  });

  it('shows empty state when no strategy', () => {
    mockUseStrategy.mockReturnValue({
      strategy: null,
      isLoading: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId('strategy-empty')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockUseStrategy.mockReturnValue({
      strategy: null,
      isLoading: true,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId('strategy-loading')).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockUseStrategy.mockReturnValue({
      strategy: null,
      isLoading: false,
      isError: true,
      error: new Error('fetch failed'),
    });
    renderPage();
    expect(screen.getByTestId('strategy-error')).toBeInTheDocument();
  });

  it('renders generate form', () => {
    renderPage();
    expect(screen.getByTestId('strategy-service-area-input')).toBeInTheDocument();
    expect(screen.getByTestId('strategy-offer-input')).toBeInTheDocument();
    expect(screen.getByTestId('strategy-budget-input')).toBeInTheDocument();
    expect(screen.getByTestId('generate-strategy-btn')).toBeInTheDocument();
  });

  it('shows submit button for draft strategy', () => {
    renderPage();
    expect(screen.getByTestId('submit-strategy-btn')).toBeInTheDocument();
  });

  it('shows approve button for pending approval strategy', () => {
    mockUseStrategy.mockReturnValue({
      strategy: { ...mockStrategy, status: 'PENDING_APPROVAL' },
      isLoading: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId('approve-strategy-btn')).toBeInTheDocument();
  });
});
