import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';
import { SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_TITLE } from '@/lib/runtime-boundaries';

const { mockCollectionsApi, mockAnalytics, mockCapabilities, mockUserActivity, mockAiOperations } = vi.hoisted(() => ({
  mockCollectionsApi: {
    list: vi.fn(),
  },
  mockAnalytics: {
    executeAnalyticsQuery: vi.fn(),
    executeFederatedQuery: vi.fn(),
    explainAnalyticsQuery: vi.fn(),
    fetchAnalyticsQuerySuggestions: vi.fn(),
    evaluateQueryPolicy: vi.fn(),
  },
  mockCapabilities: {
    useCapabilityRegistry: vi.fn(),
  },
  mockUserActivity: {
    getRecentActivity: vi.fn(),
  },
  mockAiOperations: {
    getQualityAdvisories: vi.fn(),
  },
}));

vi.mock('../../lib/api/collections', () => ({
  collectionsApi: mockCollectionsApi,
}));

vi.mock('../../api/analytics.service', () => ({
  executeAnalyticsQuery: mockAnalytics.executeAnalyticsQuery,
  executeFederatedQuery: mockAnalytics.executeFederatedQuery,
  explainAnalyticsQuery: mockAnalytics.explainAnalyticsQuery,
  fetchAnalyticsQuerySuggestions: mockAnalytics.fetchAnalyticsQuerySuggestions,
  evaluateQueryPolicy: mockAnalytics.evaluateQueryPolicy,
}));

vi.mock('../../api/ai-operations.service', () => ({
  aiOperationsService: mockAiOperations,
}));

vi.mock('../../api/capabilities.service', () => ({
  useCapabilityRegistry: mockCapabilities.useCapabilityRegistry,
  getCapabilitySignal: (capabilities: Array<{ key: string }> | undefined, aliases: string[]) =>
    capabilities?.find((capability) => aliases.includes(capability.key)),
}));

vi.mock('../../lib/api/user-activity', () => ({
  getRecentActivity: mockUserActivity.getRecentActivity,
}));

import {
  deriveQueryPlanGuardrails,
  inferAnalyticsScope,
  recommendAnalyticsExecution,
  SqlWorkspacePage,
} from '../../pages/SqlWorkspacePage';


