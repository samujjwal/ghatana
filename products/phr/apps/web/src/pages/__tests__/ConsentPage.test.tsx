/**
 * Tests for ConsentPage — verifies grant creation, revoke, and error display.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ConsentPage } from '../../pages/ConsentPage';

vi.mock('../../api/phrApi', () => ({
  fetchDashboardData: vi.fn(),
  createConsentGrant: vi.fn(),
  revokeConsentGrant: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrDate: (d: string) => d,
}));

import { createConsentGrant, fetchDashboardData, revokeConsentGrant } from '../../api/phrApi';

const mockFetchDashboard = fetchDashboardData as ReturnType<typeof vi.fn>;
const mockCreate = createConsentGrant as ReturnType<typeof vi.fn>;
const mockRevoke = revokeConsentGrant as ReturnType<typeof vi.fn>;

const sampleConsent = {
  id: 'grant-1',
  recipient: 'Dr. Sharma',
  purpose: 'Treatment',
  expiresAt: '2027-01-01',
  status: 'active' as const,
};

const dashboardWithConsent = {
  consents: [sampleConsent],
  appointments: [],
  labs: [],
  medications: [],
  records: [],
};

describe('ConsentPage', () => {
  beforeEach(() => {
    mockFetchDashboard.mockReset();
    mockCreate.mockReset();
    mockRevoke.mockReset();
    mockFetchDashboard.mockResolvedValue(dashboardWithConsent);
  });

  it('renders existing consents after load', async () => {
    render(<ConsentPage />);
    await waitFor(() => expect(screen.getByText('Dr. Sharma')).toBeTruthy());
  });

  it('shows grant form when Grant New is clicked', async () => {
    render(<ConsentPage />);
    await waitFor(() => expect(screen.getByText('Dr. Sharma')).toBeTruthy());

    fireEvent.click(screen.getByText('consents.grantNew'));
    expect(screen.getByRole('button', { name: /consents.grant.submit/i })).toBeTruthy();
  });

  it('calls createConsentGrant with form values on submit', async () => {
    mockCreate.mockResolvedValue({ id: 'grant-new', status: 'active' });

    render(<ConsentPage />);
    await waitFor(() => expect(screen.getByText('Dr. Sharma')).toBeTruthy());

    fireEvent.click(screen.getByText('consents.grantNew'));

    const inputs = screen.getAllByRole('textbox');
    fireEvent.change(inputs[0]!, { target: { value: 'grantee-42' } });
    fireEvent.change(inputs[1]!, { target: { value: 'Treatment' } });
    fireEvent.change(inputs[2]!, { target: { value: 'Patient,Observation' } });
    fireEvent.change(inputs[3]!, { target: { value: '2027-12-31' } });

    fireEvent.click(screen.getByText('consents.grant.submit'));

    await waitFor(() => expect(mockCreate).toHaveBeenCalledOnce());
    expect(mockCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        granteeId: 'grantee-42',
        purpose: 'Treatment',
        resourceTypes: ['Patient', 'Observation'],
      }),
      expect.any(Object),
    );
  });

  it('shows error when createConsentGrant fails', async () => {
    mockCreate.mockRejectedValue(new Error('Server error'));

    render(<ConsentPage />);
    await waitFor(() => expect(screen.getByText('Dr. Sharma')).toBeTruthy());

    fireEvent.click(screen.getByText('consents.grantNew'));
    const inputs = screen.getAllByRole('textbox');
    fireEvent.change(inputs[0]!, { target: { value: 'g1' } });
    fireEvent.change(inputs[1]!, { target: { value: 'Tx' } });
    fireEvent.change(inputs[2]!, { target: { value: 'Patient' } });
    fireEvent.change(inputs[3]!, { target: { value: '2027-01-01' } });

    fireEvent.click(screen.getByText('consents.grant.submit'));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByRole('alert').textContent).toContain('Server error');
  });

  it('calls revokeConsentGrant when Revoke is clicked and confirmed', async () => {
    mockRevoke.mockResolvedValue({ grantId: 'grant-1', status: 'REVOKED' });
    vi.stubGlobal('confirm', () => true);

    render(<ConsentPage />);
    await waitFor(() => expect(screen.getByText('Dr. Sharma')).toBeTruthy());

    fireEvent.click(screen.getByRole('button', { name: /consents.revoke Dr. Sharma/i }));

    await waitFor(() => expect(mockRevoke).toHaveBeenCalledWith('grant-1', 'current', expect.any(Object)));

    vi.unstubAllGlobals();
  });
});
