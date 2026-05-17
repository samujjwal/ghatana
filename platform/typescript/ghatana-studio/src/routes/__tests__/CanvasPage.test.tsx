import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import CanvasPage from '../CanvasPage';

const useStudioTranslationMock = vi.fn<() => (key: string) => string>();
const useStudioLifecycleDataMock = vi.fn<() => StudioLifecycleDataContextValue>();

vi.mock('../../i18n/studioTranslations', () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

vi.mock('../../data/StudioLifecycleDataContext', () => ({
  useStudioLifecycleData: () => useStudioLifecycleDataMock(),
}));

vi.mock('../yappcWorkflowData', () => ({
  artifactGraphSummary: {
    confidence: 0.88,
    nodeCount: 4,
    edgeCount: 3,
    provenanceRefs: ['prov:graph'],
    evidenceId: 'evidence:graph',
  },
  residualIslandReport: {
    confidence: 0.82,
    islandCount: 1,
    residualArtifactRefs: ['legacy-widget'],
    provenanceRefs: ['prov:residual'],
    evidenceId: 'evidence:residual',
  },
  riskHotspotReport: {
    confidence: 0.79,
    highestRiskLevel: 'high',
    hotspots: [
      {
        artifactId: 'legacy-widget',
        riskLevel: 'high',
        reason: 'Residual island still needs review',
        evidenceRefs: ['evidence:residual'],
      },
    ],
    provenanceRefs: ['prov:risk'],
    evidenceId: 'evidence:risk',
  },
  semanticArtifactReferences: [
    {
      confidence: 0.91,
      evidenceId: 'evidence:semantic',
      displayName: 'Checkout route',
      artifactKind: 'ui-route',
      artifactId: 'route-checkout',
      artifactRef: 'yappc:artifact:checkout',
      path: 'src/routes/Checkout.tsx',
      semanticTags: ['checkout', 'catalog'],
      provenanceRefs: ['prov:semantic'],
      riskLevel: 'low',
    },
  ],
}));

describe('CanvasPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
    useStudioLifecycleDataMock.mockReturnValue({
      snapshot: {
        status: 'ready',
        runtimeMode: 'configured',
        availableProductUnits: [],
        lifecycleRuns: [],
        pendingApprovals: [],
        manifestLoadState: {
          gateResultManifest: { status: 'loaded' },
          artifactManifest: { status: 'loaded' },
          deploymentManifest: { status: 'missing' },
          verifyHealthReport: { status: 'missing' },
        },
      },
      selectedProductUnitId: 'digital-marketing',
      selectedRunId: null,
      selectedEnvironment: 'local',
      selectedProviderMode: 'platform',
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
      previewProductUnitIntent: vi.fn(),
      applyProductUnitIntent: vi.fn(),
      refresh: vi.fn(),
    });
  });

  it('renders translated canvas risk labels instead of raw risk codes', () => {
    render(<CanvasPage />);

    expect(screen.getByText('studio.route.canvas.riskLevel.high')).toBeInTheDocument();
    expect(screen.getByText('studio.route.canvas.riskLevel.low')).toBeInTheDocument();
    expect(screen.getByText(/studio\.route\.canvas\.highestRiskPrefix/)).toBeInTheDocument();
  });
});
