import { describe, it, expect, beforeEach, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import { InsightsPage } from '../../pages/InsightsPage';

const mockNavigate = vi.fn();

const analyticsMocks = vi.hoisted(() => ({
  useAnalyticsQuery: vi.fn(),
  useCollectionEntityCounts: vi.fn(),
  useAnalyticsAiSuggestions: vi.fn(),
  useCapabilityRegistry: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('../../api/brain.service', () => ({
  brainService: {
    getBrainStats: vi.fn().mockResolvedValue({
      totalRecordsProcessed: 1234,
      activePatterns: 7,
      hotTierRecords: 55,
    }),
  },
}));

vi.mock('../../api/cost.service', () => ({
  costService: {
    getCostAnalysis: vi.fn().mockResolvedValue({ total: 2450 }),
  },
}));

vi.mock('../../lib/api/workflows', () => ({
  workflowsApi: {
    list: vi.fn().mockResolvedValue({ total: 9 }),
  },
}));

vi.mock('../../api/analytics.service', () => analyticsMocks);

vi.mock('../../api/capabilities.service', () => ({
  useCapabilityRegistry: analyticsMocks.useCapabilityRegistry,
  getCapabilitySignal: (capabilities: Array<{ key: string }> | undefined, aliases: string[]) =>
    capabilities?.find((capability) => aliases.includes(capability.key)),
}));

vi.mock('../../lib/api/data-cloud-api', () => ({
  dataCloudApi: {
    getCollections: vi.fn().mockResolvedValue([{ id: 'orders', name: 'orders' }]),
  },
}));

vi.mock('../../components/layout/PageLayout', () => ({
  PageHeader: ({ title, subtitle, actions }: { title: string; subtitle: string; actions?: React.ReactNode }) => (
    <div>
      <h1>{title}</h1>
      <p>{subtitle}</p>
      {actions}
    </div>
  ),
  PageContent: ({ children, aiSidebar }: { children: React.ReactNode; aiSidebar?: React.ReactNode }) => (
    <div>
      <div>{children}</div>
      <aside>{aiSidebar}</aside>
    </div>
  ),
  AISidebar: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  ),
  AISuggestion: ({ title, description, actionLabel, onAction }: { title: string; description: string; actionLabel?: string; onAction?: () => void }) => (
    <div>
      <span>{title}</span>
      <span>{description}</span>
      {actionLabel ? <button onClick={onAction}>{actionLabel}</button> : null}
    </div>
  ),
  StatCard: ({ label, value }: { label: string; value: React.ReactNode }) => (
    <div>
      <span>{label}</span>
      <span>{String(value)}</span>
    </div>
  ),
}));

vi.mock('../../components/brain/SpotlightRing', () => ({
  SpotlightRing: () => <div>Spotlight Ring</div>,
}));

vi.mock('../../components/brain/AutonomyTimeline', () => ({
  AutonomyTimeline: () => <div>Autonomy Timeline</div>,
}));


describe('InsightsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
    analyticsMocks.useCollectionEntityCounts.mockReturnValue({ data: [], isLoading: false });
    analyticsMocks.useAnalyticsQuery.mockReturnValue({
      mutate: vi.fn(),
      data: undefined,
      isPending: false,
      error: null,
      reset: vi.fn(),
    });
    analyticsMocks.useAnalyticsAiSuggestions.mockReturnValue({
      data: [
        {
          key: 'opt-1',
          type: 'optimization',
          title: 'Cache repeated analytics query',
          description: 'query hot path',
          confidence: 0.87,
          reasons: ['query'],
          fallback: false,
        },
      ],
      isLoading: false,
    });
    analyticsMocks.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-runtime',
        tenantId: 'tenant-alpha',
        capabilities: [
          {
            key: 'analytics',
            label: 'Analytics',
            status: 'active',
            summary: 'ACTIVE',
            detail: undefined,
            rawValue: 'ACTIVE',
          },
          {
            key: 'voice',
            label: 'Voice',
            status: 'degraded',
            summary: 'DEGRADED',
            detail: 'Voice dependencies are optional in this launcher profile.',
            rawValue: 'DEGRADED',
          },
        ],
      },
    });
  });

  it('renders live overview metrics from canonical services', async () => {
    render(<InsightsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Records Processed')).toBeInTheDocument();

    await waitFor(() => {
      expect(within(screen.getByText('Records Processed').closest('div') as HTMLElement).getByText('1234')).toBeInTheDocument();
      expect(within(screen.getByText('Active Patterns').closest('div') as HTMLElement).getByText('7')).toBeInTheDocument();
      expect(within(screen.getByText('Active Pipelines').closest('div') as HTMLElement).getByText('9')).toBeInTheDocument();
      expect(within(screen.getByText('Est. Monthly Cost').closest('div') as HTMLElement).getByText('$2,450')).toBeInTheDocument();
    });
  });

  it('renders AI suggestions and deep-links optimization actions', async () => {
    render(<InsightsPage />, { wrapper: TestWrapper });

    const matchingSuggestions = await screen.findAllByText('Cache repeated analytics query');
    expect(matchingSuggestions.length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: 'View' }));

    expect(mockNavigate).toHaveBeenCalledWith('/query', {
      state: { query: 'query hot path' },
    });
  });

  it('renders the runtime capability truth panel', async () => {
    render(<InsightsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Runtime Capability Truth')).toBeInTheDocument();
    expect(screen.getByText('Analytics')).toBeInTheDocument();
    expect(screen.getByText('Voice dependencies are optional in this launcher profile.')).toBeInTheDocument();
  });

  it('shows an honest unavailable state when analytics capability is disabled', async () => {
    analyticsMocks.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-runtime',
        tenantId: 'tenant-alpha',
        capabilities: [
          {
            key: 'analytics',
            label: 'Analytics',
            status: 'unavailable',
            summary: 'NOT_CONFIGURED',
            detail: 'Analytics connectors are not configured for this launcher profile.',
            rawValue: 'NOT_CONFIGURED',
          },
        ],
      },
    });

    render(<InsightsPage />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole('button', { name: 'Dashboards' }));

    expect(await screen.findByText('Analytics unavailable')).toBeInTheDocument();
    expect(screen.getByText('Analytics connectors are not configured for this launcher profile.')).toBeInTheDocument();
  });
});
