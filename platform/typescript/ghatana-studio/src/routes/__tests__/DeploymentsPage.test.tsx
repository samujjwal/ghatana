import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import DeploymentsPage from '../DeploymentsPage';

const useStudioLifecycleDataMock = vi.fn<() => StudioLifecycleDataContextValue>();
const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock('../../data/StudioLifecycleDataContext', () => ({
  useStudioLifecycleData: () => useStudioLifecycleDataMock(),
}));

vi.mock('../../i18n/studioTranslations', () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

function createContextValue(overrides: Partial<StudioLifecycleDataContextValue> = {}): StudioLifecycleDataContextValue {
  return {
    snapshot: {
      status: 'ready',
      runtimeMode: 'configured',
      availableProductUnits: [],
      lifecycleRuns: [],
      pendingApprovals: [],
      deploymentManifest: {
        target: 'local-compose',
        environment: 'local',
        services: { api: {}, web: {} },
        verifierResult: { valid: false },
        rollbackPlan: { steps: ['stop web', 'stop api'] },
      } as unknown as StudioLifecycleDataContextValue['snapshot']['deploymentManifest'],
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

describe('DeploymentsPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders translated deployment verifier status labels', () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<DeploymentsPage />);

    expect(screen.getByText('studio.route.deployments.verifierStatus.requiresVerification')).toBeInTheDocument();
  });
});
