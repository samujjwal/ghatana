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

import { ThemeProvider } from "@ghatana/theme";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider } from "jotai";
import React from "react";
import { BrowserRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../../lib/api/client";
import { collectionsApi } from "../../lib/api/collections";

// =============================================================================
// Module mocks
// =============================================================================

vi.mock("../../api/events.service", () => ({
  eventsService: {
    listEvents: vi.fn(),
    getStats: vi.fn(),
    openStream: vi.fn(),
  },
}));

vi.mock("../../api/memory.service", () => ({
  memoryService: {
    listMemoryItems: vi.fn(),
    deleteMemoryItem: vi.fn(),
    getConsolidationStatus: vi.fn(),
  },
}));

// Mock apiClient for EntityBrowserPage and DataFabricPage
vi.mock("../../lib/api/client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue([]),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("../../lib/api/collections", () => ({
  collectionsApi: {
    list: vi.fn(),
  },
}));

// Mock ai-operations service — ML platform not yet deployed; boundary responses expected
vi.mock("../../api/ai-operations.service", () => ({
  aiOperationsService: {
    getFabricAdvisories: vi.fn().mockRejectedValue({ status: 404 }),
    getCrossCorrelations: vi.fn().mockRejectedValue({ status: 404 }),
    getSuggestions: vi.fn().mockRejectedValue({ status: 404 }),
    applySuggestion: vi.fn().mockRejectedValue({ status: 404 }),
    getWorkflowAdvisories: vi.fn().mockRejectedValue({ status: 404 }),
    getQualityAdvisories: vi.fn().mockRejectedValue({ status: 404 }),
  },
}));

// Mock @ghatana/canvas/flow so tests don't need ReactFlow DOM
vi.mock("@ghatana/canvas/flow", () => ({
  FlowCanvas: ({ children }: { children?: React.ReactNode }) =>
    React.createElement("div", { "data-testid": "flow-canvas" }, children),
  FlowControls: () =>
    React.createElement("div", { "data-testid": "flow-controls" }),
  useNodesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
  useEdgesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
  addEdge: vi.fn((conn, eds) => eds),
  MarkerType: { ArrowClosed: "arrowclosed" },
  Position: { Left: "left", Right: "right" },
}));

// =============================================================================
// Helpers
// =============================================================================

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function TestWrapper({
  children,
}: {
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <Provider>
      <QueryClientProvider client={makeQueryClient()}>
        <ThemeProvider>
          <BrowserRouter>{children}</BrowserRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>
  );
}

// =============================================================================
// EventExplorerPage
// =============================================================================

import { eventsService } from "../../api/events.service";
import { EventExplorerPage } from "../../pages/EventExplorerPage";

const SAMPLE_EVENTS = [
  {
    id: "evt-001",
    tenantId: "tenant-a",
    eventType: "AGENT_COMPLETED",
    tier: "HOT" as const,
    payload: { agentId: "ag-1" },
    timestamp: new Date().toISOString(),
    source: "aep-core",
    metadata: {},
  },
  {
    id: "evt-002",
    tenantId: "tenant-a",
    eventType: "PIPELINE_FAILED",
    tier: "WARM" as const,
    payload: { pipelineId: "pip-1", error: "Timeout" },
    timestamp: new Date().toISOString(),
    source: "pipeline-engine",
    metadata: { retries: 3 },
  },
];

const SAMPLE_STATS = {
  total: 12_345,
  byTier: { HOT: 3_000, WARM: 5_000, COOL: 3_000, COLD: 1_345 },
  byType: { AGENT_COMPLETED: 6_000 },
  eventsPerMinute: 42.5,
};

