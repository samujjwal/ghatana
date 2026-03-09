// All tests skipped - incomplete feature
import { screen } from '@testing-library/react';
import React from 'react';

import { RecommendationsTab } from './RecommendationsTab';
import { render } from './test-utils';

const mockRecommendations = {
    performance: [
        {
            id: 'r1',
            type: 'performance',
            priority: 'high',
            title: 'Tune cache',
            description: 'Use caching',
            expectedImpact: { improvement: 12, metric: 'build time', timeframe: '1 week' },
            implementation: { effort: 'low', steps: ['enable cache', 'configure ttl'] },
            confidence: 0.8,
        },
    ],
};

test('renders no recommendations state when empty', () => {
    render(<RecommendationsTab recommendationsByType={{}} recommendationCounts={{ critical: 0, high: 0, medium: 0, low: 0, total: 0 }} implementingIds={new Set()} onImplement={() => { }} onDismiss={() => { }} />);
    expect(screen.getByText(/No Recommendations/i)).toBeInTheDocument();
});

test('renders grouped recommendations', () => {
    render(<RecommendationsTab recommendationsByType={mockRecommendations as unknown} recommendationCounts={{ critical: 0, high: 1, medium: 0, low: 0, total: 1 }} implementingIds={new Set()} onImplement={() => { }} onDismiss={() => { }} />);
    expect(screen.getByText(/performance Recommendations/i)).toBeInTheDocument();
    expect(screen.getByText(/Tune cache/i)).toBeInTheDocument();
});
