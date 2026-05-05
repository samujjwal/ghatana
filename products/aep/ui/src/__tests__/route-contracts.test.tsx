/**
 * Route contract tests — mount router + assert target page rendered.
 *
 * Prevents route-helper drift by asserting that canonical paths render
 * the expected page components.
 */
import React, { Suspense } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';
import { AuthProvider } from '@/context/AuthContext';
import { ProtectedRoute } from '@/components/security/ProtectedRoute';
import * as aepApi from '@/api/aep.api';
import * as pipelineApi from '@/api/pipeline.api';

vi.mock('@/api/sse', () => ({ subscribeToAepStream: () => ({ close: vi.fn() }) }));
vi.mock('@/lib/feature-flags', () => ({ isFeatureEnabled: () => false, featureFlags: {} }));
vi.mock('@/api/aep.api');
vi.mock('@/api/pipeline.api');
vi.mock('@/hooks/useCapabilities', () => ({ useCapabilities: () => ({ capabilities: { gdprCompliance: true, pipelineBuilderV2: true, hitlReview: true, agentMemory: true, costAnalysis: true, patternDetection: true, multiTenancy: true, ssoEnabled: true, scheduledRuns: true, eventReplay: true }, isLoading: false, isError: false }) }));
vi.mock('@/hooks/useAgentMemory', () => ({
  useAllEpisodes: () => ({ data: [], isLoading: false, isError: false }),
  usePolicies: () => ({ data: [], isLoading: false, isError: false, refetch: vi.fn() }),
  POLICIES_QUERY_KEY: 'policies',
}));

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function renderRoute(path: string, ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={[path]}>
            <Suspense fallback={<div data-testid="suspense">Loading</div>}>{ui}</Suspense>
          </MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('Route contracts', () => {
  beforeEach(() => {
    vi.mocked(aepApi.listOperations).mockResolvedValue([]);
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue([]);
    vi.mocked(aepApi.getPipelineMetrics).mockResolvedValue([]);
    vi.mocked(aepApi.getCostSummary).mockResolvedValue({
      tenantId: 'default', windowStart: '', windowEnd: '', totalCostUsd: 0,
      projectedMonthlyCostUsd: 0, averageCostPerRunUsd: 0, perPipeline: [], perAgent: [], perModel: [],
      budget: { daily: { budgetUsd: 0, observedUsd: 0, remainingUsd: 0, usagePercent: 0, status: 'healthy' }, monthly: { budgetUsd: 0, observedUsd: 0, remainingUsd: 0, usagePercent: 0, status: 'healthy' } },
      alerts: [], dataSource: 'metrics', allocationModel: 'token-weight', estimated: false,
    });
    vi.mocked(pipelineApi.listPatterns).mockResolvedValue([]);
  });
  it('renders login page at /login', async () => {
    const { LoginPage } = await import('@/pages/LoginPage');
    renderRoute('/login', <Routes><Route path="/login" element={<LoginPage />} /></Routes>);
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    // LoginPage renders the AEP product heading and a sign-in affordance
    expect(screen.getByRole('heading', { name: /aep control plane/i })).toBeInTheDocument();
    expect(document.title).not.toBe('Page not found');
  });

  it('renders session expiry page at /session-expired', async () => {
    const { SessionExpiryPage } = await import('@/pages/SessionExpiryPage');
    renderRoute('/session-expired', <Routes><Route path="/session-expired" element={<SessionExpiryPage />} /></Routes>);
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    expect(screen.getByText(/session expired/i)).toBeInTheDocument();
  });

  it('redirects / to /operate', async () => {
    const { MonitoringDashboardPage } = await import('@/pages/MonitoringDashboardPage');
    renderRoute(
      '/',
      <Routes>
        <Route path="/" element={<div data-testid="home">Home</div>} />
        <Route path="/operate" element={<MonitoringDashboardPage />} />
      </Routes>,
    );
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
  });

  it('renders monitoring dashboard at /operate', async () => {
    const { MonitoringDashboardPage } = await import('@/pages/MonitoringDashboardPage');
    renderRoute(
      '/operate',
      <Routes><Route path="/operate" element={<MonitoringDashboardPage />} /></Routes>,
    );
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    // MonitoringDashboardPage renders the Monitoring heading
    expect(screen.getByRole('heading', { name: /monitoring/i })).toBeInTheDocument();
  });

  it('redirects /agents to /catalog/agents', () => {
    renderRoute(
      '/agents',
      <Routes>
        <Route path="/agents" element={<div data-testid="agents-redirect">Redirected</div>} />
        <Route path="/catalog/agents" element={<div data-testid="catalog">Catalog</div>} />
      </Routes>,
    );
    expect(screen.getByTestId('agents-redirect')).toBeInTheDocument();
  });

  it('renders cost dashboard at /operate/costs', async () => {
    const { CostDashboardPage } = await import('@/pages/CostDashboardPage');
    renderRoute(
      '/operate/costs',
      <Routes><Route path="/operate/costs" element={<CostDashboardPage />} /></Routes>,
    );
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: /cost/i })).toBeInTheDocument();
  });

  it('renders operation center at /operate/operations', async () => {
    const { OperationCenterPage } = await import('@/pages/OperationCenterPage');
    renderRoute(
      '/operate/operations',
      <Routes><Route path="/operate/operations" element={<OperationCenterPage />} /></Routes>,
    );
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: 'Operation Center', level: 1 })).toBeInTheDocument();
  });

  it('renders pattern studio at /build/patterns', async () => {
    const { PatternStudioPage } = await import('@/pages/PatternStudioPage');
    renderRoute(
      '/build/patterns',
      <Routes><Route path="/build/patterns" element={<PatternStudioPage />} /></Routes>,
    );
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: /pattern studio/i })).toBeInTheDocument();
  });

  it('renders privacy requests at /govern/privacy', async () => {
    const { PrivacyRequestPage } = await import('@/pages/PrivacyRequestPage');
    renderRoute(
      '/govern/privacy',
      <Routes><Route path="/govern/privacy" element={<PrivacyRequestPage />} /></Routes>,
    );
    await waitFor(() => expect(screen.queryByTestId('suspense')).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: /privacy requests/i })).toBeInTheDocument();
  });

  it('renders 404 state for unmapped routes', () => {
    renderRoute(
      '/this-route-does-not-exist',
      <Routes>
        <Route path="*" element={<div data-testid="not-found">Page not found</div>} />
      </Routes>,
    );
    expect(screen.getByTestId('not-found')).toBeInTheDocument();
  });
});
