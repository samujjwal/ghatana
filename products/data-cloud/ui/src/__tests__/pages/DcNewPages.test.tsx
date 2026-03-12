/**
 * Tests for new DC UI pages:
 * - EventExplorerPage
 * - MemoryPlaneViewerPage
 * - EntityBrowserPage
 * - DataFabricPage
 *
 * Test pattern: simple TestWrapper with Jotai Provider + BrowserRouter + QueryClient.
 * Mocks are applied at the module level via vi.mock().
 *
 * @doc.type test
 * @doc.purpose RTL tests for new Data-Cloud UI pages
 * @doc.layer frontend
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

// =============================================================================
// Module mocks
// =============================================================================

vi.mock('../../api/events.service', () => ({
  eventsService: {
    listEvents: vi.fn(),
    getStats: vi.fn(),
    openStream: vi.fn(),
  },
}));

vi.mock('../../api/memory.service', () => ({
  memoryService: {
    listMemoryItems: vi.fn(),
    deleteMemoryItem: vi.fn(),
    getConsolidationStatus: vi.fn(),
  },
}));

// Mock axios for EntityBrowserPage direct calls
vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: vi.fn(),
      delete: vi.fn(),
    }),
  },
}));

// Mock @ghatana/flow-canvas so tests don't need ReactFlow DOM
vi.mock('@ghatana/flow-canvas', () => ({
  FlowCanvas: ({ children }: { children?: React.ReactNode }) =>
    React.createElement('div', { 'data-testid': 'flow-canvas' }, children),
  FlowControls: () => React.createElement('div', { 'data-testid': 'flow-controls' }),
  useNodesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
  useEdgesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
  addEdge: vi.fn((conn, eds) => eds),
  MarkerType: { ArrowClosed: 'arrowclosed' },
  Position: { Left: 'left', Right: 'right' },
}));

// =============================================================================
// Helpers
// =============================================================================

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function TestWrapper({ children }: { children: React.ReactNode }): React.ReactElement {
  return (
    <Provider>
      <QueryClientProvider client={makeQueryClient()}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    </Provider>
  );
}

// =============================================================================
// EventExplorerPage
// =============================================================================

import { EventExplorerPage } from '../../pages/EventExplorerPage';
import { eventsService } from '../../api/events.service';

const SAMPLE_EVENTS = [
  {
    id: 'evt-001',
    tenantId: 'tenant-a',
    eventType: 'AGENT_COMPLETED',
    tier: 'HOT' as const,
    payload: { agentId: 'ag-1' },
    timestamp: new Date().toISOString(),
    source: 'aep-core',
    metadata: {},
  },
  {
    id: 'evt-002',
    tenantId: 'tenant-a',
    eventType: 'PIPELINE_FAILED',
    tier: 'WARM' as const,
    payload: { pipelineId: 'pip-1', error: 'Timeout' },
    timestamp: new Date().toISOString(),
    source: 'pipeline-engine',
    metadata: { retries: 3 },
  },
];

const SAMPLE_STATS = {
  total: 12_345,
  byTier: { HOT: 3_000, WARM: 5_000, COOL: 3_000, COLD: 1_345 },
  byType: { AGENT_COMPLETED: 6_000 },
  eventsPerMinute: 42.5,
};

describe('EventExplorerPage', () => {
  beforeEach(() => {
    vi.mocked(eventsService.listEvents).mockResolvedValue({
      events: SAMPLE_EVENTS,
      total: 2,
      hasMore: false,
    });
    vi.mocked(eventsService.getStats).mockResolvedValue(SAMPLE_STATS);
    vi.mocked(eventsService.openStream).mockReturnValue({
      close: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
      onmessage: null,
      onerror: null,
      onopen: null,
      readyState: 1,
      url: '',
      withCredentials: false,
      CONNECTING: 0,
      OPEN: 1,
      CLOSED: 2,
    } as unknown as EventSource);
  });

  it('renders page header', () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Event Explorer')).toBeDefined();
    expect(screen.getByText(/browse events across/i)).toBeDefined();
  });

  it('shows event list after data loads', async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText('AGENT_COMPLETED')).toBeDefined();
      expect(screen.getByText('PIPELINE_FAILED')).toBeDefined();
    });
  });

  it('shows tier badges', async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getAllByText('HOT').length).toBeGreaterThan(0);
      expect(screen.getAllByText('WARM').length).toBeGreaterThan(0);
    });
  });

  it('shows stats bar with total events', async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/12,345/)).toBeDefined();
    });
  });

  it('shows event detail panel when row clicked', async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText('AGENT_COMPLETED'));
    fireEvent.click(screen.getByTestId('event-row-evt-001'));
    expect(screen.getByText('Event Detail')).toBeDefined();
    // evt-001 appears in both the table row and the detail panel — ensure it's present
    expect(screen.getAllByText('evt-001').length).toBeGreaterThan(1);
  });

  it('closes detail panel when X is clicked', async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText('AGENT_COMPLETED'));
    fireEvent.click(screen.getByTestId('event-row-evt-001'));
    expect(screen.getByText('Event Detail')).toBeDefined();
    fireEvent.click(screen.getByLabelText('Close detail panel'));
    expect(screen.queryByText('Event Detail')).toBeNull();
  });

  it('renders tier filter buttons', () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    expect(screen.getByRole('button', { name: 'ALL' })).toBeDefined();
    expect(screen.getByRole('button', { name: 'HOT' })).toBeDefined();
    expect(screen.getByRole('button', { name: 'COLD' })).toBeDefined();
  });

  it('shows live tail button', () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    expect(screen.getByText(/Live Tail/)).toBeDefined();
  });

  it('shows error state when API fails', async () => {
    vi.mocked(eventsService.listEvents).mockRejectedValue(new Error('Network error'));
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/Network error/)).toBeDefined();
    });
  });
});

// =============================================================================
// MemoryPlaneViewerPage
// =============================================================================

import { MemoryPlaneViewerPage } from '../../pages/MemoryPlaneViewerPage';
import { memoryService } from '../../api/memory.service';

const SAMPLE_MEMORY_ITEMS = [
  {
    id: 'mem-001',
    tenantId: 'tenant-a',
    agentId: 'agent-123',
    type: 'EPISODIC' as const,
    content: 'User asked about refund policy',
    tags: ['refund', 'policy'],
    salience: 0.85,
    createdAt: new Date().toISOString(),
    metadata: { source: 'conversation' },
  },
  {
    id: 'mem-002',
    tenantId: 'tenant-a',
    agentId: 'agent-456',
    type: 'EPISODIC' as const,
    content: 'Failed to process order #12345',
    tags: ['order', 'failure'],
    salience: 0.62,
    createdAt: new Date().toISOString(),
    metadata: {},
  },
];

const SAMPLE_CONSOLIDATION = {
  lastRun: new Date().toISOString(),
  episodesProcessed: 150,
  policiesExtracted: 12,
};

describe('MemoryPlaneViewerPage', () => {
  beforeEach(() => {
    vi.mocked(memoryService.listMemoryItems).mockResolvedValue(SAMPLE_MEMORY_ITEMS);
    vi.mocked(memoryService.getConsolidationStatus).mockResolvedValue(SAMPLE_CONSOLIDATION);
    vi.mocked(memoryService.deleteMemoryItem).mockResolvedValue(undefined);
  });

  it('renders page header', () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Memory Plane Viewer')).toBeDefined();
  });

  it('shows memory type tabs', () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    expect(screen.getByRole('tab', { name: /EPISODIC/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /SEMANTIC/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /PROCEDURAL/i })).toBeDefined();
    expect(screen.getByRole('tab', { name: /PREFERENCE/i })).toBeDefined();
  });

  it('renders memory items after loading', async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText('User asked about refund policy')).toBeDefined();
      expect(screen.getByText('Failed to process order #12345')).toBeDefined();
    });
  });

  it('shows consolidation status', async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/Episodes processed/i)).toBeDefined();
      expect(screen.getByText('150')).toBeDefined();
    });
  });

  it('filters items by search text', async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText('User asked about refund policy'));

    const searchInput = screen.getByLabelText('Search memory items');
    fireEvent.change(searchInput, { target: { value: 'refund' } });

    expect(screen.queryByText('Failed to process order #12345')).toBeNull();
    expect(screen.getByText('User asked about refund policy')).toBeDefined();
  });

  it('shows item count in filter bar', async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/2 items/)).toBeDefined();
    });
  });

  it('calls delete when delete button clicked', async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getAllByLabelText('Delete memory item'));
    fireEvent.click(screen.getAllByLabelText('Delete memory item')[0]);
    await waitFor(() => {
      expect(vi.mocked(memoryService.deleteMemoryItem)).toHaveBeenCalledWith('mem-001');
    });
  });

  it('EPISODIC tab is selected by default', () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    const episodicTab = screen.getByRole('tab', { name: /EPISODIC/i });
    expect(episodicTab.getAttribute('aria-selected')).toBe('true');
  });

  it('shows empty state when no items match search', async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText('User asked about refund policy'));

    const searchInput = screen.getByLabelText('Search memory items');
    fireEvent.change(searchInput, { target: { value: 'zzznomatch' } });

    expect(screen.getByText(/No episodic memory items found/i)).toBeDefined();
  });
});

// =============================================================================
// EntityBrowserPage
// =============================================================================

import { EntityBrowserPage } from '../../pages/EntityBrowserPage';

// We need to mock axios module for EntityBrowserPage
// The axios mock is set up at the top, but we need to intercept specific calls.
// Since EntityBrowserPage creates its own axios instance, we mock at module level.

const SAMPLE_ENTITIES = [
  {
    id: 'ent-001',
    tenantId: 'tenant-a',
    namespace: 'products',
    data: { name: 'Widget Pro', price: 49.99 },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    version: 2,
  },
  {
    id: 'ent-002',
    tenantId: 'tenant-a',
    namespace: 'products',
    data: { name: 'Basic Widget', price: 9.99 },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    version: 1,
  },
];

// Since mocking the axios instance inside EntityBrowserPage is complex,
// we mock the imported axios so create() returns our controllable instance.
describe('EntityBrowserPage', () => {
  it('renders page header', () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Entity Browser')).toBeDefined();
    expect(screen.getByText(/browse entities stored/i)).toBeDefined();
  });

  it('renders namespace sidebar', () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    expect(screen.getAllByText(/Namespaces/i).length).toBeGreaterThan(0);
  });

  it('renders search input', () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    expect(screen.getByLabelText('Search entities')).toBeDefined();
  });

  it('shows empty state when no namespace selected', () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    // No namespace data loaded; should show no-namespace state
    expect(screen.getByText(/Select a namespace to browse entities/i)).toBeDefined();
  });

  it('does not crash on render', () => {
    const { container } = render(<EntityBrowserPage />, { wrapper: TestWrapper });
    expect(container).toBeTruthy();
  });
});

// =============================================================================
// DataFabricPage
// =============================================================================

import { DataFabricPage } from '../../pages/DataFabricPage';

const SAMPLE_FABRIC_METRICS = {
  tiers: [
    {
      tier: 'HOT' as const,
      label: 'Redis Cluster',
      throughputEps: 42_000,
      latencyP99Ms: 0.8,
      errorRate: 0.001,
      queueDepth: 0,
      status: 'healthy' as const,
      instanceCount: 3,
    },
    {
      tier: 'WARM' as const,
      label: 'PostgreSQL',
      throughputEps: 8_000,
      latencyP99Ms: 6.5,
      errorRate: 0,
      queueDepth: 120,
      status: 'healthy' as const,
      instanceCount: 2,
      storageGb: 42.7,
    },
    {
      tier: 'COOL' as const,
      label: 'Apache Iceberg',
      throughputEps: 200,
      latencyP99Ms: 85,
      errorRate: 0,
      queueDepth: 0,
      status: 'healthy' as const,
      instanceCount: 1,
      storageGb: 850.3,
    },
    {
      tier: 'COLD' as const,
      label: 'S3 Archive',
      throughputEps: 5,
      latencyP99Ms: 2_000,
      errorRate: 0,
      queueDepth: 0,
      status: 'healthy' as const,
      instanceCount: 1,
      storageGb: 10_200,
    },
  ],
  totalEventsPerSec: 50_205,
  totalStorageGb: 11_093,
  lastUpdated: new Date().toISOString(),
};

// Mock the DC API call inside DataFabricPage (axios.create instance)
// We intercept via the module-level axios mock
describe('DataFabricPage', () => {
  it('renders page header', () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Data Fabric')).toBeDefined();
    expect(screen.getByText(/four-tier event cloud topology/i)).toBeDefined();
  });

  it('renders the flow canvas placeholder', () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.getByTestId('flow-canvas')).toBeDefined();
  });

  it('shows tier legend', () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Tier Legend')).toBeDefined();
    expect(screen.getByText(/HOT.*Redis/i)).toBeDefined();
    expect(screen.getByText(/WARM.*PostgreSQL/i)).toBeDefined();
    expect(screen.getByText(/COOL.*Iceberg/i)).toBeDefined();
    expect(screen.getByText(/COLD.*S3/i)).toBeDefined();
  });

  it('does not crash when metrics load', async () => {
    const { container } = render(<DataFabricPage />, { wrapper: TestWrapper });
    await waitFor(() => expect(container).toBeTruthy());
  });

  it('shows flow controls', () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.getByTestId('flow-controls')).toBeDefined();
  });
});
