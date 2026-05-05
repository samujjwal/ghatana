import { describe, expect, it, vi } from 'vitest';

import { render, screen } from '@/test-utils/test-utils';

import { ResidualIslandReviewPanel, type ResidualIsland } from '../ResidualIslandReviewPanel';

const sampleIsland: ResidualIsland = {
  id: 'legacy-chart',
  type: 'CustomChart',
  description: 'Legacy chart rendering still requires manual review.',
  code: '<LegacyChart />',
  severity: 'high',
  requiredAction: 'review',
  filePath: 'src/pages/home.tsx',
  lineNumber: 42,
  provenanceSource: 'story:landing-page',
  roundTripFidelity: 0.82,
  blocking: false,
};

describe('ResidualIslandReviewPanel', () => {
  it('shows advisory metadata including provenance and fidelity', () => {
    render(
      <ResidualIslandReviewPanel
        islands={[sampleIsland]}
        onAccept={vi.fn()}
        onReject={vi.fn()}
        onReview={vi.fn()}
      />,
    );

    expect(screen.getByText('advisory')).toBeInTheDocument();
    expect(screen.getByText('Fidelity: 82%')).toBeInTheDocument();
    expect(screen.getByText('Provenance: story:landing-page')).toBeInTheDocument();
  });

  it('shows blocking summary when at least one residual is blocking', () => {
    const blockingIsland: ResidualIsland = {
      ...sampleIsland,
      id: 'legacy-payment-flow',
      requiredAction: 'block',
      blocking: true,
    };

    render(
      <ResidualIslandReviewPanel
        islands={[sampleIsland, blockingIsland]}
        onAccept={vi.fn()}
        onReject={vi.fn()}
        onReview={vi.fn()}
      />,
    );

    expect(screen.getByText(/1 blocking residual requires resolution before generate or release\./i)).toBeInTheDocument();
    expect(screen.getByText('blocking')).toBeInTheDocument();
  });

  it('submits review notes for the selected residual island', async () => {
    const onAccept = vi.fn();
    const onReject = vi.fn();
    const onReview = vi.fn();
    const { user } = render(
      <ResidualIslandReviewPanel
        islands={[sampleIsland]}
        onAccept={onAccept}
        onReject={onReject}
        onReview={onReview}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Review' }));
    await user.type(screen.getByPlaceholderText('Add review notes...'), 'Needs mapping to canonical chart component');
    await user.click(screen.getByRole('button', { name: 'Save Notes' }));

    expect(onReview).toHaveBeenCalledWith('legacy-chart', 'Needs mapping to canonical chart component');
    expect(onAccept).not.toHaveBeenCalled();
    expect(onReject).not.toHaveBeenCalled();
  });
});