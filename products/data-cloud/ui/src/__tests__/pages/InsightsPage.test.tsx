import { describe, it, expect, beforeEach, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import {
  INSIGHTS_CAPABILITY_SNAPSHOT_NOTE,
  INSIGHTS_REGISTRY_REQUEST_NOTE,
} from '@/lib/runtime-boundaries';
import { InsightsPage } from '../../pages/InsightsPage';
import SessionBootstrap from '../../lib/auth/session';
import { TokenStorage } from '../../lib/auth/tokenStorage';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

const mockNavigate = vi.fn();

const analyticsMocks = vi.hoisted(() => ({
  useAnalyticsQuery: vi.fn(),
  useCollectionEntityCounts: vi.fn(),
  useAnalyticsAiSuggestions: vi.fn(),
  useCapabilityRegistry: vi.fn(),
  useAiQualitySummary: vi.fn(),
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
      timestamp: '2026-04-17T12:34:00Z',
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

vi.mock('../../api/ai-observability.service', () => ({
  useAiQualitySummary: analyticsMocks.useAiQualitySummary,
}));

vi.mock('../../lib/api/collections', () => ({
  collectionsApi: {
    list: vi.fn().mockResolvedValue({ items: [{ id: 'orders', name: 'orders' }] }),
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
  PageContent: ({ children, contextSidebar }: { children: React.ReactNode; contextSidebar?: React.ReactNode }) => (
    <div>
      <div>{children}</div>
      <aside>{contextSidebar}</aside>
    </div>
  ),
  ContextSidebar: ({ title, children }: { title: string; children: React.ReactNode }) => (
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  ),
  SuggestionCard: ({ title, description, actionLabel, onAction }: { title: string; description: string; actionLabel?: string; onAction?: () => void }) => (
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
    localStorage.clear();
    sessionStorage.clear();
    SessionBootstrap.setTenantId(TEST_TENANT_ID);
    TokenStorage.set('auth-token');
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
    analyticsMocks.useAiQualitySummary.mockReturnValue({
      data: {
        generatedAt: '2026-04-18T12:40:00Z',
        scope: 'launcher-process',
        summary: {
          requestCount: 6,
          fallbackCount: 2,
          fallbackRate: 0.333,
          llmConfigured: true,
        },
        types: [
          {
            type: 'analytics_suggest',
            label: 'Analytics suggestions',
            route: '/api/v1/analytics/suggest',
            requestCount: 3,
            fallbackCount: 1,
            fallbackRate: 0.333,
            meanConfidence: 0.84,
            provenanceMode: 'ai-envelope',
            reviewGuidance: 'Fallback-heavy analytics suggestions should trigger manual SQL review before execution.',
          },
          {
            type: 'pipeline_draft',
            label: 'Workflow draft generation',
            route: '/api/v1/pipelines/draft',
            requestCount: 2,
            fallbackCount: 1,
            fallbackRate: 0.5,
            meanConfidence: 0.64,
            provenanceMode: 'ai-envelope-and-draft-provenance',
            reviewGuidance: 'Review low-confidence drafts or any fallback-generated workflow before saving.',
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

    const capabilityPanel = await screen.findByText('Runtime Capability Truth');
    expect(capabilityPanel).toBeInTheDocument();
    expect(screen.getAllByText('Analytics').length).toBeGreaterThan(0);
    expect(screen.getByText('Voice dependencies are optional in this launcher profile.')).toBeInTheDocument();
  });

  it('renders operator diagnostics with tenant, auth, and capability registry state', async () => {
    render(<InsightsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Operator Diagnostics')).toBeInTheDocument();
    expect(screen.getByText('tenant-alpha')).toBeInTheDocument();
    expect(screen.getByText('Authenticated session present')).toBeInTheDocument();
    expect(screen.getByText('0 unavailable / 1 degraded')).toBeInTheDocument();
    expect(screen.getByText('req-runtime')).toBeInTheDocument();
    expect(screen.getByText(INSIGHTS_CAPABILITY_SNAPSHOT_NOTE)).toBeInTheDocument();
    expect(screen.getByText(INSIGHTS_REGISTRY_REQUEST_NOTE)).toBeInTheDocument();
  });

  it('renders AI truth telemetry for operator review', async () => {
    render(<InsightsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('AI Truth Snapshot')).toBeInTheDocument();
    expect(screen.getByText('2/6 fallbacks')).toBeInTheDocument();
    expect(within(screen.getByTestId('insights-ai-type-analytics_suggest')).getByText('3 requests')).toBeInTheDocument();
    expect(screen.getByText('Workflow draft generation')).toBeInTheDocument();
    expect(within(screen.getByTestId('insights-ai-type-pipeline_draft')).getByText('50% fallback')).toBeInTheDocument();
    expect(within(screen.getByTestId('insights-ai-type-pipeline_draft')).getByText('1 fallback responses')).toBeInTheDocument();
    expect(screen.getByText('/api/v1/pipelines/draft • ai-envelope-and-draft-provenance')).toBeInTheDocument();
    expect(screen.getByText('Review low-confidence drafts or any fallback-generated workflow before saving.')).toBeInTheDocument();
  });

  it('shows when the current insight snapshot was generated', async () => {
    render(<InsightsPage />, { wrapper: TestWrapper });

    expect(await screen.findAllByText(/Generated /)).not.toHaveLength(0);
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

    fireEvent.click(screen.getByRole('button', { name: 'Analytics' }));

    expect(await screen.findByText('Analytics unavailable')).toBeInTheDocument();
    expect(screen.getByText('Analytics connectors are not configured for this launcher profile.')).toBeInTheDocument();
  });

  it('shows a capability loading state before runtime truth is available', async () => {
    analyticsMocks.useCapabilityRegistry.mockReturnValue({
      data: undefined,
      isLoading: true,
    });

    render(<InsightsPage />, { wrapper: TestWrapper });

    expect(screen.getByText('Loading runtime capabilities')).toBeInTheDocument();
    expect(screen.getByText('Checking which optional insights dependencies are active before rendering AI suggestions.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Analytics' }));

    expect(screen.getByText('Confirming analytics and federated query dependencies before enabling live analytics views.')).toBeInTheDocument();
  });

  it('shows an honest empty state when no suggestions are returned', async () => {
    analyticsMocks.useAnalyticsAiSuggestions.mockReturnValue({
      data: [],
      isLoading: false,
    });

    render(<InsightsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('No suggestions right now')).toBeInTheDocument();
    expect(screen.getByText('No active AI insights')).toBeInTheDocument();
  });
});