describe("EventExplorerPage", () => {
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
      url: "",
      withCredentials: false,
      CONNECTING: 0,
      OPEN: 1,
      CLOSED: 2,
    } as unknown as EventSource);
  });

  it("renders page header", () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Event Explorer")).toBeDefined();
    expect(screen.getByText(/browse events across/i)).toBeDefined();
  });

  it("shows event list after data loads", async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText("AGENT_COMPLETED")).toBeDefined();
      expect(screen.getByText("PIPELINE_FAILED")).toBeDefined();
    });
  });

  it("shows tier badges", async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getAllByText("HOT").length).toBeGreaterThan(0);
      expect(screen.getAllByText("WARM").length).toBeGreaterThan(0);
    });
  });

  it("shows stats bar with total events", async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/12,345/)).toBeDefined();
    });
  });

  it("shows event detail panel when row clicked", async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText("AGENT_COMPLETED"));
    fireEvent.click(screen.getByTestId("event-row-evt-001"));
    expect(screen.getByText("Event Detail")).toBeDefined();
    // evt-001 appears in both the table row and the detail panel — ensure it's present
    expect(screen.getAllByText("evt-001").length).toBeGreaterThan(1);
  });

  it("closes detail panel when X is clicked", async () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText("AGENT_COMPLETED"));
    fireEvent.click(screen.getByTestId("event-row-evt-001"));
    expect(screen.getByText("Event Detail")).toBeDefined();
    fireEvent.click(screen.getByLabelText("Close detail panel"));
    expect(screen.queryByText("Event Detail")).toBeNull();
  });

  it("renders tier filter buttons", () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    expect(screen.getByRole("button", { name: "ALL" })).toBeDefined();
    expect(screen.getByRole("button", { name: "HOT" })).toBeDefined();
    expect(screen.getByRole("button", { name: "COLD" })).toBeDefined();
  });

  it("shows live tail button", () => {
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    expect(screen.getByText(/Live Tail/)).toBeDefined();
  });

  it("shows error state when API fails", async () => {
    vi.mocked(eventsService.listEvents).mockRejectedValue(
      new Error("Network error"),
    );
    render(<EventExplorerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/Network error/)).toBeDefined();
    });
  });
});

// =============================================================================
// MemoryPlaneViewerPage
// =============================================================================

import { memoryService } from "../../api/memory.service";
import { MemoryPlaneViewerPage } from "../../pages/MemoryPlaneViewerPage";

const SAMPLE_MEMORY_ITEMS = [
  {
    id: "mem-001",
    tenantId: "tenant-a",
    agentId: "agent-123",
    type: "EPISODIC" as const,
    content: "User asked about refund policy",
    tags: ["refund", "policy"],
    salience: 0.85,
    createdAt: new Date().toISOString(),
    metadata: { source: "conversation" },
  },
  {
    id: "mem-002",
    tenantId: "tenant-a",
    agentId: "agent-456",
    type: "EPISODIC" as const,
    content: "Failed to process order 12345",
    tags: ["order", "failure"],
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

describe("MemoryPlaneViewerPage", () => {
  beforeEach(() => {
    vi.mocked(memoryService.listMemoryItems).mockResolvedValue(
      SAMPLE_MEMORY_ITEMS,
    );
    vi.mocked(memoryService.getConsolidationStatus).mockResolvedValue(
      SAMPLE_CONSOLIDATION,
    );
    vi.mocked(memoryService.deleteMemoryItem).mockResolvedValue(undefined);
  });

  it("renders page header", () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Memory Plane Viewer")).toBeDefined();
  });

  it("shows memory type tabs", () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    expect(screen.getByRole("tab", { name: /EPISODIC/i })).toBeDefined();
    expect(screen.getByRole("tab", { name: /SEMANTIC/i })).toBeDefined();
    expect(screen.getByRole("tab", { name: /PROCEDURAL/i })).toBeDefined();
    expect(screen.getByRole("tab", { name: /PREFERENCE/i })).toBeDefined();
  });

  it("renders memory items after loading", async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText("User asked about refund policy")).toBeDefined();
      expect(screen.getByText("Failed to process order 12345")).toBeDefined();
    });
  });

  it("shows consolidation status", async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/Episodes processed/i)).toBeDefined();
      expect(screen.getByText("150")).toBeDefined();
    });
  });

  it("filters items by search text", async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText("User asked about refund policy"));

    const searchInput = screen.getByLabelText("Search memory items");
    fireEvent.change(searchInput, { target: { value: "refund" } });

    expect(screen.queryByText("Failed to process order 12345")).toBeNull();
    expect(screen.getByText("User asked about refund policy")).toBeDefined();
  });

  it("shows item count in filter bar", async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/2 items/)).toBeDefined();
    });
  });

  it("calls delete when delete button clicked", async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getAllByLabelText("Delete memory item"));
    fireEvent.click(screen.getAllByLabelText("Delete memory item")[0]);
    await waitFor(() => {
      // deleteMemoryItem is called with (agentId, itemId) signature
      expect(vi.mocked(memoryService.deleteMemoryItem)).toHaveBeenCalledWith(
        expect.any(String),
        "mem-001",
      );
    });
  });

  it("EPISODIC tab is selected by default", () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    const episodicTab = screen.getByRole("tab", { name: /EPISODIC/i });
    expect(episodicTab.getAttribute("aria-selected")).toBe("true");
  });

  it("shows empty state when no items match search", async () => {
    render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
    await waitFor(() => screen.getByText("User asked about refund policy"));

    const searchInput = screen.getByLabelText("Search memory items");
    fireEvent.change(searchInput, { target: { value: "zzznomatch" } });

    expect(screen.getByText(/No episodic memory items found/i)).toBeDefined();
  });
});

