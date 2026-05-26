/**
 * KernelHealthDashboardPage Tests
 *
 * @doc.type test
 * @doc.purpose Integration tests for the KernelHealthDashboardPage component
 * @doc.layer product
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router';
import { KernelHealthDashboardPage } from '../KernelHealthDashboardPage';

// Mock the useKernelHealth hooks — isolates the page from network
vi.mock('../../../hooks/useKernelHealth', () => ({
  useKernelProductUnitList: vi.fn(),
  useKernelProductUnitHealth: vi.fn(),
  useKernelLifecycleTimeline: vi.fn(),
  useKernelRecommendedActions: vi.fn(),
}));

import {
  useKernelProductUnitList,
  useKernelProductUnitHealth,
  useKernelLifecycleTimeline,
  useKernelRecommendedActions,
} from '../../../hooks/useKernelHealth';

const mockUseKernelProductUnitList = vi.mocked(useKernelProductUnitList);
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
  mockUseKernelProductUnitList.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as ReturnType<typeof useKernelProductUnitList>);

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

    it('renders ProductUnit health summaries in list view', () => {
      mockUseKernelProductUnitList.mockReturnValue({
        data: [
          {
            productUnitId: 'digital-marketing',
            overallStatus: 'degraded',
            currentPhase: 'observe',
            lastRunTimestamp: '2026-05-01T10:00:00Z',
          },
        ],
        isLoading: false,
        isError: false,
        refetch: vi.fn(),
      } as ReturnType<typeof useKernelProductUnitList>);

      renderPage();

      expect(screen.getByText('digital-marketing')).toBeInTheDocument();
      expect(screen.getByText('observe')).toBeInTheDocument();
      expect(screen.getByText('degraded')).toBeInTheDocument();
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
      healthSnapshot: {
        previewSecurity: {
          previewTokenId: 'preview-token-1',
          trustLevel: 'trusted',
          acknowledgementRequired: true,
          acknowledgementStatus: 'acknowledged',
          tokenScope: [
            { id: 'scope-preview', name: 'preview:read', required: true, granted: true },
          ],
          scopeMismatches: [],
          expiresAt: '2026-05-01T12:00:00Z',
          lastRefreshed: '2026-05-01T10:00:00Z',
        },
        agentGovernance: [
          {
            agentId: 'agent-1',
            agentName: 'Delivery Agent',
            learningLevel: 'L2',
            governanceState: 'ready',
            learningEvidence: {
              semanticFacts: 12,
              negativeKnowledge: 1,
              episodicCaptures: 4,
            },
            policyBlocks: [],
            promotionQueue: null,
          },
        ],
      },
      lifecycleResult: {
        gates: [
          {
            id: 'gate-1',
            name: 'Run readiness',
            phase: 'run',
            status: 'passed',
            required: true,
            criteria: ['Assurance passed'],
            evidence: 'evidence-1',
          },
        ],
        artifacts: [
          {
            id: 'artifact-1',
            type: 'docker-image',
            surface: 'web',
            path: 'registry/app:1',
            fingerprint: 'sha256:abc',
            producedBy: 'generate-run-1',
            producedAt: '2026-05-01T10:00:00Z',
            healthCheckStatus: 'healthy',
            lastVerified: '2026-05-01T10:05:00Z',
          },
        ],
      },
      deployment: {
        id: 'deployment-1',
        status: 'deployed',
        target: 'preview-us',
        environment: 'preview',
        deployedAt: '2026-05-01T10:05:00Z',
        artifactId: 'artifact-1',
        rollbackAvailable: true,
        rollbackStatus: 'available',
        healthChecks: [
          { name: 'HTTP readiness', status: 'pass', lastChecked: '2026-05-01T10:06:00Z' },
        ],
      },
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

    it('renders Kernel truth sections from health fixture data', async () => {
      const user = userEvent.setup();

      renderPage('digital-marketing');

      await user.click(screen.getByRole('tab', { name: /^deployment$/i }));
      expect(screen.getByText('preview-us')).toBeInTheDocument();
      expect(screen.getByText('HTTP readiness')).toBeInTheDocument();

      await user.click(screen.getByRole('tab', { name: /gate health/i }));
      expect(screen.getByText('Run readiness')).toBeInTheDocument();
      expect(screen.getByText('Assurance passed')).toBeInTheDocument();
      expect(screen.getByText(/evidence-1/i)).toBeInTheDocument();

      await user.click(screen.getByRole('tab', { name: /artifacts/i }));
      expect(screen.getByText('registry/app:1')).toBeInTheDocument();
      expect(screen.getByText('sha256:abc')).toBeInTheDocument();

      await user.click(screen.getByRole('tab', { name: /agent governance/i }));
      expect(screen.getByText('Delivery Agent')).toBeInTheDocument();
      expect(screen.getByText('L2')).toBeInTheDocument();
      expect(screen.getByText('12')).toBeInTheDocument();

      await user.click(screen.getByRole('tab', { name: /preview security/i }));
      expect(screen.getByText('preview:read')).toBeInTheDocument();
      expect(screen.getAllByText('trusted')).not.toHaveLength(0);
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
