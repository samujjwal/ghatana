import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { CapabilityGate } from '../components/common/CapabilityGate';
import * as useCapabilityGateModule from '../hooks/useCapabilityGate';

vi.mock('../hooks/useCapabilityGate', () => ({
  useCapabilityGate: vi.fn(),
}));

describe('Smoke test — real component rendering', () => {
  it('renders CapabilityGate children when capability is granted', () => {
    vi.spyOn(useCapabilityGateModule, 'useCapabilityGate').mockReturnValue({
      granted: true,
      reason: undefined,
    });

    render(
      <CapabilityGate capability="admin:billing">
        <div data-testid="granted-content">Billing Dashboard</div>
      </CapabilityGate>
    );

    expect(screen.getByTestId('granted-content')).toBeInTheDocument();
    expect(screen.getByText('Billing Dashboard')).toBeInTheDocument();
  });

  it('renders fallback when capability is denied', () => {
    vi.spyOn(useCapabilityGateModule, 'useCapabilityGate').mockReturnValue({
      granted: false,
      reason: 'backend-not-live',
    });

    render(
      <CapabilityGate
        capability="admin:billing"
        fallback={<div data-testid="denied-fallback">Coming soon</div>}
      >
        <div data-testid="granted-content">Billing Dashboard</div>
      </CapabilityGate>
    );

    expect(screen.queryByTestId('granted-content')).not.toBeInTheDocument();
    expect(screen.getByTestId('denied-fallback')).toBeInTheDocument();
  });
});
