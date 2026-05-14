/**
 * GateHealthPanel Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for the GateHealthPanel component
 * @doc.layer product
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { GateHealthPanel } from '../GateHealthPanel';

describe('GateHealthPanel', () => {
  const productUnitId = 'digital-marketing';

  describe('empty state', () => {
    it('renders the empty state when there are no gates', () => {
      render(<GateHealthPanel productUnitId={productUnitId} gates={[]} />);
      expect(screen.getByText(/no gate evaluations/i)).toBeInTheDocument();
    });
  });

  describe('with gate evaluations', () => {
    const passedGate = {
      id: 'security-gate',
      name: 'Security Gate',
      phase: 'build',
      status: 'passed' as const,
      required: true,
      criteria: ['No OWASP vulnerabilities', 'All unit tests pass'],
    };

    const failedGate = {
      id: 'compliance-gate',
      name: 'Compliance Gate',
      phase: 'deploy',
      status: 'failed' as const,
      required: true,
      criteria: ['All GDPR fields present'],
      reason: 'Missing consent field in user schema',
    };

    const blockedGate = {
      id: 'performance-gate',
      name: 'Performance Gate',
      phase: 'verify',
      status: 'blocked' as const,
      required: false,
      criteria: ['p99 < 200ms'],
    };

    it('renders a passed gate with its name', () => {
      render(<GateHealthPanel productUnitId={productUnitId} gates={[passedGate]} />);
      expect(screen.getByText('Security Gate')).toBeInTheDocument();
    });

    it('renders a failed gate with its failure reason', () => {
      render(<GateHealthPanel productUnitId={productUnitId} gates={[failedGate]} />);
      expect(screen.getByText('Compliance Gate')).toBeInTheDocument();
      expect(screen.getByText('Missing consent field in user schema')).toBeInTheDocument();
    });

    it('renders status badge for each gate', () => {
      render(
        <GateHealthPanel
          productUnitId={productUnitId}
          gates={[passedGate, failedGate, blockedGate]}
        />
      );
      expect(screen.getByText('passed')).toBeInTheDocument();
      expect(screen.getByText('failed')).toBeInTheDocument();
      expect(screen.getByText('blocked')).toBeInTheDocument();
    });

    it('renders the phase label for each gate', () => {
      render(
        <GateHealthPanel
          productUnitId={productUnitId}
          gates={[passedGate, failedGate]}
        />
      );
      expect(screen.getByText('build')).toBeInTheDocument();
      expect(screen.getByText('deploy')).toBeInTheDocument();
    });
  });
});
