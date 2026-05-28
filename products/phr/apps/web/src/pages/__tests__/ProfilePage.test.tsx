/**
 * Tests for ProfilePage — verifies loading, error, and data display states.
 */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ProfilePage } from '../ProfilePage';

vi.mock('../../api/patientApi', () => ({
  fetchPatientProfile: vi.fn(),
  updatePatientProfile: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }, isAuthenticated: true, setSession: vi.fn(), clearSession: vi.fn() }),
}));

import { fetchPatientProfile, updatePatientProfile } from '../../api/patientApi';

const mockFetch = vi.mocked(fetchPatientProfile);
const mockUpdate = vi.mocked(updatePatientProfile);

const sampleProfile = {
  id: 'p-1',
  name: 'Alice Tamang',
  age: 32,
  bloodType: 'O+',
  location: 'Kathmandu',
  emergencyContact: '+977-9800000001',
  birthDate: '1992-06-15',
  preferredLanguage: 'en',
  facilityId: 'facility-1',
  mrn: 'MRN-1',
  gender: 'female',
};

describe('ProfilePage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    mockUpdate.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {})); // never resolves
    render(<ProfilePage />);
    expect(screen.getByText(/profile\.loading/)).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<ProfilePage />);
    await waitFor(() =>
      expect(screen.getByText(/profile\.error/)).toBeTruthy()
    );
  });

  it('displays patient name after successful fetch', async () => {
    mockFetch.mockResolvedValue(sampleProfile);
    render(<ProfilePage />);
    await waitFor(() => expect(screen.getByText('Alice Tamang')).toBeTruthy());
  });

  it('displays blood type', async () => {
    mockFetch.mockResolvedValue(sampleProfile);
    render(<ProfilePage />);
    await waitFor(() => expect(screen.getByText('O+')).toBeTruthy());
  });

  it('saves editable patient preference fields', async () => {
    mockFetch.mockResolvedValue(sampleProfile);
    mockUpdate.mockResolvedValue({ ...sampleProfile, emergencyContact: '+977-9800000002', preferredLanguage: 'ne' });
    render(<ProfilePage />);

    fireEvent.click(await screen.findByRole('button', { name: 'profile.edit' }));
    fireEvent.change(screen.getByLabelText('profile.emergencyContact'), { target: { value: '+977-9800000002' } });
    fireEvent.change(screen.getByLabelText('profile.language'), { target: { value: 'ne' } });
    fireEvent.click(screen.getByRole('button', { name: 'profile.save' }));

    await waitFor(() => expect(mockUpdate).toHaveBeenCalledWith({
      emergencyContact: '+977-9800000002',
      preferredLanguage: 'ne',
    }, {
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('profile.saved');
  });

  it('does not expose facility editing for patient role', async () => {
    mockFetch.mockResolvedValue(sampleProfile);
    render(<ProfilePage />);

    fireEvent.click(await screen.findByRole('button', { name: 'profile.edit' }));

    expect(screen.queryByLabelText('profile.facilityId')).toBeNull();
    expect(screen.getByText('profile.facilityManaged')).toBeTruthy();
  });

  it('validates emergency contact length before saving', async () => {
    mockFetch.mockResolvedValue(sampleProfile);
    render(<ProfilePage />);

    fireEvent.click(await screen.findByRole('button', { name: 'profile.edit' }));
    fireEvent.change(screen.getByLabelText('profile.emergencyContact'), { target: { value: '1'.repeat(81) } });
    fireEvent.click(screen.getByRole('button', { name: 'profile.save' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('profile.validation.emergencyContactLength');
    expect(mockUpdate).not.toHaveBeenCalled();
  });

  it('cancels edits and restores displayed values', async () => {
    mockFetch.mockResolvedValue(sampleProfile);
    render(<ProfilePage />);

    fireEvent.click(await screen.findByRole('button', { name: 'profile.edit' }));
    fireEvent.change(screen.getByLabelText('profile.emergencyContact'), { target: { value: '+977-9999999999' } });
    fireEvent.click(screen.getByRole('button', { name: 'profile.cancel' }));

    expect(screen.getByText('+977-9800000001')).toBeTruthy();
    expect(screen.queryByText('+977-9999999999')).toBeNull();
  });
});
