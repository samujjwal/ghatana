import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import DevelopPage from '../DevelopPage';

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
      productUnit: {
        schemaVersion: '1.0.0',
        id: 'digital-marketing',
        name: 'Digital Marketing',
        kind: 'business-product',
        registryProviderRef: { providerId: 'registry' },
        sourceProviderRef: { providerId: 'source' },
        lifecycleStatus: 'enabled',
        metadata: { environments: ['local'], lifecycleExecutionAllowed: true },
        surfaces: [
          { id: 'web', type: 'web', implementationStatus: 'implemented' },
          { id: 'api', type: 'api', implementationStatus: 'experimental' },
        ],
      },
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

describe('DevelopPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders translated lifecycle and surface status labels', () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<DevelopPage />);

    expect(screen.getByText('studio.route.develop.lifecycleStatus.enabled')).toBeInTheDocument();
    expect(screen.getByText('studio.route.develop.surfaceStatus.implemented')).toBeInTheDocument();
    expect(screen.getByText('studio.route.develop.surfaceStatus.experimental')).toBeInTheDocument();
  });
});
