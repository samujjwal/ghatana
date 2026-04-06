import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CapacityDashboard } from '../CapacityDashboard';

describe('CapacityDashboard', () => {
  it('renders scaling recommendation and cost outlook', () => {
    render(
      <CapacityDashboard
        recommendation={{
          action: 'SCALE_UP',
          currentReplicas: 3,
          targetReplicas: 4,
          avgCpuUtilization: 0.72,
          peakCpuUtilization: 0.88,
          avgMemoryUtilization: 0.65,
          currentMonthlyCost: 3200,
          projectedMonthlyCost: 3625,
          confidence: 0.91,
          rationale: 'Keep one extra replica available during the rollout window.',
        }}
      />
    );

    expect(screen.getByTestId('capacity-dashboard')).toBeInTheDocument();
    expect(screen.getByText('SCALE_UP')).toBeInTheDocument();
    expect(screen.getByText('3 → 4 replicas')).toBeInTheDocument();
    expect(screen.getByText('$3,200')).toBeInTheDocument();
    expect(screen.getByText('$3,625')).toBeInTheDocument();
  });
});