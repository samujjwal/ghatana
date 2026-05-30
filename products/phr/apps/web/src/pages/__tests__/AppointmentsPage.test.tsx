import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppointmentsPage } from '../AppointmentsPage';
import { fetchAppointments } from '../../api/adminApi';

vi.mock('../../api/adminApi', () => ({
  fetchAppointments: vi.fn(),
  bookAppointment: vi.fn(),
  cancelAppointment: vi.fn(),
  rescheduleAppointment: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: (() => {
    const session = { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: '2027-01-01T00:00:00Z' };
    return () => ({
      session,
      isAuthenticated: true,
      setSession: vi.fn(),
      clearSession: vi.fn(),
    });
  })(),
}));

const mockFetchAppointments = fetchAppointments as ReturnType<typeof vi.fn>;

describe('AppointmentsPage', () => {
  beforeEach(() => {
    mockFetchAppointments.mockReset();
    mockFetchAppointments.mockResolvedValue([]);
  });

  it('renders loading state initially', () => {
    render(<AppointmentsPage />);
    expect(screen.getByText('Loading appointments...')).toBeInTheDocument();
  });

  it('renders appointments after loading', async () => {
    const appointments = [
      { id: 'appt-1', provider: 'Dr. Sharma', specialty: 'General Medicine', location: 'Clinic A', startsAt: '2027-03-15T09:00:00Z', status: 'confirmed', reminderSent: false },
    ];
    mockFetchAppointments.mockResolvedValue(appointments);

    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText('Loading appointments...')).not.toBeInTheDocument());
    expect(screen.getByText('Dr. Sharma')).toBeInTheDocument();
  });

  it('shows empty state when no appointments', async () => {
    mockFetchAppointments.mockResolvedValue([]);

    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText('Loading appointments...')).not.toBeInTheDocument());
    expect(screen.getByText('No upcoming appointments')).toBeInTheDocument();
  });
});
