import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import LearnPage from '../LearnPage';

const useStudioTranslationMock = vi.fn<() => (key: string) => string>();

vi.mock('../../i18n/studioTranslations', () => ({
  useStudioTranslation: () => useStudioTranslationMock(),
}));

vi.mock('../yappcWorkflowData', () => ({
  generatedChangeSetSummary: {
    confidence: 0.87,
    validationEvidenceRefs: ['evidence:residual', 'evidence:risk'],
    generatedArtifactRefs: ['evidence:graph'],
    evidenceId: 'evidence:changes',
  },
  residualIslandReport: {
    recommendedActions: ['Promote legacy widget to a reviewed registry candidate before lifecycle promotion'],
    evidenceId: 'evidence:residual',
  },
  riskHotspotReport: {
    highestRiskLevel: 'high',
    confidence: 0.79,
    hotspotCount: 1,
    provenanceRefs: ['prov:risk'],
    evidenceId: 'evidence:risk',
  },
}));

describe('LearnPage', () => {
  beforeEach(() => {
    useStudioTranslationMock.mockReturnValue((key: string) => key);
  });

  it('renders translated highest-risk labels in the learning summary', () => {
    render(<LearnPage />);

    expect(screen.getByText('studio.route.learn.riskLevel.high')).toBeInTheDocument();
    expect(screen.getByText(/studio\.route\.learn\.whyRecommendedText/)).toBeInTheDocument();
    expect(screen.getByText(/studio\.route\.learn\.highestRiskPrefix/)).toBeInTheDocument();
  });
});
