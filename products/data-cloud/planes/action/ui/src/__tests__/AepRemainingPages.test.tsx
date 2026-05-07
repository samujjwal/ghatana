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
import { createMemoryRouter, RouterProvider } from 'react-router';
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
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    isAuthenticated: false,
    isBootstrappingSession: false,
    isVerifyingAuth: false,
    hasAnyRole: () => true,
    loginWithToken: vi.fn(),
    logout: vi.fn(),
    user: null,
  }),
}));

// ── Fixtures ──────────────────────────────────────────────────────────

const MOCK_MARKETPLACE_AGENTS: aepApi.MarketplaceAgentListing[] = [
  {
    id: 'agent-001',
    name: 'ValidatorAgent',
    description: 'Validates data streams',
    version: '1.0.0',
    domain: 'data-quality',
    level: 'production',
    capabilities: ['validate', 'enrich'],
    tags: ['validation'],
    source: 'community',
    owner: 'team-a',
    publishedAt: '2024-01-01T00:00:00Z',
    averageRating: 4.5,
    reviewCount: 10,
  },
  {
    id: 'agent-002',
    name: 'AnalyzerAgent',
    description: 'Analyzes patterns',
    version: '2.0.0',
    domain: 'analytics',
    level: 'production',
    capabilities: ['analyze', 'predict'],
    tags: ['ml'],
    source: 'community',
    owner: 'team-b',
    publishedAt: '2024-01-02T00:00:00Z',
    averageRating: 4.0,
    reviewCount: 5,
  },
];

const MOCK_WORKFLOW_TEMPLATES: aepApi.WorkflowTemplate[] = [
  {
    id: 'tpl-001',
    name: 'Data Validation Workflow',
    description: 'Validates incoming data streams',
    operatorCount: 5,
    version: '1.0.0',
    tags: ['validation', 'etl'],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  },
  {
    id: 'tpl-002',
    name: 'Event Processing Pipeline',
    description: 'Processes event streams',
    operatorCount: 3,
    version: '2.0.0',
    tags: ['events'],
    createdAt: '2024-01-02T00:00:00Z',
    updatedAt: '2024-01-15T11:00:00Z',
  },
];

const MOCK_COST_METRICS: aepApi.CostSummary = {
  tenantId: 'default',
  windowStart: '2024-01-01T00:00:00Z',
  windowEnd: '2024-01-31T23:59:59Z',
  totalCostUsd: 1250.50,
  projectedMonthlyCostUsd: 1500.00,
  averageCostPerRunUsd: 0.25,
  perPipeline: [],
  perAgent: [],
  perModel: [],
  budget: {
    daily: { budgetUsd: 100, observedUsd: 40, remainingUsd: 60, usagePercent: 40, status: 'healthy' },
    monthly: { budgetUsd: 2000, observedUsd: 1250, remainingUsd: 750, usagePercent: 62.5, status: 'healthy' },
  },
  alerts: [],
  dataSource: 'metrics',
  allocationModel: 'token-weight',
  estimated: false,
};

// ── Test Wrapper ───────────────────────────────────────────────────────

function renderWithAep(component: React.ReactElement) {
  return render(component, { wrapper: createAepTestWrapper() });
}

// ── AgentMarketplacePage Tests ───────────────────────────────────────

describe('AgentMarketplacePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(aepApi.listMarketplaceAgents).mockResolvedValue(MOCK_MARKETPLACE_AGENTS);
  });

  it('renders marketplace with agent list', async () => {
    renderWithAep(<AgentMarketplacePage />);

    await waitFor(() => {
      expect(screen.getByText('ValidatorAgent')).toBeInTheDocument();
      expect(screen.getByText('AnalyzerAgent')).toBeInTheDocument();
    });
  });

  it('shows empty state when no agents available', async () => {
    vi.mocked(aepApi.listMarketplaceAgents).mockResolvedValue([]);

    renderWithAep(<AgentMarketplacePage />);

    await waitFor(() => {
      expect(screen.getByText(/no marketplace agents match/i)).toBeInTheDocument();
    });
  });

  it('displays agent details correctly', async () => {
    renderWithAep(<AgentMarketplacePage />);

    await waitFor(() => {
      expect(screen.getByText('ValidatorAgent')).toBeInTheDocument();
      expect(screen.getByText('Validates data streams')).toBeInTheDocument();
    });
  });
});

// ── CostDashboardPage Tests ───────────────────────────────────────────