describe('SqlWorkspacePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAnalytics.evaluateQueryPolicy.mockResolvedValue({
      verdict: 'allow' as const,
      confidence: 1.0,
      reasons: [],
      requiresApproval: false,
      source: 'heuristic-fallback' as const,
    });
    mockAiOperations.getQualityAdvisories.mockRejectedValue({ status: 404 });
    mockCollectionsApi.list.mockResolvedValue({
      items: [
        {
          id: 'col-orders',
          name: 'orders',
          description: 'Orders collection',
          schemaType: 'entity',
          status: 'active',
          entityCount: 42,
          schema: {},
          tags: ['commerce'],
          createdAt: '2026-04-01T00:00:00Z',
          updatedAt: '2026-04-14T00:00:00Z',
          createdBy: 'tester',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 50,
      hasMore: false,
    });
    mockAnalytics.executeAnalyticsQuery.mockResolvedValue({
      queryId: 'query-1',
      queryType: 'analytics',
      rowCount: 1,
      columnCount: 2,
      rows: [{ id: 'evt-1', total: 42 }],
      executionTimeMs: 27,
      optimized: true,
      timestamp: '2026-04-14T12:00:00Z',
    });
    mockAnalytics.executeFederatedQuery.mockResolvedValue({
      queryId: 'query-fed-1',
      queryType: 'federated',
      rowCount: 1,
      columnCount: 1,
      rows: [{ region: 'global' }],
      executionTimeMs: 43,
      optimized: true,
      timestamp: '2026-04-14T12:05:00Z',
    });
    mockAnalytics.fetchAnalyticsQuerySuggestions.mockResolvedValue({
      suggestions: [
        {
          name: 'Top event types this week',
          template: 'SELECT event_type, COUNT(*) AS total\nFROM events\nWHERE timestamp > NOW() - INTERVAL 7 DAY\nGROUP BY event_type\nORDER BY total DESC\nLIMIT 10;',
          explanation: 'Summarize event volume by type for the past seven days.',
        },
      ],
      fallback: false,
      confidence: 0.86,
    });
    mockAnalytics.explainAnalyticsQuery.mockResolvedValue({
      queryId: 'plan-1',
      queryType: 'AGGREGATE',
      dataSources: ['orders'],
      estimatedCost: 128,
      optimized: true,
      explain: true,
      timestamp: '2026-04-14T12:02:00Z',
    });
    mockUserActivity.getRecentActivity.mockResolvedValue({
      activities: [
        {
          id: 'act-1',
          action: 'query',
          target: 'Investigated orders anomalies',
          timestamp: '2026-04-14T12:00:00Z',
          type: 'query',
        },
      ],
      continueWorking: [
        {
          id: 'cw-1',
          name: 'orders',
          type: 'collection',
          lastAccessed: '5 minutes ago',
          path: '/data/orders',
        },
      ],
    });
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-query',
        tenantId: TEST_TENANT_ID,
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
            key: 'trino',
            label: 'Trino',
            status: 'unavailable',
            summary: 'NOT_CONFIGURED',
            detail: 'Trino is not configured for this environment.',
            rawValue: 'NOT_CONFIGURED',
          },
          {
            key: 'ai_assist',
            label: 'Ai Assist',
            status: 'degraded',
            summary: 'DEGRADED',
            detail: 'AI assist is deployed without a backing LLM service.',
            rawValue: 'DEGRADED',
          },
        ],
      },
    });
  });

  it('renders the SQL workspace shell with editor and execution controls', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });
    await screen.findByText('orders');
    expect(screen.getByTestId('sql-workspace-page')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /SQL Workspace/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Run Query/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Direct/i })).toBeInTheDocument();
    expect(screen.getByText('Query Runtime Truth')).toBeInTheDocument();
  });

  it('loads canonical collection metadata into the schema sidebar', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    expect(await screen.findByText('orders')).toBeInTheDocument();

    await waitFor(() => {
      expect(mockCollectionsApi.list).toHaveBeenCalledTimes(1);
    });
  });

  it('runs a direct analytics query and renders canonical results', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole('button', { name: /run query/i }));

    expect(await screen.findByText(/1 rows • 27ms/i)).toBeInTheDocument();
    expect(screen.getByText('evt-1')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();

    await waitFor(() => {
      expect(mockAnalytics.executeAnalyticsQuery).toHaveBeenCalledWith(
        'SELECT * FROM events\nWHERE timestamp > NOW() - INTERVAL 1 DAY\nLIMIT 100;'
      );
    });
  });

  it('routes query execution through the federated path when the toggle is enabled', async () => {
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-query-fed',
        tenantId: TEST_TENANT_ID,
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
            key: 'trino',
            label: 'Trino',
            status: 'active',
            summary: 'ACTIVE',
            detail: undefined,
            rawValue: 'ACTIVE',
          },
        ],
      },
    });

    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByRole('button', { name: /direct/i }));
    expect(screen.getByRole('button', { name: /federated/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /run query/i }));

    await waitFor(() => {
      expect(mockAnalytics.executeFederatedQuery).toHaveBeenCalledWith(
        'SELECT * FROM events\nWHERE timestamp > NOW() - INTERVAL 1 DAY\nLIMIT 100;'
      );
    });
    expect(screen.getByText(/1 rows • 43ms/i)).toBeInTheDocument();
    expect(screen.getByText('global')).toBeInTheDocument();
  });

  it('disables federated queries when the capability registry marks Trino unavailable', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    expect(await screen.findByText('orders')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Direct/i })).toBeDisabled();
    expect(screen.getByText(SQL_OPTIONAL_DEPENDENCIES_UNAVAILABLE_TITLE)).toBeInTheDocument();
  });

  it('uses live analytics suggestions for AI query assist instead of inserting a fake generated SQL comment', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    expect(await screen.findByText('orders')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /AI Assist/i }));
    fireEvent.change(
      screen.getByPlaceholderText(/Describe what you want to query/i),
      { target: { value: 'Show top event types this week' } },
    );

    fireEvent.click(screen.getByRole('button', { name: /Generate/i }));

    await waitFor(() => {
      expect(mockAnalytics.fetchAnalyticsQuerySuggestions).toHaveBeenCalledWith(
        expect.stringContaining('Show top event types this week'),
      );
    });

    expect(mockAnalytics.fetchAnalyticsQuerySuggestions.mock.calls[0][0]).toContain('Collection context: orders');

    expect(await screen.findByText('Top event types this week')).toBeInTheDocument();
    expect(screen.getByText(/confidence 86%/i)).toBeInTheDocument();
    expect(screen.getByText(/Inferred scope/i)).toBeInTheDocument();
    expect(screen.getByText(/collection orders/i)).toBeInTheDocument();
    expect(screen.queryByText(/AI SQL generation is unavailable in this deployment/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Apply to Editor/i }));

    expect(screen.getByDisplayValue(/SELECT event_type, COUNT\(\*\) AS total/i)).toBeInTheDocument();
  });

  it('explains the current query and renders plan guardrails', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    fireEvent.click(screen.getByTestId('sql-explain-query'));

    await waitFor(() => {
      expect(mockAnalytics.explainAnalyticsQuery).toHaveBeenCalledWith(
        'SELECT * FROM events\nWHERE timestamp > NOW() - INTERVAL 1 DAY\nLIMIT 100;'
      );
    });

    expect(await screen.findByTestId('sql-query-plan')).toBeInTheDocument();
    expect(screen.getByText('AGGREGATE')).toBeInTheDocument();
    expect(screen.getByTestId('sql-query-plan-guardrails')).toHaveTextContent(/Optimizer hints available/i);
  });

  it('surfaces a clarification prompt when the inferred scope is ambiguous', async () => {
    mockCollectionsApi.list.mockResolvedValueOnce({
      items: [
        {
          id: 'col-orders',
          name: 'orders',
          description: 'Orders collection',
          schemaType: 'entity',
          status: 'active',
          entityCount: 42,
          schema: {},
          tags: ['commerce'],
          createdAt: '2026-04-01T00:00:00Z',
          updatedAt: '2026-04-14T00:00:00Z',
          createdBy: 'tester',
        },
        {
          id: 'col-customers',
          name: 'customers',
          description: 'Customers collection',
          schemaType: 'entity',
          status: 'active',
          entityCount: 35,
          schema: {},
          tags: ['crm'],
          createdAt: '2026-04-01T00:00:00Z',
          updatedAt: '2026-04-14T00:00:00Z',
          createdBy: 'tester',
        },
      ],
      total: 2,
      page: 1,
      pageSize: 50,
      hasMore: false,
    });
    mockUserActivity.getRecentActivity.mockResolvedValueOnce({
      activities: [
        {
          id: 'act-1',
          action: 'query',
          target: 'Compared orders and customers',
          timestamp: '2026-04-14T12:00:00Z',
          type: 'query',
        },
      ],
      continueWorking: [
        {
          id: 'cw-1',
          name: 'orders',
          type: 'collection',
          lastAccessed: '5 minutes ago',
          path: '/data/orders',
        },
        {
          id: 'cw-2',
          name: 'customers',
          type: 'collection',
          lastAccessed: '7 minutes ago',
          path: '/data/customers',
        },
      ],
    });

    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    await screen.findByText('orders');
    fireEvent.click(screen.getByTestId('sql-ai-assist-toggle'));
    fireEvent.change(screen.getByTestId('sql-ai-assist-input'), {
      target: { value: 'Compare this week' },
    });
    fireEvent.click(screen.getByTestId('sql-ai-assist-generate'));

    expect(await screen.findByTestId('sql-clarification-prompt')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('sql-clarify-orders'));
    expect(screen.getByDisplayValue(/Compare this week for orders/i)).toBeInTheDocument();
  });

  it('exports low-confidence scope candidates from the helper', () => {
    const scope = inferAnalyticsScope(
      'Compare orders and customers this week',
      [
        { name: 'orders', tables: ['orders'] },
        { name: 'customers', tables: ['customers'] },
      ],
      [],
      [],
    );

    expect(scope.ambiguous).toBe(true);
    expect(scope.candidates).toEqual(['orders', 'customers']);
    expect(scope.confidence).toBe('low');
  });

  it('recommends review when a cross-source query cannot use federation', () => {
    expect(
      recommendAnalyticsExecution(
        'SELECT * FROM bronze.orders JOIN gold.customers ON bronze.orders.customer_id = gold.customers.id',
        false,
      ),
    ).toMatchObject({
      path: 'review',
      requiresReview: true,
    });
  });

  it('recommends the direct path for bounded queries and supports switching to the recommended engine', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    expect(await screen.findByText(/Recommended: direct analytics execution/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Use recommended path/i })).toBeInTheDocument();
  });

  it('derives plan guardrails for review-first and expensive scans', () => {
    const guardrails = deriveQueryPlanGuardrails(
      'SELECT * FROM bronze.orders JOIN gold.customers ON bronze.orders.customer_id = gold.customers.id',
      {
        queryId: 'plan-2',
        queryType: 'JOIN',
        dataSources: ['bronze', 'gold'],
        estimatedCost: 5400,
        optimized: false,
        explain: true,
        timestamp: '2026-04-14T12:02:00Z',
      },
      {
        path: 'review',
        title: 'Review required before execution',
        detail: 'Cross-source query needs review.',
        requiresReview: true,
      },
    );

    expect(guardrails.map((guardrail) => guardrail.title)).toEqual(expect.arrayContaining([
      'Review-first path required',
      'Multiple data sources in plan',
      'Elevated estimated cost',
      'Wide scan risk',
    ]));
  });
});
