import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import CanvasPage from '../CanvasPage';

const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock('../../i18n/studioTranslations', () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
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
  });

  it('renders translated canvas risk labels instead of raw risk codes', () => {
    render(<CanvasPage />);

    expect(screen.getByText('studio.route.canvas.riskLevel.high')).toBeInTheDocument();
    expect(screen.getByText('studio.route.canvas.riskLevel.low')).toBeInTheDocument();
    expect(screen.getByText(/studio\.route\.canvas\.highestRiskPrefix/)).toBeInTheDocument();
  });
});
