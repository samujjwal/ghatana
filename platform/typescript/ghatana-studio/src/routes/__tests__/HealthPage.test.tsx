import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import HealthPage from '../HealthPage';

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
      selectedRun: {
        runId: 'run-1',
        correlationId: 'corr-1',
        productUnitId: 'digital-marketing',
        phase: 'build',
        status: 'healthy',
        manifestRefs: {
          pluginHealth: 'degraded',
          toolchainHealth: 'unavailable',
        },
      },
      artifactManifest: {
        providerMode: 'bootstrap',
      } as StudioLifecycleDataContextValue['snapshot']['artifactManifest'],
      verifyHealthReport: {
        status: 'unhealthy',
      } as StudioLifecycleDataContextValue['snapshot']['verifyHealthReport'],
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

describe('HealthPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders translated health signal status labels', () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<HealthPage />);

    expect(screen.getByText('studio.route.health.signalStatus.healthy')).toBeInTheDocument();
    expect(screen.getByText('studio.route.health.providerMode.bootstrap')).toBeInTheDocument();
    expect(screen.getByText('studio.route.health.signalStatus.unhealthy')).toBeInTheDocument();
    expect(screen.getByText('studio.route.health.signalStatus.degraded')).toBeInTheDocument();
    expect(screen.getByText('studio.route.health.signalStatus.unavailable')).toBeInTheDocument();
  });
});
