import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DeploymentPanel } from '../DeploymentPanel';

describe('DeploymentPanel', () => {
  it('renders strategy, risk, and blockers', () => {
    render(
      <DeploymentPanel
        plan={{
          strategy: 'CANARY',
          riskScore: 6.4,
          readiness: 84,
          rationale: 'Roll out with observation gates while one lifecycle concern remains open.',
          riskFactors: ['lifecycle-blockers', 'phase-gate-pending'],
          blockers: ['Missing release notes sign-off'],
          requiresApproval: true,
          canaryPercent: 5,
        }}
      />
    );

    expect(screen.getByTestId('deployment-panel')).toBeInTheDocument();
    expect(screen.getByText('CANARY')).toBeInTheDocument();
    expect(screen.getByText(/Risk 6.4\/10/)).toBeInTheDocument();
    expect(screen.getByText('Missing release notes sign-off')).toBeInTheDocument();
    expect(screen.getByText('Required')).toBeInTheDocument();
  });
});