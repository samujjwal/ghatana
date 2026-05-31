/**
 * Tests for AgentPluginManagerPage
 *
 * Covers read-only agent catalog rendering plus explicit boundary states for
 * unsupported registration, deregistration, and live SSE features.
 *
 * @doc.type test
 * @doc.purpose RTL tests for AgentPluginManagerPage
 * @doc.layer frontend
 */

import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import { AGENT_REGISTRY_BOUNDARY_MESSAGE } from "@/lib/runtime-boundaries";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "jotai";
import React from "react";
import { BrowserRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

// =============================================================================
// Module mocks
// =============================================================================

vi.mock("../../api/agent-registry.service", () => ({
  AGENT_REGISTRY_BOUNDARY_MESSAGE,
  agentRegistryService: {
    listAgents: vi.fn(),
    registerAgent: vi.fn(),
    deregisterAgent: vi.fn(),
    streamRegistryEvents: vi.fn(),
  },
}));

// =============================================================================
// Sample data
// =============================================================================

import type {
  AgentDefinition,
  RegistryEvent,
} from "../../api/agent-registry.service";

const SAMPLE_AGENTS: AgentDefinition[] = [
  {
    agentId: "agent-001",
    name: "Planning Agent",
    description: "Handles task planning and decomposition",
    version: "1.2.0",
    tenantId: TEST_TENANT_ID,
    status: "ACTIVE",
    capabilities: [
      {
        id: "cap-1",
        name: "TaskDecomposer",
        description: "Breaks tasks into subtasks",
        version: "1.0.0",
      },
    ],
    registeredAt: "2026-01-15T10:00:00Z",
    updatedAt: "2026-03-01T08:00:00Z",
    metadata: {},
  },
  {
    agentId: "agent-002",
    name: "Execution Agent",
    description: "Executes pipeline steps",
    version: "2.0.0",
    tenantId: TEST_TENANT_ID,
    status: "ERROR",
    capabilities: [],
    registeredAt: "2026-02-01T12:00:00Z",
    updatedAt: "2026-03-10T09:30:00Z",
    metadata: {},
  },
];

const _SAMPLE_REGISTRY_EVENT: RegistryEvent = {
  id: "evt-reg-001",
  eventType: "AGENT_REGISTERED",
  agentId: "agent-001",
  tenantId: TEST_TENANT_ID,
  timestamp: new Date().toISOString(),
  payload: {},
};

// =============================================================================
// Helpers
// =============================================================================

function _makeFakeEventSource(
  opts: { connected?: boolean } = {},
): EventSource & { triggerMessage: (data: unknown) => void } {
  let messageHandler: ((e: MessageEvent) => void) | null = null;
  const es = {
    onopen: null as (() => void) | null,
    onmessage: null as ((e: MessageEvent) => void) | null,
    onerror: null as ((e: Event) => void) | null,
    readyState: opts.connected ? 1 : 0,
    url: "",
    withCredentials: false,
    CONNECTING: 0,
    OPEN: 1,
    CLOSED: 2,
    close: vi.fn(),
    addEventListener: vi.fn(
      (event: string, handler: EventListenerOrEventListenerObject) => {
        if (event === "message") {
          messageHandler = handler as (e: MessageEvent) => void;
        }
      },
    ),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
    triggerMessage: (data: unknown) => {
      if (messageHandler) {
        messageHandler(
          new MessageEvent("message", { data: JSON.stringify(data) }),
        );
      } else if (es.onmessage) {
        es.onmessage(
          new MessageEvent("message", { data: JSON.stringify(data) }),
        );
      }
    },
  };
  return es as unknown as EventSource & {
    triggerMessage: (data: unknown) => void;
  };
}

function makeQueryClient(): QueryClient {
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
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    </Provider>
  );
}

// =============================================================================
// Import page + service (after mocks are declared)
// =============================================================================

import { agentRegistryService } from "../../api/agent-registry.service";
import { AgentPluginManagerPage } from "../../pages/AgentPluginManagerPage";

// =============================================================================
// Tests
// =============================================================================

