import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ProductUnitIntentApplicationResult } from '@ghatana/kernel-product-contracts';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import BlueprintsPage from '../BlueprintsPage';

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
    providerMode: 'platform',
    registryProviderId: 'kernel-product-registry',
    sourceProviderId: 'yappc-creator',
    lifecycleEventRefs: [],
    provenanceRefs: [],
    runtimeTruthRefs: [],
    blockedReasons: [],
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
    selectedProviderMode: 'platform',
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

describe('BlueprintsPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('exports blueprint ProductUnitIntent via preview and apply actions', async () => {
    const context = createContextValue();
    useStudioLifecycleDataMock.mockReturnValue(context);

    render(<BlueprintsPage />);

    fireEvent.click(screen.getByText('studio.route.blueprints.exportPreview'));
    await waitFor(() => {
      expect(context.previewProductUnitIntent).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByText('studio.route.blueprints.exportApply'));
    await waitFor(() => {
      expect(context.applyProductUnitIntent).toHaveBeenCalledTimes(1);
    });
  });
});