describe('CostDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(aepApi.getCostSummary).mockResolvedValue(MOCK_COST_METRICS);
  });

  it('renders cost dashboard heading', async () => {
    renderWithAep(<CostDashboardPage />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /cost/i })).toBeInTheDocument();
    });
  });

  it('displays total cost', async () => {
    renderWithAep(<CostDashboardPage />);

    await waitFor(() => {
      // Total cost 1250.50 appears in the main metric display
      const matches = screen.getAllByText(/1[,.]?250/);
      expect(matches.length).toBeGreaterThan(0);
    });
  });

  it('displays data source provenance', async () => {
    renderWithAep(<CostDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/Source: metrics/i)).toBeInTheDocument();
    });
  });
});

// ── LoginPage Tests ───────────────────────────────────────────────────

describe('LoginPage', () => {
  it('renders login heading', () => {
    renderWithAep(<LoginPage />);

    expect(screen.getByRole('heading', { name: /AEP Control Plane/i })).toBeInTheDocument();
  });

  it('renders sign-in with platform button', () => {
    renderWithAep(<LoginPage />);

    expect(screen.getByRole('button', { name: /sign in with platform/i })).toBeInTheDocument();
  });

  it('shows SSO description text', () => {
    renderWithAep(<LoginPage />);

    expect(screen.getAllByText(/platform identity/i).length).toBeGreaterThan(0);
  });
});

// ── OperationCenterPage Tests ────────────────────────────────────────

describe('OperationCenterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(aepApi.listOperations).mockResolvedValue([]);
  });

  it('renders operation center heading', async () => {
    renderWithAep(<OperationCenterPage />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Operation Center', level: 1 })).toBeInTheDocument();
    });
  });

  it('shows empty state when no operations', async () => {
    vi.mocked(aepApi.listOperations).mockResolvedValue([]);

    renderWithAep(<OperationCenterPage />);

    await waitFor(() => {
      expect(screen.getByText(/no operations/i)).toBeInTheDocument();
    });
  });
});

// ── SessionExpiryPage Tests ───────────────────────────────────────────

describe('SessionExpiryPage', () => {
  it('renders session expiry heading', () => {
    renderWithAep(<SessionExpiryPage />);

    expect(screen.getByText(/session expired/i)).toBeInTheDocument();
    expect(screen.getByText(/authentication session has ended/i)).toBeInTheDocument();
  });

  it('provides button to re-authenticate', () => {
    renderWithAep(<SessionExpiryPage />);

    expect(screen.getByRole('button', { name: /sign in again/i })).toBeInTheDocument();
  });
});

// ── SsoCallbackPage Tests ───────────────────────────────────────────────

describe('SsoCallbackPage', () => {
  it('renders loading state during SSO callback', () => {
    renderWithAep(<SsoCallbackPage />);

    // Either loading or auth error state is acceptable at render time
    const hasLoadingOrError =
      screen.queryByText(/Completing sign-in/) ||
      screen.queryByText(/Authentication Failed/);
    expect(hasLoadingOrError).toBeTruthy();
  });
});

// ── WorkflowCatalogPage Tests ───────────────────────────────────────────

describe('WorkflowCatalogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(aepApi.listWorkflowTemplates).mockResolvedValue(MOCK_WORKFLOW_TEMPLATES);
    vi.mocked(aepApi.instantiateTemplate).mockResolvedValue({ pipelineId: 'pipe-new' });
  });

  it('renders workflow catalog with template list', async () => {
    renderWithAep(<WorkflowCatalogPage />);

    await waitFor(() => {
      expect(screen.getByText('Workflow Templates')).toBeInTheDocument();
      expect(screen.getByText('Data Validation Workflow')).toBeInTheDocument();
      expect(screen.getByText('Event Processing Pipeline')).toBeInTheDocument();
    });
  });

  it('shows empty state when no templates available', async () => {
    vi.mocked(aepApi.listWorkflowTemplates).mockResolvedValue([]);

    renderWithAep(<WorkflowCatalogPage />);

    await waitFor(() => {
      expect(screen.getByText(/no workflow templates registered/i)).toBeInTheDocument();
    });
  });

  it('displays operator counts', async () => {
    renderWithAep(<WorkflowCatalogPage />);

    await waitFor(() => {
      expect(screen.getByText('5')).toBeInTheDocument();
    });
  });

  it('displays template tags', async () => {
    renderWithAep(<WorkflowCatalogPage />);

    await waitFor(() => {
      expect(screen.getByText('validation')).toBeInTheDocument();
      expect(screen.getByText('etl')).toBeInTheDocument();
    });
  });
});