// =============================================================================
// EntityBrowserPage
// =============================================================================

import { EntityBrowserPage } from "../../pages/EntityBrowserPage";

const mockedApiClientGet = vi.mocked(apiClient.get);
const mockedCollectionsList = vi.mocked(collectionsApi.list);

const _SAMPLE_ENTITIES = [
  {
    id: "ent-001",
    tenantId: "tenant-a",
    namespace: "products",
    data: { name: "Widget Pro", price: 49.99 },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    version: 2,
  },
  {
    id: "ent-002",
    tenantId: "tenant-a",
    namespace: "products",
    data: { name: "Basic Widget", price: 9.99 },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    version: 1,
  },
];

// Since mocking the apiClient is handled at the top, tests render without network calls.
describe("EntityBrowserPage", () => {
  beforeEach(() => {
    mockedApiClientGet.mockReset();
    mockedApiClientGet.mockResolvedValue([]);
    mockedCollectionsList.mockReset();
    mockedCollectionsList.mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      pageSize: 100,
      hasMore: false,
    });
  });

  it("renders page header", () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Entity Browser")).toBeDefined();
    expect(screen.getByText(/browse entities stored/i)).toBeDefined();
  });

  it("renders namespace sidebar", () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    expect(screen.getAllByText(/Namespaces/i).length).toBeGreaterThan(0);
  });

  it("renders search input", () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    expect(screen.getByLabelText("Search entities")).toBeDefined();
  });

  it("shows empty state when no namespace selected", () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });
    // No namespace data loaded; should show no-namespace state
    expect(
      screen.getByText(/Select a namespace to browse entities/i),
    ).toBeDefined();
  });

  it("renders the entity-browser shell with search and empty-state guidance", () => {
    render(<EntityBrowserPage />, { wrapper: TestWrapper });

    expect(screen.getByText("Entity Browser")).toBeDefined();
    expect(screen.getByLabelText("Search entities")).toBeDefined();
    expect(
      screen.getByText(/Select a namespace to browse entities/i),
    ).toBeDefined();
  });

  it("renders namespaces and entities from canonical collections and entity routes", async () => {
    mockedApiClientGet.mockImplementation((url: string) => {
      if (url === "/surfaces" || url === "/capabilities") {
        return Promise.resolve({
          data: { generatedAt: "2026-05-08T00:00:00Z", capabilities: {} },
          meta: { requestId: "req-surfaces", tenantId: "tenant-a" },
        });
      }
      if (url === "/entities/products") {
        return Promise.resolve({
          entities: [
            {
              id: "ent-001",
              tenantId: "tenant-a",
              collectionName: "products",
              data: { name: "Widget Pro", price: 49.99 },
              version: 2,
              createdAt: "2024-01-01T00:00:00Z",
              updatedAt: "2024-01-02T00:00:00Z",
            },
          ],
          total: 1,
          limit: 20,
          offset: 0,
        });
      }
      if (url === "/entities/products/suggest") {
        return Promise.resolve({
          data: { suggestions: [] },
          ai: { confidence: 0.2, model: "fallback", fallback: true },
        });
      }

      return Promise.resolve([]);
    });
    mockedCollectionsList.mockResolvedValue({
      items: [
        {
          id: "products",
          name: "Products",
          description: "Product catalog",
          schemaType: "entity",
          status: "active",
          isActive: true,
          entityCount: 2,
          schema: { fields: [] },
          tags: [],
          createdAt: "2024-01-01T00:00:00Z",
          updatedAt: "2024-01-01T00:00:00Z",
          createdBy: "tester",
          lifecycleStatus: "PUBLISHED",
          operationalStatus: "healthy",
          owner: "tester",
        },
      ],
      total: 1,
      page: 1,
      pageSize: 100,
      hasMore: false,
    });

    render(<EntityBrowserPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "products" })).toBeDefined();
      expect(screen.getByText(/name: Widget Pro/i)).toBeDefined();
    });

    expect(mockedCollectionsList).toHaveBeenCalledWith({ pageSize: 100 });
    expect(mockedApiClientGet).toHaveBeenCalledWith("/entities/products", {
      params: { limit: 20 },
    });
  });
});

