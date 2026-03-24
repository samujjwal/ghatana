// All tests skipped - incomplete feature
import { screen } from '@testing-library/react';
import React from 'react';

import { PredictionsTab } from './PredictionsTab';
import { render } from './test-utils';

const mockInsights = {
    buildTimePrediction: {
        predictedTime: 12.3,
        confidence: 0.85,
        range: { min: 10, max: 15 },
        factors: [{ name: 'Tests', description: 'Slow tests', impact: 0.1 }]
    }
};

test('renders prediction details', () => {
    render(<PredictionsTab insights={mockInsights as unknown} />);
    expect(screen.getByText(/Build Time Prediction/i)).toBeInTheDocument();
    expect(screen.getByText(/12.3 minutes/i)).toBeInTheDocument();
    expect(screen.getByText(/Confidence/i)).toBeInTheDocument();
});
