import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

const { mockDataCloudApi, mockAnalytics, mockCapabilities } = vi.hoisted(() => ({
  mockDataCloudApi: {
    getCollections: vi.fn(),
  },
  mockAnalytics: {
    executeAnalyticsQuery: vi.fn(),
    executeFederatedQuery: vi.fn(),
  },
  mockCapabilities: {
    useCapabilityRegistry: vi.fn(),
  },
}));

vi.mock('../../lib/api/data-cloud-api', () => ({
  dataCloudApi: mockDataCloudApi,
}));

vi.mock('../../api/analytics.service', () => ({
  executeAnalyticsQuery: mockAnalytics.executeAnalyticsQuery,
  executeFederatedQuery: mockAnalytics.executeFederatedQuery,
}));

vi.mock('../../api/capabilities.service', () => ({
  useCapabilityRegistry: mockCapabilities.useCapabilityRegistry,
  getCapabilitySignal: (capabilities: Array<{ key: string }> | undefined, aliases: string[]) =>
    capabilities?.find((capability) => aliases.includes(capability.key)),
}));

import { SqlWorkspacePage } from '../../pages/SqlWorkspacePage';


describe('SqlWorkspacePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDataCloudApi.getCollections.mockResolvedValue({
      data: [
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
      status: 200,
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
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-query',
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
    expect(screen.getByRole('heading', { name: /SQL Workspace/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Run Query/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Direct/i })).toBeInTheDocument();
    expect(screen.getByText('Query Runtime Truth')).toBeInTheDocument();
  });

  it('loads canonical collection metadata into the schema sidebar', async () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });

    expect(await screen.findByText('orders')).toBeInTheDocument();

    await waitFor(() => {
      expect(mockDataCloudApi.getCollections).toHaveBeenCalledTimes(1);
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
    expect(screen.getByText('Optional Query Dependencies Are Not Fully Available')).toBeInTheDocument();
  });
});
