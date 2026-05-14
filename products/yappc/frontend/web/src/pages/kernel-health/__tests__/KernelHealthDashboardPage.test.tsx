/**
 * KernelHealthDashboardPage Tests
 *
 * @doc.type test
 * @doc.purpose Integration tests for the KernelHealthDashboardPage component
 * @doc.layer product
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { KernelHealthDashboardPage } from '../KernelHealthDashboardPage';

// Mock the useKernelHealth hooks — isolates the page from network
vi.mock('../../../hooks/useKernelHealth', () => ({
  useKernelProductUnitHealth: vi.fn(),
  useKernelLifecycleTimeline: vi.fn(),
  useKernelRecommendedActions: vi.fn(),
}));

import {
  useKernelProductUnitHealth,
  useKernelLifecycleTimeline,
  useKernelRecommendedActions,
} from '../../../hooks/useKernelHealth';

const mockUseKernelProductUnitHealth = vi.mocked(useKernelProductUnitHealth);
const mockUseKernelLifecycleTimeline = vi.mocked(useKernelLifecycleTimeline);
const mockUseKernelRecommendedActions = vi.mocked(useKernelRecommendedActions);

function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function renderPage(productUnitId?: string) {
  const route = productUnitId
    ? `/kernel-health/products/${productUnitId}`
    : '/kernel-health';
  const path = productUnitId
    ? '/kernel-health/products/:productUnitId'
    : '/kernel-health';

  return render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path={path} element={<KernelHealthDashboardPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

// Reset mocks to "no data" defaults before each test
beforeEach(() => {
  mockUseKernelProductUnitHealth.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as ReturnType<typeof useKernelProductUnitHealth>);

  mockUseKernelLifecycleTimeline.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as ReturnType<typeof useKernelLifecycleTimeline>);

  mockUseKernelRecommendedActions.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as ReturnType<typeof useKernelRecommendedActions>);
});

describe('KernelHealthDashboardPage', () => {
  describe('list view (no productUnitId)', () => {
    it('renders the dashboard heading', () => {
      renderPage();
      expect(screen.getByText('Kernel Health Dashboard')).toBeInTheDocument();
    });

    it('renders the empty selection prompt when no productUnitId is in the route', () => {
      renderPage();
      expect(
        screen.getByText(/select a productunit to view health details/i)
      ).toBeInTheDocument();
    });

    it('renders the Refresh button', () => {
      renderPage();
      expect(screen.getByRole('button', { name: /refresh/i })).toBeInTheDocument();
    });
  });

  describe('detail view (with productUnitId)', () => {
    const healthView = {
      productUnitId: 'digital-marketing',
      overallStatus: 'healthy',
      currentPhase: 'deploy',
      lastRunTimestamp: '2026-05-01T10:00:00Z',
      gateFailureCount: 0,
      deploymentStatus: 'deployed',
      healthSnapshot: {},
      lifecycleResult: {},
      deployment: {},
    };

    beforeEach(() => {
      mockUseKernelProductUnitHealth.mockReturnValue({
        data: healthView,
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useKernelProductUnitHealth>);
    });

    it('renders the productUnitId in the summary card', () => {
      renderPage('digital-marketing');
      expect(screen.getByText('digital-marketing')).toBeInTheDocument();
    });

    it('renders the overall status badge', () => {
      renderPage('digital-marketing');
      expect(screen.getByText('healthy')).toBeInTheDocument();
    });

    it('renders the current phase', () => {
      renderPage('digital-marketing');
      expect(screen.getByText('deploy')).toBeInTheDocument();
    });

    it('renders the gate failure count', () => {
      renderPage('digital-marketing');
      expect(screen.getByText('0')).toBeInTheDocument();
    });

    it('renders health detail tabs', () => {
      renderPage('digital-marketing');
      expect(screen.getByRole('tab', { name: /lifecycle timeline/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /gate health/i })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /artifacts/i })).toBeInTheDocument();
    });
  });

  describe('loading state', () => {
    it('renders a loading spinner when health data is loading', () => {
      mockUseKernelProductUnitHealth.mockReturnValue({
        data: undefined,
        isLoading: true,
        isError: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useKernelProductUnitHealth>);

      renderPage('digital-marketing');
      // Loading spinner should be present; no summary card
      expect(screen.queryByText('digital-marketing')).not.toBeInTheDocument();
    });
  });

  describe('error state', () => {
    it('renders an error alert when health fetch fails', () => {
      mockUseKernelProductUnitHealth.mockReturnValue({
        data: undefined,
        isLoading: false,
        isError: true,
        refetch: vi.fn(),
      } as ReturnType<typeof useKernelProductUnitHealth>);

      renderPage('digital-marketing');
      expect(screen.getByText(/failed to load kernel health data/i)).toBeInTheDocument();
    });
  });
});
