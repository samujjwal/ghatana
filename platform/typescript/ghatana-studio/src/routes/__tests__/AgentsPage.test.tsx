import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import AgentsPage from '../AgentsPage';

const useStudioLifecycleDataMock = vi.fn<() => StudioLifecycleDataContextValue>();
const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock('../../data/StudioLifecycleDataContext', () => ({
  useStudioLifecycleData: () => useStudioLifecycleDataMock(),
}));

vi.mock('../../i18n/studioTranslations', () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

vi.mock('../../config/studioRuntimeContext', () => ({
  resolveStudioRuntimeContext: () => ({
    status: 'configured',
    identity: {
      baseUrl: 'http://localhost:3000',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      authToken: 'token-1',
    },
  }),
}));

vi.mock('../../api/agentLifecycleClient', () => ({
  createAgentLifecycleClient: () => ({
    submitAction: vi.fn(),
  }),
}));

function createContextValue(overrides: Partial<StudioLifecycleDataContextValue> = {}): StudioLifecycleDataContextValue {
  return {
    snapshot: {
      status: 'ready',
      runtimeMode: 'configured',
      productUnit: {
        schemaVersion: '1.0.0',
        id: 'digital-marketing',
        name: 'Digital Marketing',
        kind: 'business-product',
        registryProviderRef: { providerId: 'registry' },
        sourceProviderRef: { providerId: 'source' },
        lifecycleStatus: 'enabled',
        metadata: { environments: ['local'], lifecycleExecutionAllowed: true },
        surfaces: [{ id: 'web', type: 'web', implementationStatus: 'implemented' }],
      },
      availableProductUnits: [],
      lifecycleRuns: [],
      selectedRun: {
        runId: 'run-1',
        correlationId: 'corr-1',
        productUnitId: 'digital-marketing',
        phase: 'build',
        status: 'healthy',
        eventsRef: 'evidence:lifecycle-events',
        healthSnapshotRef: 'evidence:health-snapshot',
      },
      pendingApprovals: [],
      manifestLoadState: {
        gateResultManifest: { status: 'missing' },
        artifactManifest: { status: 'missing' },
        deploymentManifest: { status: 'missing' },
        verifyHealthReport: { status: 'missing' },
      },
    },
    selectedProductUnitId: 'digital-marketing',
    selectedRunId: 'run-1',
    selectedEnvironment: 'local',
    selectedProviderMode: 'bootstrap',
    intentOperation: { status: 'idle' },
    authenticatedUserId: 'user-123',
    selectProductUnit: vi.fn(),
    selectRun: vi.fn(),
    setEnvironment: vi.fn(),
    setProviderMode: vi.fn(),
    createPlan: vi.fn(),
    executePhase: vi.fn(),
    requestApproval: vi.fn(),
    submitApprovalDecision: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe('AgentsPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders translated proposal badges instead of raw enum codes', () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<AgentsPage />);

    expect(screen.getByText('studio.route.agents.riskLevel.medium')).toBeInTheDocument();
    expect(screen.getByText('studio.route.agents.decision.allowed')).toBeInTheDocument();
    expect(screen.getByText('studio.route.agents.decision.requires-approval')).toBeInTheDocument();
    expect(screen.getByText('studio.route.agents.decision.pending')).toBeInTheDocument();
    expect(screen.getByText('studio.route.agents.healthStatus.unknown')).toBeInTheDocument();
    expect(screen.getByText('studio.route.agents.rollbackReadiness.ready')).toBeInTheDocument();
  });
});
