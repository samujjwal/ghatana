import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppointmentsPage } from '../AppointmentsPage';
import { bookAppointment, fetchAppointments, fetchProviders } from '../../api/adminApi';

vi.mock('../../api/adminApi', () => ({
  fetchAppointments: vi.fn(),
  fetchProviders: vi.fn(),
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
const mockFetchProviders = fetchProviders as ReturnType<typeof vi.fn>;
const mockBook = bookAppointment as ReturnType<typeof vi.fn>;

const provider = {
  id: 'provider-1',
  name: 'Dr. Sharma',
  specialty: 'General Medicine',
  availableSlots: ['2027-03-15T09:00:00Z'],
};

async function waitForForm(): Promise<void> {
  await screen.findByText('appointments.request.title');
}

async function chooseSlot(): Promise<void> {
  fireEvent.change(screen.getByLabelText('Specialty'), { target: { value: provider.specialty } });
  await waitFor(() => expect((screen.getByLabelText('Provider') as HTMLSelectElement).disabled).toBe(false));
  fireEvent.change(screen.getByLabelText('Provider'), { target: { value: provider.id } });
  await waitFor(() => expect((screen.getByLabelText('appointments.slot.label') as HTMLSelectElement).disabled).toBe(false));
  fireEvent.change(screen.getByLabelText('appointments.slot.label'), { target: { value: provider.availableSlots[0] } });
}

describe('AppointmentsPage', () => {
  beforeEach(() => {
    mockFetchAppointments.mockReset();
    mockFetchProviders.mockReset();
    mockBook.mockReset();
    mockFetchAppointments.mockResolvedValue([]);
    mockFetchProviders.mockResolvedValue([provider]);
  });

  it('keeps submit disabled when provider and slot are missing', async () => {
    render(<AppointmentsPage />);
    await waitForForm();

    expect((screen.getByRole('button', { name: 'appointments.book' }) as HTMLButtonElement).disabled).toBe(true);
    expect(mockBook).not.toHaveBeenCalled();
  });

  it('calls bookAppointment with form values on valid submit', async () => {
    mockBook.mockResolvedValue({ id: 'appt-new', status: 'requested' });

    render(<AppointmentsPage />);
    await waitForForm();
    await chooseSlot();
    fireEvent.click(screen.getByText('appointments.book'));

    await waitFor(() => expect(mockBook).toHaveBeenCalledOnce());
    expect(mockBook).toHaveBeenCalledWith(
      'patient-42',
      provider.id,
      provider.availableSlots[0],
      undefined,
      expect.any(Object),
    );
  });

  it('shows success message after successful submission', async () => {
    mockBook.mockResolvedValue({ id: 'appt-42', status: 'requested' });

    render(<AppointmentsPage />);
    await waitForForm();
    await chooseSlot();
    fireEvent.click(screen.getByText('appointments.book'));

    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
  });

  it('shows error message when API call fails', async () => {
    mockBook.mockRejectedValue(new Error('Booking failed'));

    render(<AppointmentsPage />);
    await waitForForm();
    await chooseSlot();
    fireEvent.click(screen.getByText('appointments.book'));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByRole('alert').textContent).toContain('Booking failed');
  });
});
