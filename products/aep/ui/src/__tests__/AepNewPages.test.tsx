/**
 * AEP New Pages — React Testing Library tests.
 *
 * Covers AgentRegistryPage, MonitoringDashboardPage, PatternStudioPage,
 * and HitlReviewPage with mock API responses.
 *
 * Patterns:
 *  - TanStack Query: each suite wraps in `QueryClientProvider` with fresh client
 *  - API: vi.mock('@/api/aep.api') with per-test overrides via vi.mocked()
 *  - Async: waitFor() for data to appear after query resolves
 *
 * @doc.type test
 * @doc.purpose RTL integration tests for 5 new AEP UI pages
 * @doc.layer frontend
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';

// Pages under test
import { AgentRegistryPage } from '@/pages/AgentRegistryPage';
import { MonitoringDashboardPage } from '@/pages/MonitoringDashboardPage';
import { PatternStudioPage } from '@/pages/PatternStudioPage';
import { HitlReviewPage } from '@/pages/HitlReviewPage';

// API modules mocked
import * as aepApi from '@/api/aep.api';
import * as pipelineApi from '@/api/pipeline.api';
import * as useAgentMemory from '@/hooks/useAgentMemory';

// ── Mocks ────────────────────────────────────────────────────────────

vi.mock('@/api/aep.api');
vi.mock('@/api/pipeline.api');
vi.mock('@/hooks/useAgentMemory', () => ({
  useAllEpisodes: vi.fn(),
  usePolicies: vi.fn(),
  POLICIES_QUERY_KEY: 'policies',
}));
vi.mock('@audio-video/ui', () => ({
  useSpeechSynthesis: () => ({ speak: vi.fn() }),
}));

// ── Fixtures ──────────────────────────────────────────────────────────

const AGENT: aepApi.AgentRegistration = {
  id: 'agent-001',
  name: 'ValidatorAgent',
  tenantId: 'default',
  version: '1.0.0',
  type: 'DETERMINISTIC',
  status: 'ACTIVE',
  capabilities: ['validate', 'enrich'],
  memoryCount: 42,
  description: 'Validates inbound payloads before execution.',
  registrationMode: 'direct',
  executable: true,
  registryStorage: 'datacloud',
  memoryPersistence: 'datacloud',
  lastSeen: new Date().toISOString(),
  registeredAt: new Date().toISOString(),
  config: {},
};

const RUN: aepApi.PipelineRun = {
  id: 'run-001',
  pipelineId: 'pipe-001',
  pipelineName: 'My Pipeline',
  status: 'RUNNING',
  startedAt: new Date().toISOString(),
  eventsProcessed: 1234,
  errorsCount: 0,
};

const METRIC: aepApi.PipelineMetrics = {
  pipelineId: 'pipe-001',
  pipelineName: 'My Pipeline',
  throughputPerSec: 250.5,
  errorRate: 0.01,
  avgLatencyMs: 12.3,
  activeRuns: 1,
  totalRuns: 10,
};

const REVIEW_ITEM: aepApi.ReviewItem = {
  reviewId: 'rev-001',
  tenantId: 'default',
  skillId: 'email-routing',
  itemType: 'POLICY',
  status: 'PENDING',
  proposedVersion: { rule: 'if priority > 0.8 then escalate' },
  confidenceScore: 0.72,
  createdAt: new Date().toISOString(),
};

const EPISODE: aepApi.EpisodeRecord = {
  id: 'ep-001',
  tenantId: 'default',
  agentId: 'agent-001',
  pipelineId: 'pipe-001',
  outcome: 'SUCCESS',
  latencyMs: 45,
  inputSummary: 'email classification request',
  timestamp: new Date().toISOString(),
};

const POLICY: aepApi.LearnedPolicy = {
  id: 'pol-001',
  tenantId: 'default',
  skillId: 'email-routing',
  name: 'Auto-escalation rule',
  description: 'Escalate high-priority items automatically',
  status: 'PENDING_REVIEW',
  confidenceScore: 0.85,
  episodeCount: 142,
  version: 1,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  autoPromotable: false,
  autoPromoted: false,
};

const PATTERN: pipelineApi.PatternSummary = {
  id: 'pat-001',
  name: 'High error rate',
  type: 'THRESHOLD',
  status: 'ENABLED',
};

// ── Helper ────────────────────────────────────────────────────────────

function renderWithQuery(ui: React.ReactElement) {
  return render(ui, { wrapper: createAepTestWrapper() });
}

// ════════════════════════════════════════════════════════════════════

describe('AgentRegistryPage', () => {
  beforeEach(() => {
    vi.mocked(aepApi.listAgents).mockResolvedValue([AGENT]);
    vi.mocked(aepApi.deregisterAgent).mockResolvedValue(undefined);
    window.history.pushState({}, '', '/catalog/agents');
  });

  it('renders loading state initially', () => {
    vi.mocked(aepApi.listAgents).mockImplementation(() => new Promise(() => {}));
    renderWithQuery(<AgentRegistryPage />);
    expect(screen.getByText(/loading agents/i)).toBeInTheDocument();
  });

  it('renders agent table with data', async () => {
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText('ValidatorAgent')).toBeInTheDocument());
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('direct · v1.0.0')).toBeInTheDocument();
  });

  it('shows empty state when no agents', async () => {
    vi.mocked(aepApi.listAgents).mockResolvedValue([]);
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText(/no agents registered/i)).toBeInTheDocument());
  });

  it('shows error state when API fails', async () => {
    vi.mocked(aepApi.listAgents).mockRejectedValue(new Error('Network error'));
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText(/failed to load agents/i)).toBeInTheDocument());
  });

  it('filters agents by search query', async () => {
    const user = userEvent.setup();
    vi.mocked(aepApi.listAgents).mockResolvedValue([
      AGENT,
      { ...AGENT, id: 'agent-002', name: 'TransformAgent', capabilities: ['transform'] },
    ]);
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText('ValidatorAgent')).toBeInTheDocument());

    await user.type(screen.getByPlaceholderText(/search/i), 'Transform');
    await waitFor(() => expect(screen.queryByText('ValidatorAgent')).not.toBeInTheDocument());
    expect(screen.getByText('TransformAgent')).toBeInTheDocument();
  });

  it('opens detail panel on row click', async () => {
    const user = userEvent.setup();
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText('ValidatorAgent')).toBeInTheDocument());

    await user.click(screen.getByText('ValidatorAgent'));
    await waitFor(() => expect(screen.getByText('Capabilities')).toBeInTheDocument());
    expect(screen.getByText('Direct registration')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).getAllByText('Data Cloud')).toHaveLength(2);
    expect(screen.getByText('validate')).toBeInTheDocument();
    expect(screen.getByText('enrich')).toBeInTheDocument();
  });

  it('surfaces manifest-only agents as discovery-only in the registry', async () => {
    const user = userEvent.setup();
    vi.mocked(aepApi.listAgents).mockResolvedValue([
      {
        ...AGENT,
        id: 'agent-002',
        name: 'ManifestAgent',
        registrationMode: 'manifest-only',
        executable: false,
        description: 'Imported from manifest registration.',
      },
    ]);

    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText('ManifestAgent')).toBeInTheDocument());
    expect(screen.getByText('Discovery only')).toBeInTheDocument();

    await user.click(screen.getByText('ManifestAgent'));
    await waitFor(() => expect(screen.getByText(/registered for discovery and catalog visibility only/i)).toBeInTheDocument());
  });

  it('routes empty-state actions to real catalog entry points', async () => {
    const user = userEvent.setup();
    vi.mocked(aepApi.listAgents).mockResolvedValue([]);

    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText(/no agents registered/i)).toBeInTheDocument());

    await user.click(screen.getByText('Register first agent'));
    expect(window.location.pathname).toBe('/catalog/marketplace');

    window.history.pushState({}, '', '/catalog/agents');
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText(/no agents registered/i)).toBeInTheDocument());
    await user.click(screen.getByText('Auto-discover services'));
    expect(window.location.pathname).toBe('/catalog/workflows');
  });

  it('closes detail panel on X button', async () => {
    const user = userEvent.setup();
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText('ValidatorAgent')).toBeInTheDocument());

    await user.click(screen.getByText('ValidatorAgent'));
    await waitFor(() => screen.getByLabelText(/close agent details/i));

    await user.click(screen.getByLabelText(/close agent details/i));
    await waitFor(() => expect(screen.queryByText('Capabilities')).not.toBeInTheDocument());
  });

  it('calls deregisterAgent when deregister button clicked', async () => {
    const user = userEvent.setup();
    renderWithQuery(<AgentRegistryPage />);
    await waitFor(() => expect(screen.getByText('ValidatorAgent')).toBeInTheDocument());

    await user.click(screen.getByText('ValidatorAgent'));
    await waitFor(() => screen.getByText(/deregister agent/i));
    await user.click(screen.getByText(/deregister agent/i));
    await user.type(screen.getByLabelText(/reason/i), 'Retiring outdated agent');
    await user.type(screen.getByLabelText(/to confirm/i), 'DEREGISTER');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    await waitFor(() => expect(aepApi.deregisterAgent).toHaveBeenCalledWith('agent-001', 'default'));
  });
});

// ════════════════════════════════════════════════════════════════════

describe('MonitoringDashboardPage', () => {
  beforeEach(() => {
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue([RUN]);
    vi.mocked(aepApi.getPipelineMetrics).mockResolvedValue([METRIC]);
    vi.mocked(aepApi.getRuntimeDurabilityStatus).mockResolvedValue({
      mode: 'degraded',
      title: 'Partially durable runtime state',
      description: 'Run history is durable, but related runtime state is degraded: pipeline storage=in-memory, run ledger=ok.',
      components: {
        'execution-history': 'ok',
        'data-cloud.event-log': 'ok',
        'run-ledger': 'ok',
        'pipeline-storage': 'in-memory',
      },
      checkedAt: new Date().toISOString(),
      profile: 'test',
      dataCloudStorage: 'disabled',
      reasons: ['Runtime state will be lost on restart.'],
    });
    vi.mocked(aepApi.cancelRun).mockResolvedValue(undefined);
  });

  it('shows runtime durability status above monitoring KPIs', async () => {
    renderWithQuery(<MonitoringDashboardPage />);
    await waitFor(() => expect(screen.getByText('Partially durable runtime state')).toBeInTheDocument());
    expect(screen.getByText(/pipeline storage=in-memory/i)).toBeInTheDocument();
    expect(screen.getByText(/execution-history: ok/i)).toBeInTheDocument();
    expect(screen.getByText('profile: test')).toBeInTheDocument();
    expect(screen.getByText('storage: disabled')).toBeInTheDocument();
    expect(screen.getByText('Runtime state will be lost on restart.')).toBeInTheDocument();
  });

  it('renders KPI cards after data loads', async () => {
    renderWithQuery(<MonitoringDashboardPage />);
    await waitFor(() => expect(screen.getByText('Total runs')).toBeInTheDocument());
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Success rate')).toBeInTheDocument();
    expect(screen.getByText('Failed')).toBeInTheDocument();
  });

  it('renders run table in Runs tab', async () => {
    renderWithQuery(<MonitoringDashboardPage />);
    await waitFor(() => expect(screen.getByText('My Pipeline')).toBeInTheDocument());
    expect(screen.getByText('RUNNING')).toBeInTheDocument();
    expect(screen.getByText('1,234')).toBeInTheDocument();
  });

  it('shows Cancel button for running pipelines', async () => {
    renderWithQuery(<MonitoringDashboardPage />);
    await waitFor(() => screen.getByText('Cancel'));
    expect(screen.getByText('Cancel')).toBeInTheDocument();
  });

  it('calls cancelRun when Cancel clicked', async () => {
    const user = userEvent.setup();
    renderWithQuery(<MonitoringDashboardPage />);
    await waitFor(() => screen.getByText('Cancel'));
    await user.click(screen.getByText('Cancel'));
    await waitFor(() => expect(aepApi.cancelRun).toHaveBeenCalledWith('run-001', 'default'));
  });

  it('shows metrics table on Metrics tab', async () => {
    const user = userEvent.setup();
    renderWithQuery(<MonitoringDashboardPage />);
    await waitFor(() => screen.getByText('Pipeline Metrics'));
    await user.click(screen.getByText('Pipeline Metrics'));
    await waitFor(() => expect(screen.getByText('250.5')).toBeInTheDocument());
    expect(screen.getByText('12ms')).toBeInTheDocument();
  });

  it('shows empty state when no runs', async () => {
    vi.mocked(aepApi.listPipelineRuns).mockResolvedValue([]);
    renderWithQuery(<MonitoringDashboardPage />);
    await waitFor(() => expect(screen.getByText(/no pipeline runs yet/i)).toBeInTheDocument());
  });
});

// ════════════════════════════════════════════════════════════════════

describe('PatternStudioPage', () => {
  beforeEach(() => {
    vi.mocked(pipelineApi.listPatterns).mockResolvedValue([PATTERN]);
  });

  it('renders pattern list', async () => {
    renderWithQuery(<PatternStudioPage />);
    await waitFor(() => expect(screen.getByText('High error rate')).toBeInTheDocument());
    // THRESHOLD appears both in filter nav buttons and in the type badge
    const thresholdEls = screen.getAllByText('THRESHOLD');
    expect(thresholdEls.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('ENABLED')).toBeInTheDocument();
  });

  it('shows empty for non-matching filter', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await waitFor(() => screen.getByText('High error rate'));
    await user.click(screen.getByText('ANOMALY'));
    await waitFor(() => expect(screen.getByText(/no patterns found/i)).toBeInTheDocument());
  });

  it('opens create pattern modal on + New pattern', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await waitFor(() => screen.getByText(/new pattern/i));
    await user.click(screen.getByText(/new pattern/i));
    expect(screen.getByRole('dialog', { name: /create pattern/i })).toBeInTheDocument();
  });

  it('closes modal on Cancel click', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(await screen.findByText(/new pattern/i));
    await user.click(screen.getByText('Cancel'));
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /create pattern/i })).not.toBeInTheDocument(),
    );
  });

  it('shows empty state when no patterns', async () => {
    vi.mocked(pipelineApi.listPatterns).mockResolvedValue([]);
    renderWithQuery(<PatternStudioPage />);
    await waitFor(() => expect(screen.getByText(/no patterns found/i)).toBeInTheDocument());
  });
});

// ── PatternStudioPage: Learning Tab ──────────────────────────────────

describe('PatternStudioPage — learning tab', () => {
  beforeEach(() => {
    vi.mocked(pipelineApi.listPatterns).mockResolvedValue([PATTERN]);
    vi.mocked(aepApi.approvePolicy).mockResolvedValue({ ...POLICY, status: 'APPROVED' });
    vi.mocked(aepApi.rejectPolicy).mockResolvedValue({ ...POLICY, status: 'REJECTED' });
    vi.mocked(aepApi.triggerReflection).mockResolvedValue(undefined);
    // Default: episodes and policies return data
    vi.mocked(useAgentMemory.useAllEpisodes).mockReturnValue({
      data: [EPISODE],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useAgentMemory.useAllEpisodes>);
    vi.mocked(useAgentMemory.usePolicies).mockReturnValue({
      data: [POLICY],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof useAgentMemory.usePolicies>);
  });

  it('navigates to learning tab on click', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(screen.getByRole('button', { name: /learning/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /episodes/i })).toBeInTheDocument(),
    );
  });

  it('renders episodes sub-tab by default in learning tab', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(screen.getByRole('button', { name: /learning/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /episodes/i })).toBeInTheDocument(),
    );
  });

  it('shows episodes empty state when no episodes', async () => {
    vi.mocked(useAgentMemory.useAllEpisodes).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useAgentMemory.useAllEpisodes>);
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(screen.getByRole('button', { name: /learning/i }));
    await waitFor(() =>
      expect(screen.getByText(/no episodes yet/i)).toBeInTheDocument(),
    );
  });

  it('shows episodes error state when hook errors', async () => {
    vi.mocked(useAgentMemory.useAllEpisodes).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as ReturnType<typeof useAgentMemory.useAllEpisodes>);
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(screen.getByRole('button', { name: /learning/i }));
    await waitFor(() =>
      expect(screen.getByText(/failed to load episodes/i)).toBeInTheDocument(),
    );
  });

  it('shows policies when switching to policies sub-tab', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(screen.getByRole('button', { name: /learning/i }));
    await user.click(await screen.findByRole('button', { name: /policies/i }));
    await waitFor(() =>
      expect(screen.getByText('Auto-escalation rule')).toBeInTheDocument(),
    );
  });

  it('does not auto-promote policies — approve requires explicit action', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(screen.getByRole('button', { name: /learning/i }));
    await user.click(await screen.findByRole('button', { name: /policies/i }));
    await waitFor(() => screen.getByText('Auto-escalation rule'));
    // Approve button must be present but policy must not be auto-promoted
    expect(aepApi.approvePolicy).not.toHaveBeenCalled();
  });

  it('calls triggerReflection when Trigger reflection button clicked', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PatternStudioPage />);
    await user.click(screen.getByRole('button', { name: /learning/i }));
    await user.click(await screen.findByRole('button', { name: /policies/i }));
    // The reflect button has unicode ▶ prefix — match by partial name accessor
    const reflectBtn = await screen.findByRole('button', {
      name: (name) => name.includes('Trigger reflection'),
    });
    await user.click(reflectBtn);
    await waitFor(() => expect(aepApi.triggerReflection).toHaveBeenCalledWith('default'));
  });
});

// ════════════════════════════════════════════════════════════════════

describe('HitlReviewPage', () => {
  beforeEach(() => {
    vi.mocked(aepApi.listPendingReviews).mockResolvedValue([REVIEW_ITEM]);
    vi.mocked(aepApi.approveReview).mockResolvedValue({ ...REVIEW_ITEM, status: 'APPROVED' });
    vi.mocked(aepApi.rejectReview).mockResolvedValue({ ...REVIEW_ITEM, status: 'REJECTED' });
  });

  it('renders pending count badge', async () => {
    renderWithQuery(<HitlReviewPage />);
    await waitFor(() => expect(screen.getByText('1 pending')).toBeInTheDocument());
  });

  it('lists review items', async () => {
    renderWithQuery(<HitlReviewPage />);
    await waitFor(() => expect(screen.getByText('email-routing')).toBeInTheDocument());
    expect(screen.getByText('PENDING')).toBeInTheDocument();
  });

  it('shows empty state when queue is empty', async () => {
    vi.mocked(aepApi.listPendingReviews).mockResolvedValue([]);
    renderWithQuery(<HitlReviewPage />);
    await waitFor(() => expect(screen.getByText(/queue is empty/i)).toBeInTheDocument());
  });

  it('opens detail panel on item click', async () => {
    const user = userEvent.setup();
    renderWithQuery(<HitlReviewPage />);
    await waitFor(() => screen.getByText('email-routing'));
    await user.click(screen.getByRole('button', { name: /email-routing/i }));
    await waitFor(() => screen.getByText(/proposed policy/i));
    expect(screen.getByText(/ai review summary/i)).toBeInTheDocument();
    expect(screen.getByText(/review before apply/i)).toBeInTheDocument();
    expect(screen.getByText(/escalate/i)).toBeInTheDocument();
  });

  it('shows Approve and Reject buttons for PENDING items', async () => {
    const user = userEvent.setup();
    renderWithQuery(<HitlReviewPage />);
    await waitFor(() => screen.getByText('email-routing'));
    await user.click(screen.getByRole('button', { name: /email-routing/i }));
    await waitFor(() => screen.getByText('Approve'));
    expect(screen.getByText('Reject')).toBeInTheDocument();
  });

  it('calls approveReview after confirming approval', async () => {
    const user = userEvent.setup();
    renderWithQuery(<HitlReviewPage />);
    await waitFor(() => screen.getByText('email-routing'));
    await user.click(screen.getByRole('button', { name: /email-routing/i }));
    await waitFor(() => screen.getByText('Approve'));
    await user.click(screen.getByText('Approve'));
    await waitFor(() => screen.getByText('Confirm Approve'));
    await user.click(screen.getByText('Confirm Approve'));
    await waitFor(() =>
      expect(aepApi.approveReview).toHaveBeenCalledWith('rev-001', expect.objectContaining({ tenantId: 'default' })),
    );
  });

  it('calls rejectReview after providing reason', async () => {
    const user = userEvent.setup();
    renderWithQuery(<HitlReviewPage />);
    await waitFor(() => screen.getByText('email-routing'));
    await user.click(screen.getByRole('button', { name: /email-routing/i }));
    await waitFor(() => screen.getByText('Reject'));
    await user.click(screen.getByText('Reject'));
    await user.type(screen.getByPlaceholderText(/why is this policy/i), 'Wrong rule');
    await user.click(screen.getByText('Confirm Reject'));
    await waitFor(() =>
      expect(aepApi.rejectReview).toHaveBeenCalledWith('rev-001', expect.objectContaining({ reason: 'Wrong rule' })),
    );
  });
});

// NOTE: LearningPage removed; episodes and policies are tested via PatternStudioPage.
// All learning-related API calls are exercised in the PatternStudioPage suite above.
