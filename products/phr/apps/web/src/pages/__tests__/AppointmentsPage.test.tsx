import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppointmentsPage } from '../AppointmentsPage';
import { cancelAppointment, createAppointmentRequest, fetchAppointments, rescheduleAppointment } from '../../api/adminApi';

vi.mock('../../api/adminApi', () => ({
  fetchAppointments: vi.fn(),
  createAppointmentRequest: vi.fn(),
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
const mockCreateAppointmentRequest = createAppointmentRequest as ReturnType<typeof vi.fn>;
const mockCancelAppointment = cancelAppointment as ReturnType<typeof vi.fn>;
const mockRescheduleAppointment = rescheduleAppointment as ReturnType<typeof vi.fn>;

describe('AppointmentsPage', () => {
  beforeEach(() => {
    mockFetchAppointments.mockReset();
    mockCreateAppointmentRequest.mockReset();
    mockCancelAppointment.mockReset();
    mockRescheduleAppointment.mockReset();
    mockFetchAppointments.mockResolvedValue([]);
    mockCreateAppointmentRequest.mockResolvedValue({ id: 'request-1', status: 'requested', specialty: 'General', preferredDate: '2027-03-16T10:30', createdAt: '2026-05-31T10:00:00Z' });
    mockCancelAppointment.mockResolvedValue({ success: true });
    mockRescheduleAppointment.mockResolvedValue({ id: 'appt-1', status: 'confirmed' });
  });

  it('renders loading state initially', () => {
    render(<AppointmentsPage />);
    expect(screen.getByText('appointments.loading')).toBeInTheDocument();
  });

  it('renders appointments after loading', async () => {
    const appointments = [
      { id: 'appt-1', provider: 'Dr. Sharma', specialty: 'General Medicine', location: 'Clinic A', startsAt: '2027-03-15T09:00:00Z', status: 'confirmed', reminderSent: false },
    ];
    mockFetchAppointments.mockResolvedValue(appointments);

    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText('appointments.loading')).not.toBeInTheDocument());
    expect(screen.getByText('Dr. Sharma')).toBeInTheDocument();
  });

  it('shows empty state when no appointments', async () => {
    mockFetchAppointments.mockResolvedValue([]);

    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText('appointments.loading')).not.toBeInTheDocument());
    expect(screen.getByText('appointments.empty')).toBeInTheDocument();
  });

  it('cancels a confirmed upcoming appointment through the backend action', async () => {
    mockFetchAppointments.mockResolvedValue([
      { id: 'appt-1', provider: 'Dr. Sharma', specialty: 'General Medicine', location: 'Clinic A', startsAt: '2027-03-15T09:00:00Z', status: 'confirmed', reminderSent: false },
    ]);

    render(<AppointmentsPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'appointments.cancel' }));

    await waitFor(() => expect(mockCancelAppointment).toHaveBeenCalledWith('appt-1', 'patient-42', {
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('appointments.cancel.success');
  });

  it('submits a patient scheduling request through the backend action', async () => {
    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText('appointments.loading')).not.toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('appointments.specialty.label'), { target: { value: 'Cardiology' } });
    fireEvent.change(screen.getByLabelText('appointments.preferredDate.label'), { target: { value: '2027-03-16T10:30' } });
    fireEvent.change(screen.getByLabelText('appointments.notes.label'), { target: { value: 'Follow up' } });
    fireEvent.click(screen.getByRole('button', { name: 'appointments.submit' }));

    await waitFor(() => expect(mockCreateAppointmentRequest).toHaveBeenCalledWith({
      specialty: 'Cardiology',
      preferredDate: '2027-03-16T10:30',
      notes: 'Follow up',
    }, {
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('appointments.success');
  });

  it('reschedules a confirmed upcoming appointment without using a browser prompt', async () => {
    const promptSpy = vi.spyOn(window, 'prompt');
    mockFetchAppointments.mockResolvedValue([
      { id: 'appt-1', provider: 'Dr. Sharma', specialty: 'General Medicine', location: 'Clinic A', startsAt: '2027-03-15T09:00:00Z', status: 'confirmed', reminderSent: false },
    ]);

    render(<AppointmentsPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'appointments.reschedule' }));
    fireEvent.change(screen.getByLabelText('appointments.reschedule.slot.label'), { target: { value: '2027-03-16T10:30:00Z' } });
    fireEvent.click(screen.getByRole('button', { name: 'appointments.reschedule.save' }));

    await waitFor(() => expect(mockRescheduleAppointment).toHaveBeenCalledWith('appt-1', 'patient-42', '2027-03-16T10:30:00Z', {
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    }));
    expect(promptSpy).not.toHaveBeenCalled();
  });
});
