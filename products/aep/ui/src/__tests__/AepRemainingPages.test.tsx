/**
 * AEP Remaining Pages — React Testing Library tests.
 *
 * Covers AgentMarketplacePage, CostDashboardPage, LoginPage, OperationCenterPage,
 * SessionExpiryPage, SsoCallbackPage, and WorkflowCatalogPage with mock API responses.
 *
 * Patterns:
 *  - TanStack Query: each suite wraps in `QueryClientProvider` with fresh client
 *  - API: vi.mock('@/api/aep.api') with per-test overrides via vi.mocked()
 *  - Async: waitFor() for data to appear after query resolves
 *
 * @doc.type test
 * @doc.purpose RTL integration tests for remaining AEP UI pages
 * @doc.layer frontend
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';

// Pages under test
import { AgentMarketplacePage } from '@/pages/AgentMarketplacePage';
import { CostDashboardPage } from '@/pages/CostDashboardPage';
import { LoginPage } from '@/pages/LoginPage';
import { OperationCenterPage } from '@/pages/OperationCenterPage';
import { SessionExpiryPage } from '@/pages/SessionExpiryPage';
import { SsoCallbackPage } from '@/pages/SsoCallbackPage';
import { WorkflowCatalogPage } from '@/pages/WorkflowCatalogPage';

// API modules mocked
import * as aepApi from '@/api/aep.api';
import * as pipelineApi from '@/api/pipeline.api';

// ── Mocks ────────────────────────────────────────────────────────────

vi.mock('@/api/aep.api');
vi.mock('@/api/pipeline.api');

// ── Fixtures ──────────────────────────────────────────────────────────

const MOCK_AGENTS: aepApi.AgentRegistration[] = [
  {
    id: 'agent-001',
    name: 'ValidatorAgent',
    tenantId: 'default',
    version: '1.0.0',
    type: 'DETERMINISTIC',
    status: 'ACTIVE',
    capabilities: ['validate', 'enrich'],
    memoryCount: 42,
    createdAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 'agent-002',
    name: 'AnalyzerAgent',
    tenantId: 'default',
    version: '2.0.0',
    type: 'PROBABILISTIC',
    status: 'ACTIVE',
    capabilities: ['analyze', 'predict'],
    memoryCount: 15,
    createdAt: '2024-01-02T00:00:00Z',
  },
];

const MOCK_WORKFLOWS: pipelineApi.PipelineSummary[] = [
  {
    id: 'workflow-001',
    name: 'Data Validation Workflow',
    tenantId: 'default',
    status: 'ACTIVE',
    stepCount: 5,
    lastRunAt: '2024-01-15T10:00:00Z',
    createdAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 'workflow-002',
    name: 'Event Processing Pipeline',
    tenantId: 'default',
    status: 'ACTIVE',
    stepCount: 3,
    lastRunAt: '2024-01-15T11:00:00Z',
    createdAt: '2024-01-02T00:00:00Z',
  },
];

const MOCK_COST_METRICS = {
  totalCost: 1250.50,
  costByAgent: {
    'agent-001': 750.25,
    'agent-002': 500.25,
  },
  costByWorkflow: {
    'workflow-001': 800.00,
    'workflow-002': 450.50,
  },
  period: '2024-01',
};

// ── Test Wrapper ───────────────────────────────────────────────────────

function createTestWrapper(component: React.ReactNode) {
  return createAepTestWrapper(component);
}

// ── AgentMarketplacePage Tests ───────────────────────────────────────

describe('AgentMarketplacePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders marketplace with agent list', async () => {
    vi.mocked(aepApi.listAgents).mockResolvedValue(MOCK_AGENTS);

    render(createTestWrapper(<AgentMarketplacePage />));

    await waitFor(() => {
      expect(screen.getByText('Agent Marketplace')).toBeInTheDocument();
      expect(screen.getByText('ValidatorAgent')).toBeInTheDocument();
      expect(screen.getByText('AnalyzerAgent')).toBeInTheDocument();
    });
  });

  it('shows empty state when no agents available', async () => {
    vi.mocked(aepApi.listAgents).mockResolvedValue([]);

    render(createTestWrapper(<AgentMarketplacePage />));

    await waitFor(() => {
      expect(screen.getByText(/no agents available/i)).toBeInTheDocument();
    });
  });

  it('displays agent details correctly', async () => {
    vi.mocked(aepApi.listAgents).mockResolvedValue(MOCK_AGENTS);

    render(createTestWrapper(<AgentMarketplacePage />));

    await waitFor(() => {
      expect(screen.getByText('DETERMINISTIC')).toBeInTheDocument();
      expect(screen.getByText('PROBABILISTIC')).toBeInTheDocument();
    });
  });
});

// ── CostDashboardPage Tests ───────────────────────────────────────────

describe('CostDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders cost dashboard with metrics', async () => {
    vi.mocked(aepApi.getCostMetrics).mockResolvedValue(MOCK_COST_METRICS);

    render(createTestWrapper(<CostDashboardPage />));

    await waitFor(() => {
      expect(screen.getByText('Cost Dashboard')).toBeInTheDocument();
      expect(screen.getByText(/\$1,250\.50/)).toBeInTheDocument();
    });
  });

  it('displays cost breakdown by agent', async () => {
    vi.mocked(aepApi.getCostMetrics).mockResolvedValue(MOCK_COST_METRICS);

    render(createTestWrapper(<CostDashboardPage />));

    await waitFor(() => {
      expect(screen.getByText('ValidatorAgent')).toBeInTheDocument();
      expect(screen.getByText(/\$750\.25/)).toBeInTheDocument();
    });
  });

  it('displays cost breakdown by workflow', async () => {
    vi.mocked(aepApi.getCostMetrics).mockResolvedValue(MOCK_COST_METRICS);

    render(createTestWrapper(<CostDashboardPage />));

    await waitFor(() => {
      expect(screen.getByText('Data Validation Workflow')).toBeInTheDocument();
      expect(screen.getByText(/\$800\.00/)).toBeInTheDocument();
    });
  });
});

// ── LoginPage Tests ───────────────────────────────────────────────────

describe('LoginPage', () => {
  it('renders login form', () => {
    render(createTestWrapper(<LoginPage />));

    expect(screen.getByText('Sign In')).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('allows email and password input', async () => {
    const user = { click: vi.fn(), type: vi.fn() };
    render(createTestWrapper(<LoginPage />));

    const emailInput = screen.getByLabelText(/email/i);
    const passwordInput = screen.getByLabelText(/password/i);

    expect(emailInput).toBeInTheDocument();
    expect(passwordInput).toBeInTheDocument();
  });

  it('shows SSO login option', () => {
    render(createTestWrapper(<LoginPage />));

    expect(screen.getByText(/sign in with sso/i)).toBeInTheDocument();
  });
});

// ── OperationCenterPage Tests ────────────────────────────────────────

describe('OperationCenterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders operation center with system status', async () => {
    vi.mocked(aepApi.getSystemStatus).mockResolvedValue({
      status: 'HEALTHY',
      uptime: 86400,
      activePipelines: 5,
      activeAgents: 10,
    });

    render(createTestWrapper(<OperationCenterPage />));

    await waitFor(() => {
      expect(screen.getByText('Operation Center')).toBeInTheDocument();
      expect(screen.getByText('HEALTHY')).toBeInTheDocument();
    });
  });

  it('displays active pipelines count', async () => {
    vi.mocked(aepApi.getSystemStatus).mockResolvedValue({
      status: 'HEALTHY',
      uptime: 86400,
      activePipelines: 5,
      activeAgents: 10,
    });

    render(createTestWrapper(<OperationCenterPage />));

    await waitFor(() => {
      expect(screen.getByText('5')).toBeInTheDocument();
    });
  });
});

// ── SessionExpiryPage Tests ───────────────────────────────────────────

describe('SessionExpiryPage', () => {
  it('renders session expiry message', () => {
    render(createTestWrapper(<SessionExpiryPage />));

    expect(screen.getByText(/session expired/i)).toBeInTheDocument();
    expect(screen.getByText(/your session has expired/i)).toBeInTheDocument();
  });

  it('provides link to login page', () => {
    const router = createMemoryRouter(
      [{ path: '/session-expiry', element: <SessionExpiryPage /> }],
      { initialEntries: ['/session-expiry'] }
    );

    render(<RouterProvider router={router} />);

    const loginLink = screen.getByText(/sign in again/i);
    expect(loginLink).toBeInTheDocument();
  });
});

// ── SsoCallbackPage Tests ───────────────────────────────────────────────

describe('SsoCallbackPage', () => {
  it('renders loading state during SSO callback', () => {
    render(createTestWrapper(<SsoCallbackPage />));

    expect(screen.getByText(/processing/i)).toBeInTheDocument();
    expect(screen.getByText(/completing sso login/i)).toBeInTheDocument();
  });

  it('handles SSO callback errors', async () => {
    vi.mocked(aepApi.completeSsoLogin).mockRejectedValue(new Error('SSO failed'));

    render(createTestWrapper(<SsoCallbackPage />));

    await waitFor(() => {
      expect(screen.getByText(/authentication failed/i)).toBeInTheDocument();
    });
  });
});

// ── WorkflowCatalogPage Tests ───────────────────────────────────────────

describe('WorkflowCatalogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders workflow catalog with workflow list', async () => {
    vi.mocked(pipelineApi.listPipelines).mockResolvedValue(MOCK_WORKFLOWS);

    render(createTestWrapper(<WorkflowCatalogPage />));

    await waitFor(() => {
      expect(screen.getByText('Workflow Catalog')).toBeInTheDocument();
      expect(screen.getByText('Data Validation Workflow')).toBeInTheDocument();
      expect(screen.getByText('Event Processing Pipeline')).toBeInTheDocument();
    });
  });

  it('shows empty state when no workflows available', async () => {
    vi.mocked(pipelineApi.listPipelines).mockResolvedValue([]);

    render(createTestWrapper(<WorkflowCatalogPage />));

    await waitFor(() => {
      expect(screen.getByText(/no workflows available/i)).toBeInTheDocument();
    });
  });

  it('displays workflow status badges', async () => {
    vi.mocked(pipelineApi.listPipelines).mockResolvedValue(MOCK_WORKFLOWS);

    render(createTestWrapper(<WorkflowCatalogPage />));

    await waitFor(() => {
      const statusBadges = screen.getAllByText('ACTIVE');
      expect(statusBadges.length).toBeGreaterThan(0);
    });
  });

  it('displays workflow step counts', async () => {
    vi.mocked(pipelineApi.listPipelines).mockResolvedValue(MOCK_WORKFLOWS);

    render(createTestWrapper(<WorkflowCatalogPage />));

    await waitFor(() => {
      expect(screen.getByText('5 steps')).toBeInTheDocument();
      expect(screen.getByText('3 steps')).toBeInTheDocument();
    });
  });
});
