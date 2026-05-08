import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThreatLifecycle } from '../ThreatLifecycle';
import type { ThreatRecord } from '../ThreatLifecycle';

function makeThreat(overrides: Partial<ThreatRecord> = {}): ThreatRecord {
  return {
    id: 'threat-1',
    asset: 'Auth Service',
    category: 'spoofing',
    description: 'Attacker impersonates authenticated user via stolen session',
    severity: 'HIGH',
    status: 'IDENTIFIED',
    auditTrail: [],
    ...overrides,
  };
}

describe('ThreatLifecycle', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders threats with status badges', () => {
    const threats = [
      makeThreat({ id: 'threat-1', status: 'IDENTIFIED' }),
      makeThreat({ id: 'threat-2', status: 'MITIGATED', description: 'SQL injection via query param' }),
    ];
    render(
      <ThreatLifecycle threats={threats} currentUser="alice" onDispose={vi.fn()} />
    );
    expect(screen.getByText('Identified')).toBeInTheDocument();
    expect(screen.getByText('Mitigated')).toBeInTheDocument();
  });

  it('shows open count badge when threats are IDENTIFIED', () => {
    render(
      <ThreatLifecycle
        threats={[makeThreat()]}
        currentUser="alice"
        onDispose={vi.fn()}
      />
    );
    expect(screen.getByText('1 open')).toBeInTheDocument();
  });

  it('shows "record disposition" button for IDENTIFIED threats', () => {
    render(
      <ThreatLifecycle
        threats={[makeThreat()]}
        currentUser="alice"
        onDispose={vi.fn()}
      />
    );
    expect(screen.getByTestId('threat-dispose-btn-threat-1')).toHaveClass('inline-flex');
  });

  it('does not show disposition button for disposed threats', () => {
    render(
      <ThreatLifecycle
        threats={[makeThreat({ status: 'ACCEPTED' })]}
        currentUser="alice"
        onDispose={vi.fn()}
      />
    );
    expect(screen.queryByTestId('threat-dispose-btn-threat-1')).toBeNull();
  });

  it('calls onDispose with correct arguments on confirm', async () => {
    const updated: ThreatRecord = makeThreat({ status: 'MITIGATED', auditTrail: [] });
    const onDispose = vi.fn().mockResolvedValue(updated);

    render(
      <ThreatLifecycle
        threats={[makeThreat()]}
        currentUser="alice"
        onDispose={onDispose}
      />
    );

    // Open the disposition form
    fireEvent.click(screen.getByTestId('threat-dispose-btn-threat-1'));

    // Form should appear
    expect(screen.getByTestId('threat-disposition-form-threat-1')).toBeInTheDocument();
    expect(screen.getByLabelText(/justification/i)).toHaveClass('text-sm');

    // Select MITIGATED (it's the default so just confirm)
    fireEvent.change(screen.getByLabelText(/justification/i), {
      target: { value: 'Added rate limiting' },
    });

    const confirmButton = screen.getByText('Confirm').closest('button');
    expect(confirmButton).toHaveClass('inline-flex');
    fireEvent.click(confirmButton!);
    await waitFor(() =>
      expect(onDispose).toHaveBeenCalledWith(
        'threat-1',
        'MITIGATED',
        'Added rate limiting'
      )
    );
  });

  it('renders empty state when no threats', () => {
    render(
      <ThreatLifecycle threats={[]} currentUser="alice" onDispose={vi.fn()} />
    );
    expect(screen.getByText('No threats identified yet.')).toBeInTheDocument();
  });

  it('shows threat asset and severity in each row', () => {
    render(
      <ThreatLifecycle
        threats={[makeThreat({ severity: 'CRITICAL', asset: 'Auth Service' })]}
        currentUser="alice"
        onDispose={vi.fn()}
      />
    );
    expect(screen.getByText('Auth Service')).toBeInTheDocument();
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });
});
