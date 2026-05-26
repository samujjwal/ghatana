/**
 * Tests for CaregiverDependentsPage — verifies loading, error, empty, and dependent list.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { CaregiverDependentsPage } from '../CaregiverDependentsPage';

vi.mock('../../api/phrApi', () => ({
  fetchCaregiverDependents: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

import { fetchCaregiverDependents } from '../../api/phrApi';

const mockFetch = fetchCaregiverDependents as ReturnType<typeof vi.fn>;

const dependents = [
  { id: 'dep-1', name: 'Arun Tamang', relationship: 'Son', age: 8 },
  { id: 'dep-2', name: 'Sarita Tamang', relationship: 'Daughter', age: null },
];

describe('CaregiverDependentsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<CaregiverDependentsPage />);
    expect(screen.getByText('caregiver.dependents.loading')).toBeTruthy();
  });

  it('shows error when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<CaregiverDependentsPage />);
    await waitFor(() => expect(screen.getByText(/caregiver\.dependents\.error/)).toBeTruthy());
  });

  it('shows empty state when no dependents', async () => {
    mockFetch.mockResolvedValue([]);
    render(<CaregiverDependentsPage />);
    await waitFor(() => expect(screen.getByText('caregiver.dependents.empty')).toBeTruthy());
  });

  it('displays dependent names and relationships', async () => {
    mockFetch.mockResolvedValue(dependents);
    render(<CaregiverDependentsPage />);
    await waitFor(() => expect(screen.getByText('Arun Tamang')).toBeTruthy());
    expect(screen.getByText('Son')).toBeTruthy();
    expect(screen.getByText('Sarita Tamang')).toBeTruthy();
  });

  it('shows age when present', async () => {
    mockFetch.mockResolvedValue(dependents);
    render(<CaregiverDependentsPage />);
    await waitFor(() => expect(screen.getByText('Age 8')).toBeTruthy());
  });

  it('calls fetchCaregiverDependents on mount', async () => {
    mockFetch.mockResolvedValue([]);
    render(<CaregiverDependentsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));
  });
});