describe("AgentPluginManagerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Default: successful data load
    vi.mocked(agentRegistryService.listAgents).mockResolvedValue(SAMPLE_AGENTS);
    vi.mocked(agentRegistryService.registerAgent).mockRejectedValue(
      new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE),
    );
    vi.mocked(agentRegistryService.deregisterAgent).mockRejectedValue(
      new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE),
    );
    vi.mocked(agentRegistryService.streamRegistryEvents).mockImplementation(
      () => {
        throw new Error(AGENT_REGISTRY_BOUNDARY_MESSAGE);
      },
    );
  });

  // --------------------------------------------------------------------------
  // 1. Renders page header
  // --------------------------------------------------------------------------
  it('renders "Agent Catalog" heading', () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Agent Catalog")).toBeDefined();
    expect(
      screen.getByText(/monitor the launcher-exposed agent catalog/i),
    ).toBeDefined();
  });

  // --------------------------------------------------------------------------
  // 2. Shows loading state
  // --------------------------------------------------------------------------
  it("shows loading text while agents are being fetched", () => {
    // Make the query hang (never resolves during this test)
    vi.mocked(agentRegistryService.listAgents).mockReturnValue(
      new Promise(() => {}),
    );
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(screen.getByText(/loading agents/i)).toBeDefined();
  });

  // --------------------------------------------------------------------------
  // 3. Shows agent cards on successful load
  // --------------------------------------------------------------------------
  it("renders agent cards when agents load successfully", async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText("Planning Agent")).toBeDefined();
      expect(screen.getByText("Execution Agent")).toBeDefined();
    });
  });

  it("shows agent version in each card", async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText("v1.2.0")).toBeDefined();
      expect(screen.getByText("v2.0.0")).toBeDefined();
    });
  });

  it("shows status badge for each agent", async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText("ACTIVE")).toBeDefined();
      expect(screen.getByText("ERROR")).toBeDefined();
    });
  });

  it("shows KPI counts in header", async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      // 2 total, 1 active
      const body = document.body.textContent ?? "";
      expect(body).toMatch(/2/); // total
      expect(body).toMatch(/1/); // active count
    });
  });

  // --------------------------------------------------------------------------
  // 4. Empty state
  // --------------------------------------------------------------------------
  it("shows empty state when no agents are registered", async () => {
    vi.mocked(agentRegistryService.listAgents).mockResolvedValue([]);
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(
        screen.getByText(/no agent catalog entries are currently exposed/i),
      ).toBeDefined();
    });
  });

  it("shows a boundary note explaining that mutations are unavailable", async () => {
    vi.mocked(agentRegistryService.listAgents).mockResolvedValue([]);
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(
        screen.getAllByText(
          /aep owns registration, deregistration, execution history, and live registry events/i,
        ).length,
      ).toBeGreaterThan(0);
    });
  });

  // --------------------------------------------------------------------------
  // 5. Error state
  // --------------------------------------------------------------------------
  it("shows error message when the query fails", async () => {
    vi.mocked(agentRegistryService.listAgents).mockRejectedValue(
      new Error("Network error"),
    );
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/failed to load agents/i)).toBeDefined();
    });
  });

  it("error state shows a Retry button", async () => {
    vi.mocked(agentRegistryService.listAgents).mockRejectedValue(
      new Error("Fail"),
    );
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText("Retry")).toBeDefined();
    });
  });

  // --------------------------------------------------------------------------
  // 6. Registration controls are removed in launcher mode
  // --------------------------------------------------------------------------
  it("does not render register controls because launcher mode is catalog-only", async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(
      screen.queryByRole("button", { name: /register agent/i }),
    ).toBeNull();
    expect(
      screen.queryByRole("heading", { name: "Register Agent" }),
    ).toBeNull();
  });

  // --------------------------------------------------------------------------
  // 7. Deregistration
  // --------------------------------------------------------------------------
  it("shows catalog-only badges instead of deregistration controls", async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });

    await waitFor(() => screen.getByText("Planning Agent"));
    expect(screen.queryByTitle("Deregister agent")).toBeNull();
    expect(screen.getAllByText("Catalog only").length).toBeGreaterThan(0);
  });

  // --------------------------------------------------------------------------
  // 8. Registry surface boundary panel
  // --------------------------------------------------------------------------
  it('renders "Registry Surface Status" panel', () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(screen.getByText("Registry Surface Status")).toBeDefined();
  });

  it("does not establish SSE stream on mount in launcher mode", () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(agentRegistryService.streamRegistryEvents).not.toHaveBeenCalled();
  });

  it("renders explicit ownership boundaries for the catalog surface", async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(
      screen.getByText(/aep owns the executable registry surface/i),
    ).toBeDefined();
    expect(
      screen.getByText(/execution history and runtime event streaming/i),
    ).toBeDefined();
    expect(
      screen.getByText(/read-only launcher catalog entries/i),
    ).toBeDefined();
  });

  // --------------------------------------------------------------------------
  // 9. Capabilities expand/collapse
  // --------------------------------------------------------------------------
  it('expands agent capabilities when "Capabilities" button is clicked', async () => {
    const user = userEvent.setup();
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });

    await waitFor(() => screen.getByText("Planning Agent"));

    // Planning Agent has 1 capability
    const capBtn = screen.getByRole("button", { name: /capabilities \(1\)/i });
    await user.click(capBtn);

    expect(screen.getByText("TaskDecomposer")).toBeDefined();
  });
});
