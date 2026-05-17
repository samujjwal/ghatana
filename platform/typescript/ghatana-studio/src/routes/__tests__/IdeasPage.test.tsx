import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ProductUnitIntentApplicationResult } from '@ghatana/kernel-product-contracts';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import IdeasPage from '../IdeasPage';

const useStudioLifecycleDataMock = vi.fn<() => StudioLifecycleDataContextValue>();
const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock('../../data/StudioLifecycleDataContext', () => ({
  useStudioLifecycleData: () => useStudioLifecycleDataMock(),
}));

vi.mock('../../i18n/studioTranslations', () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

function createIntentResult(status: ProductUnitIntentApplicationResult['status']): ProductUnitIntentApplicationResult {
  return {
    schemaVersion: '1.0.0',
    intentId: 'intent:yappc:commerce-studio:corr-1',
    status,
    productUnitId: 'commerce-studio',
    correlationId: 'corr-1',
    providerMode: 'bootstrap',
    registryProviderId: 'kernel-product-registry',
    sourceProviderId: 'yappc-creator',
    lifecycleEventRefs: [],
    provenanceRefs: [],
    runtimeTruthRefs: [],
    blockedReasons: status === 'blocked' ? ['provider-mode-not-available'] : [],
    errors: [],
  };
}

function createContextValue(overrides: Partial<StudioLifecycleDataContextValue> = {}): StudioLifecycleDataContextValue {
  return {
    snapshot: {
      status: 'ready',
      runtimeMode: 'configured',
      availableProductUnits: [],
      lifecycleRuns: [],
      pendingApprovals: [],
      manifestLoadState: {
        gateResultManifest: { status: 'missing' },
        artifactManifest: { status: 'missing' },
        deploymentManifest: { status: 'missing' },
        verifyHealthReport: { status: 'missing' },
      },
    },
    selectedProductUnitId: 'digital-marketing',
    selectedRunId: null,
    selectedEnvironment: 'local',
    selectedProviderMode: 'bootstrap',
    intentOperation: { status: 'idle' },
    authenticatedUserId: 'user-1',
    selectProductUnit: vi.fn(),
    selectRun: vi.fn(),
    setEnvironment: vi.fn(),
    setProviderMode: vi.fn(),
    createPlan: vi.fn(),
    executePhase: vi.fn(),
    requestApproval: vi.fn(),
    submitApprovalDecision: vi.fn(),
    previewProductUnitIntent: vi.fn().mockResolvedValue(createIntentResult('previewed')),
    applyProductUnitIntent: vi.fn().mockResolvedValue(createIntentResult('applied')),
    refresh: vi.fn(),
    ...overrides,
  };
}

describe('IdeasPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('invokes preview and apply ProductUnitIntent actions and renders handoff status', async () => {
    const context = createContextValue();
    useStudioLifecycleDataMock.mockReturnValue(context);

    render(<IdeasPage />);

    fireEvent.click(screen.getByText('studio.route.ideas.previewIntent'));
    await waitFor(() => {
      expect(context.previewProductUnitIntent).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByText('studio.route.ideas.applyIntent'));
    await waitFor(() => {
      expect(context.applyProductUnitIntent).toHaveBeenCalledTimes(1);
    });
  });

  it('renders centralized success result details including correlation and blocked reasons', () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        intentOperation: {
          status: 'success',
          mode: 'apply',
          correlationId: 'corr-1',
          result: createIntentResult('blocked'),
        },
      }),
    );

    render(<IdeasPage />);

    expect(screen.getByText('studio.route.ideas.handoffStatusLabel blocked')).toBeInTheDocument();
    expect(screen.getByText('studio.route.ideas.handoffCorrelationIdLabel corr-1')).toBeInTheDocument();
    expect(screen.getByText('studio.route.ideas.blockedReasonsLabel provider-mode-not-available')).toBeInTheDocument();
  });

  it('renders handoff unavailable when intent mutation operations are missing', () => {
    useStudioLifecycleDataMock.mockReturnValue(
      createContextValue({
        previewProductUnitIntent: undefined,
        applyProductUnitIntent: undefined,
      }),
    );

    render(<IdeasPage />);

    expect(screen.getByText('studio.route.ideas.handoffUnavailable')).toBeInTheDocument();
  });
});