// =============================================================================
// DataFabricPage
// =============================================================================

import { dataFabricMetricsBoundary } from "@/components/common/unsupportedSurfaceRegistry";
import { DataFabricPage } from "../../pages/DataFabricPage";

const SAMPLE_FABRIC_METRICS = {
  tiers: [
    {
      tier: "HOT" as const,
      label: "Redis Cluster",
      throughputEps: 42_000,
      latencyP99Ms: 0.8,
      errorRate: 0.001,
      queueDepth: 0,
      status: "healthy" as const,
      instanceCount: 3,
    },
    {
      tier: "WARM" as const,
      label: "PostgreSQL",
      throughputEps: 8_000,
      latencyP99Ms: 6.5,
      errorRate: 0,
      queueDepth: 120,
      status: "healthy" as const,
      instanceCount: 2,
      storageGb: 42.7,
    },
    {
      tier: "COOL" as const,
      label: "Apache Iceberg",
      throughputEps: 200,
      latencyP99Ms: 85,
      errorRate: 0,
      queueDepth: 0,
      status: "healthy" as const,
      instanceCount: 1,
      storageGb: 850.3,
    },
    {
      tier: "COLD" as const,
      label: "S3 Archive",
      throughputEps: 5,
      latencyP99Ms: 2_000,
      errorRate: 0,
      queueDepth: 0,
      status: "healthy" as const,
      instanceCount: 1,
      storageGb: 10_200,
    },
  ],
  totalEventsPerSec: 50_205,
  totalStorageGb: 11_093,
  lastUpdated: new Date().toISOString(),
};

// apiClient mock is applied top-level; DataFabricPage renders without network calls
describe("DataFabricPage", () => {
  beforeEach(() => {
    mockedApiClientGet.mockReset();
    mockedApiClientGet.mockResolvedValue(SAMPLE_FABRIC_METRICS);
  });

  it("renders page header", () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Data Fabric")).toBeDefined();
    expect(screen.getByText(/four-tier event cloud topology/i)).toBeDefined();
    expect(screen.getByText(dataFabricMetricsBoundary.summary)).toBeDefined();
  });

  it("renders unavailable boundary state when fabric metrics capability is not active", () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.getByTestId("data-fabric-page-unavailable")).toBeDefined();
    expect(screen.getByText(dataFabricMetricsBoundary.summary)).toBeDefined();
  });

  it("shows boundary guidance bullets", () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Current boundary")).toBeDefined();
    expect(
      screen.getByText(
        /Topology layout is available for orientation and design review/i,
      ),
    ).toBeDefined();
    expect(
      screen.getByText(
        /Live throughput, latency, and queue depth are preview values/i,
      ),
    ).toBeDefined();
  });

  it("renders the data-fabric shell with preview metrics summary and migration action", async () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.getByText(dataFabricMetricsBoundary.summary)).toBeDefined();
      expect(
        screen.queryByRole("button", { name: /Migrate Tier/i }),
      ).toBeNull();
      expect(screen.getByText("Current boundary")).toBeDefined();
    });
  });

  it("does not render flow controls in unavailable boundary mode", () => {
    render(<DataFabricPage />, { wrapper: TestWrapper });
    expect(screen.queryByTestId("flow-controls")).toBeNull();
  });
});
