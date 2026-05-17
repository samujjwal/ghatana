import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import HomePage from '../HomePage';

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
      },
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

describe('HomePage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders the latest run summary through translated run status labels', () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<HomePage />);

    expect(screen.getByText(/studio\.route\.home\.latestRunPrefix/)).toBeInTheDocument();
    expect(screen.getByText(/studio\.route\.lifecycle\.runStatus\.healthy/)).toBeInTheDocument();
  });
});
