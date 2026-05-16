import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import ArtifactsPage from '../ArtifactsPage';

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
      artifactManifest: {
        artifacts: [
          {
            id: 'artifact-1',
            found: true,
            metadata: {
              type: 'route',
              packaging: 'tsx',
              sizeBytes: 2048,
            },
            fingerprint: { algorithm: 'sha256', hash: 'abc123' },
          },
          {
            id: 'artifact-2',
            found: false,
            metadata: {
              type: 'route',
              packaging: 'tsx',
              sizeBytes: 4096,
            },
            fingerprint: { algorithm: 'sha256', hash: 'def456' },
          },
        ],
      } as StudioLifecycleDataContextValue['snapshot']['artifactManifest'],
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

describe('ArtifactsPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders translated artifact status badges', () => {
    useStudioLifecycleDataMock.mockReturnValue(createContextValue());

    render(<ArtifactsPage />);

    expect(screen.getByText('studio.route.artifacts.status.found')).toBeInTheDocument();
    expect(screen.getByText('studio.route.artifacts.status.missing')).toBeInTheDocument();
  });
});
