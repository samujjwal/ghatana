/**
 * Tests for ProfilePage — verifies loading, error, and data display states.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ProfilePage } from '../ProfilePage';

vi.mock('../../api/phrApi', () => ({
  fetchPatientProfile: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

import { fetchPatientProfile } from '../../api/phrApi';

const mockFetch = fetchPatientProfile as ReturnType<typeof vi.fn>;

const sampleProfile = {
  id: 'p-1',
  name: 'Alice Tamang',
  age: 32,
  bloodType: 'O+',
  location: 'Kathmandu',
  emergencyContact: '+977-9800000001',
  birthDate: '1992-06-15',
  preferredLanguage: 'en',
};

describe('ProfilePage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {})); // never resolves
    render(<ProfilePage />);
    expect(screen.getByText(/route\.profile\.loading/)).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<ProfilePage />);
    await waitFor(() =>
      expect(screen.getByText(/route\.profile\.error/)).toBeTruthy()
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
});
