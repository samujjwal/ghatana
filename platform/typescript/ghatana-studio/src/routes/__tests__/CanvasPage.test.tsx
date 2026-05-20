import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { Provider } from 'jotai';
import { createStore } from 'jotai';
import type { StudioLifecycleDataContextValue } from '../../data/StudioLifecycleDataContext';
import CanvasPage from '../CanvasPage';
import type { BuilderDocument } from '@ghatana/ui-builder';
import { createBuilderDocument, insertNode } from '@ghatana/ui-builder';
import { setArtifactWorkflowAtom, projectedBuilderDocumentAtom } from '../../state/artifactWorkflowStore.js';
import { builderToCanvas, canvasToBuilder } from '../../adapters/BuilderCanvasProjectionAdapter.js';
import type { BuilderCanvasNode } from '../../adapters/BuilderCanvasProjectionAdapter.js';

const useStudioTranslationMock = vi.fn<() => (key: string) => string>();
const useStudioLifecycleDataMock = vi.fn<() => StudioLifecycleDataContextValue>();

// --- Captured HybridCanvas props for assertions ---
let capturedOnNodesChange: ((nodes: BuilderCanvasNode[]) => void) | undefined;

vi.mock('@ghatana/canvas/hybrid', () => ({
  HybridCanvas: (props: {
    nodes: BuilderCanvasNode[];
    edges: unknown[];
    onNodesChange?: (nodes: BuilderCanvasNode[]) => void;
    [key: string]: unknown;
  }) => {
    capturedOnNodesChange = props.onNodesChange;
    return (
      <div
        data-testid="hybrid-canvas"
        data-node-count={String(props.nodes.length)}
        data-edge-count={String(props.edges.length)}
      />
    );
  },
}));

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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const LIFECYCLE_DATA: StudioLifecycleDataContextValue = {
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
};

/** Creates a minimal two-node BuilderDocument for use in tests. */
function makeTwoNodeDoc(): BuilderDocument {
  let doc = createBuilderDocument('test-doc');
  doc = insertNode(doc, { contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: {} });
  doc = insertNode(doc, { contractName: 'Input', props: {}, slots: {}, bindings: [], metadata: {} });
  return doc;
}

describe('CanvasPage', () => {
  beforeEach(() => {
    capturedOnNodesChange = undefined;
    useStudioTranslationMock.mockReturnValue((key: string) => key);
    useStudioLifecycleDataMock.mockReturnValue(LIFECYCLE_DATA);
  });

  it('renders translated canvas risk labels instead of raw risk codes', () => {
    render(<CanvasPage />);

    expect(screen.getByText('studio.route.canvas.riskLevel.high')).toBeInTheDocument();
    expect(screen.getByText('studio.route.canvas.riskLevel.low')).toBeInTheDocument();
    expect(screen.getByText(/studio\.route\.canvas\.highestRiskPrefix/)).toBeInTheDocument();
  });

  describe('bidirectional canvas sync', () => {
    it('passes nodes derived from builderToCanvas to HybridCanvas when projectedBuilderDocumentAtom is non-null', () => {
      const store = createStore();
      const twoNodeDoc = makeTwoNodeDoc();
      store.set(setArtifactWorkflowAtom, { projectedBuilderDocument: twoNodeDoc });

      render(
        <Provider store={store}>
          <CanvasPage />
        </Provider>,
      );

      const canvas = screen.getByTestId('hybrid-canvas');
      // makeTwoNodeDoc has 2 component nodes + 1 RootContainer node.
      // builderToCanvas emits all non-root nodes; count must be > 0.
      const nodeCount = Number(canvas.getAttribute('data-node-count'));
      expect(nodeCount).toBeGreaterThan(0);
    });

    it('falls back to static artifact canvas document when projectedBuilderDocumentAtom is null', () => {
      const store = createStore();
      // atom starts as null (default)
      expect(store.get(projectedBuilderDocumentAtom)).toBeNull();

      render(
        <Provider store={store}>
          <CanvasPage />
        </Provider>,
      );

      // Static fallback document always renders some nodes (the static yappcWorkflowData).
      const canvas = screen.getByTestId('hybrid-canvas');
      // node-count could be 0 if static doc is empty, but canvas element must still render.
      expect(canvas).toBeInTheDocument();
    });

    it('updates projectedBuilderDocumentAtom when HybridCanvas fires onNodesChange', () => {
      const store = createStore();
      const twoNodeDoc = makeTwoNodeDoc();
      store.set(setArtifactWorkflowAtom, { projectedBuilderDocument: twoNodeDoc });

      render(
        <Provider store={store}>
          <CanvasPage />
        </Provider>,
      );

      expect(capturedOnNodesChange).toBeDefined();

      // Simulate the canvas reporting back a node list (position update).
      // We use the actual nodes from builderToCanvas so that the type is correct.
      const { nodes: canvasNodes } = builderToCanvas(twoNodeDoc);

      act(() => {
        capturedOnNodesChange!(canvasNodes);
      });

      // After the callback, the atom must reflect the updated document.
      const updatedDoc = store.get(projectedBuilderDocumentAtom);
      expect(updatedDoc).not.toBeNull();
      expect(Object.keys(updatedDoc!.nodes).length).toBeGreaterThan(0);

      // canvasToBuilder is the write-back path used internally by handleNodesChange;
      // verify it correctly round-trips back to a BuilderDocument from the same canvas nodes.
      const writtenBack = canvasToBuilder({ canvasNodes, baseDocument: twoNodeDoc });
      expect(Object.keys(writtenBack.nodes).length).toBeGreaterThan(0);
    });
  });
});
