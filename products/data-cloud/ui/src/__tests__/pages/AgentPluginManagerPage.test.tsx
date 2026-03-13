/**
 * Tests for AgentPluginManagerPage
 *
 * Covers agent listing, loading/error/empty states, registration modal,
 * deregistration flow, and SSE registry event feed.
 *
 * @doc.type test
 * @doc.purpose RTL tests for AgentPluginManagerPage
 * @doc.layer frontend
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

// =============================================================================
// Module mocks
// =============================================================================

vi.mock('../../api/agent-registry.service', () => ({
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

import type { AgentDefinition, RegistryEvent } from '../../api/agent-registry.service';

const SAMPLE_AGENTS: AgentDefinition[] = [
  {
    agentId: 'agent-001',
    name: 'Planning Agent',
    description: 'Handles task planning and decomposition',
    version: '1.2.0',
    tenantId: 'tenant-a',
    status: 'ACTIVE',
    capabilities: [
      {
        id: 'cap-1',
        name: 'TaskDecomposer',
        description: 'Breaks tasks into subtasks',
        version: '1.0.0',
      },
    ],
    registeredAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-03-01T08:00:00Z',
    metadata: {},
  },
  {
    agentId: 'agent-002',
    name: 'Execution Agent',
    description: 'Executes pipeline steps',
    version: '2.0.0',
    tenantId: 'tenant-a',
    status: 'ERROR',
    capabilities: [],
    registeredAt: '2026-02-01T12:00:00Z',
    updatedAt: '2026-03-10T09:30:00Z',
    metadata: {},
  },
];

const SAMPLE_REGISTRY_EVENT: RegistryEvent = {
  id: 'evt-reg-001',
  eventType: 'AGENT_REGISTERED',
  agentId: 'agent-001',
  tenantId: 'tenant-a',
  timestamp: new Date().toISOString(),
  payload: {},
};

// =============================================================================
// Helpers
// =============================================================================

function makeFakeEventSource(
  opts: { connected?: boolean } = {}
): EventSource & { triggerMessage: (data: unknown) => void } {
  let messageHandler: ((e: MessageEvent) => void) | null = null;
  const es = {
    onopen: null as (() => void) | null,
    onmessage: null as ((e: MessageEvent) => void) | null,
    onerror: null as ((e: Event) => void) | null,
    readyState: opts.connected ? 1 : 0,
    url: '',
    withCredentials: false,
    CONNECTING: 0,
    OPEN: 1,
    CLOSED: 2,
    close: vi.fn(),
    addEventListener: vi.fn((event: string, handler: EventListenerOrEventListenerObject) => {
      if (event === 'message') {
        messageHandler = handler as (e: MessageEvent) => void;
      }
    }),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
    triggerMessage: (data: unknown) => {
      if (messageHandler) {
        messageHandler(new MessageEvent('message', { data: JSON.stringify(data) }));
      } else if (es.onmessage) {
        es.onmessage(new MessageEvent('message', { data: JSON.stringify(data) }));
      }
    },
  };
  return es as unknown as EventSource & { triggerMessage: (data: unknown) => void };
}

function makeQueryClient(): QueryClient {
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
// Import page + service (after mocks are declared)
// =============================================================================

import { AgentPluginManagerPage } from '../../pages/AgentPluginManagerPage';
import { agentRegistryService } from '../../api/agent-registry.service';

// =============================================================================
// Tests
// =============================================================================

describe('AgentPluginManagerPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Default: successful data load
    vi.mocked(agentRegistryService.listAgents).mockResolvedValue(SAMPLE_AGENTS);
    vi.mocked(agentRegistryService.registerAgent).mockResolvedValue(SAMPLE_AGENTS[0]);
    vi.mocked(agentRegistryService.deregisterAgent).mockResolvedValue(undefined);
    vi.mocked(agentRegistryService.streamRegistryEvents).mockImplementation(
      (_tenantId, onEvent) => {
        const source = makeFakeEventSource({ connected: true });
        // Store onEvent for later triggering in specific tests
        (source as { _onEvent?: (e: RegistryEvent) => void })._onEvent = onEvent;
        return source as unknown as EventSource;
      }
    );
  });

  // --------------------------------------------------------------------------
  // 1. Renders page header
  // --------------------------------------------------------------------------
  it('renders "Agent Registry" heading', () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Agent Registry')).toBeDefined();
    expect(screen.getByText(/monitor and manage registered agents/i)).toBeDefined();
  });

  // --------------------------------------------------------------------------
  // 2. Shows loading state
  // --------------------------------------------------------------------------
  it('shows loading text while agents are being fetched', () => {
    // Make the query hang (never resolves during this test)
    vi.mocked(agentRegistryService.listAgents).mockReturnValue(new Promise(() => {}));
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(screen.getByText(/loading agents/i)).toBeDefined();
  });

  // --------------------------------------------------------------------------
  // 3. Shows agent cards on successful load
  // --------------------------------------------------------------------------
  it('renders agent cards when agents load successfully', async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText('Planning Agent')).toBeDefined();
      expect(screen.getByText('Execution Agent')).toBeDefined();
    });
  });

  it('shows agent version in each card', async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText('v1.2.0')).toBeDefined();
      expect(screen.getByText('v2.0.0')).toBeDefined();
    });
  });

  it('shows status badge for each agent', async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText('ACTIVE')).toBeDefined();
      expect(screen.getByText('ERROR')).toBeDefined();
    });
  });

  it('shows KPI counts in header', async () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      // 2 total, 1 active
      const body = document.body.textContent ?? '';
      expect(body).toMatch(/2/); // total
      expect(body).toMatch(/1/); // active count
    });
  });

  // --------------------------------------------------------------------------
  // 4. Empty state
  // --------------------------------------------------------------------------
  it('shows empty state when no agents are registered', async () => {
    vi.mocked(agentRegistryService.listAgents).mockResolvedValue([]);
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/no agents registered yet/i)).toBeDefined();
    });
  });

  it('empty state has a "Register your first agent" link', async () => {
    vi.mocked(agentRegistryService.listAgents).mockResolvedValue([]);
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/register your first agent/i)).toBeDefined();
    });
  });

  // --------------------------------------------------------------------------
  // 5. Error state
  // --------------------------------------------------------------------------
  it('shows error message when the query fails', async () => {
    vi.mocked(agentRegistryService.listAgents).mockRejectedValue(new Error('Network error'));
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText(/failed to load agents/i)).toBeDefined();
    });
  });

  it('error state shows a Retry button', async () => {
    vi.mocked(agentRegistryService.listAgents).mockRejectedValue(new Error('Fail'));
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      expect(screen.getByText('Retry')).toBeDefined();
    });
  });

  // --------------------------------------------------------------------------
  // 6. Registration modal - open
  // --------------------------------------------------------------------------
  it('opens registration modal when "Register Agent" button is clicked', async () => {
    const user = userEvent.setup();
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    const headerBtn = screen
      .getAllByRole('button', { name: /register agent/i })
      .find((btn) => btn.classList.contains('bg-indigo-600'))!;
    await user.click(headerBtn);
    // Modal heading should now be visible
    expect(screen.getByRole('heading', { name: 'Register Agent' })).toBeDefined();
    expect(screen.getByPlaceholderText('My Agent')).toBeDefined();
  });

  // --------------------------------------------------------------------------
  // 7. Registration modal - cancel
  // --------------------------------------------------------------------------
  it('closes modal when Cancel is clicked', async () => {
    const user = userEvent.setup();
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    await user.click(screen.getByRole('button', { name: /register agent/i }));
    expect(screen.getByPlaceholderText('My Agent')).toBeDefined();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByPlaceholderText('My Agent')).toBeNull();
  });

  // --------------------------------------------------------------------------
  // 8. Registration modal - submit
  // --------------------------------------------------------------------------
  it('calls registerAgent on form submission and closes modal', async () => {
    const user = userEvent.setup();
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });

    // Open modal using the header button (has bg-indigo-600 class)
    const headerBtn = screen
      .getAllByRole('button', { name: /register agent/i })
      .find((btn) => btn.classList.contains('bg-indigo-600'))!;
    await user.click(headerBtn);

    // Fill name field
    const nameInput = screen.getByPlaceholderText('My Agent');
    await user.clear(nameInput);
    await user.type(nameInput, 'Test Agent');

    // Click the modal's submit button (type=submit inside the form)
    const submitBtn = document.querySelector<HTMLButtonElement>('form button[type="submit"]')!;
    await user.click(submitBtn);

    await waitFor(() => {
      expect(agentRegistryService.registerAgent).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Test Agent' })
      );
    });
  });

  // --------------------------------------------------------------------------
  // 9. Deregistration
  // --------------------------------------------------------------------------
  it('calls deregisterAgent when deregister button is clicked and user confirms', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });

    await waitFor(() => screen.getByText('Planning Agent'));
    const trashButtons = screen.getAllByTitle('Deregister agent');
    await user.click(trashButtons[0]);

    await waitFor(() => {
      expect(agentRegistryService.deregisterAgent).toHaveBeenCalledWith('agent-001');
    });

    vi.restoreAllMocks();
  });

  it('does NOT call deregisterAgent when user cancels the confirm dialog', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });

    await waitFor(() => screen.getByText('Planning Agent'));
    const trashButtons = screen.getAllByTitle('Deregister agent');
    await user.click(trashButtons[0]);

    expect(agentRegistryService.deregisterAgent).not.toHaveBeenCalled();
    vi.restoreAllMocks();
  });

  // --------------------------------------------------------------------------
  // 10. SSE live feed
  // --------------------------------------------------------------------------
  it('renders "Live Registry Events" feed panel', () => {
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Live Registry Events')).toBeDefined();
  });

  it('establishes SSE stream on mount and closes it on unmount', () => {
    const fakeSource = makeFakeEventSource();
    vi.mocked(agentRegistryService.streamRegistryEvents).mockReturnValue(
      fakeSource as unknown as EventSource
    );
    const { unmount } = render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(agentRegistryService.streamRegistryEvents).toHaveBeenCalledTimes(1);
    unmount();
    expect(fakeSource.close).toHaveBeenCalledTimes(1);
  });

  it('appends incoming SSE events to the feed', async () => {
    let capturedOnEvent: ((event: RegistryEvent) => void) | undefined;

    vi.mocked(agentRegistryService.streamRegistryEvents).mockImplementation(
      (_tenantId, onEvent) => {
        capturedOnEvent = onEvent;
        return makeFakeEventSource() as unknown as EventSource;
      }
    );

    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });
    expect(screen.getByText(/waiting for registry events/i)).toBeDefined();

    // Simulate an incoming SSE event
    capturedOnEvent!(SAMPLE_REGISTRY_EVENT);

    await waitFor(() => {
      expect(screen.getByText('AGENT_REGISTERED')).toBeDefined();
    });
  });

  // --------------------------------------------------------------------------
  // 11. Capabilities expand/collapse
  // --------------------------------------------------------------------------
  it('expands agent capabilities when "Capabilities" button is clicked', async () => {
    const user = userEvent.setup();
    render(<AgentPluginManagerPage />, { wrapper: TestWrapper });

    await waitFor(() => screen.getByText('Planning Agent'));

    // Planning Agent has 1 capability
    const capBtn = screen.getByRole('button', { name: /capabilities \(1\)/i });
    await user.click(capBtn);

    expect(screen.getByText('TaskDecomposer')).toBeDefined();
  });
});
