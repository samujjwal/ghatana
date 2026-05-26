/**
 * Tests for FchvDashboardPage — verifies loading, error, and community patient list.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { FchvDashboardPage } from '../FchvDashboardPage';

vi.mock('../../api/phrApi', () => ({
  fetchFchvDashboard: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

import { fetchFchvDashboard } from '../../api/phrApi';

const mockFetch = fetchFchvDashboard as ReturnType<typeof vi.fn>;

const patients = [
  { id: 'fp-1', name: 'Maya Tamang', village: 'Lalitpur', pendingActions: 2, lastContact: '2025-01-15T00:00:00Z' },
  { id: 'fp-2', name: 'Devi Karki', village: 'Bhaktapur', pendingActions: 0, lastContact: null },
];

describe('FchvDashboardPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<FchvDashboardPage />);
    expect(screen.getByText('fchv.dashboard.loading')).toBeTruthy();
  });

  it('shows error when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<FchvDashboardPage />);
    await waitFor(() => expect(screen.getByText(/fchv\.dashboard\.error/)).toBeTruthy());
  });

  it('shows empty state when no patients', async () => {
    mockFetch.mockResolvedValue([]);
    render(<FchvDashboardPage />);
    await waitFor(() => expect(screen.getByText('provider.patients.empty')).toBeTruthy());
  });

  it('displays patient names and villages', async () => {
    mockFetch.mockResolvedValue(patients);
    render(<FchvDashboardPage />);
    await waitFor(() => expect(screen.getByText('Maya Tamang')).toBeTruthy());
    expect(screen.getByText('Lalitpur')).toBeTruthy();
    expect(screen.getByText('Devi Karki')).toBeTruthy();
  });

  it('shows pending action badge for patients with pending actions', async () => {
    mockFetch.mockResolvedValue(patients);
    render(<FchvDashboardPage />);
    await waitFor(() => expect(screen.getByText('2 pending')).toBeTruthy());
  });

  it('calls fetchFchvDashboard on mount', async () => {
    mockFetch.mockResolvedValue([]);
    render(<FchvDashboardPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));
  });
});
