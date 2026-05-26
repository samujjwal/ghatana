/**
 * Tests for AppointmentsPage — verifies form validation and submit API call.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AppointmentsPage } from '../../pages/AppointmentsPage';

vi.mock('../../api/phrApi', () => ({
  fetchDashboardData: vi.fn(),
  createAppointmentRequest: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrDate: (d: string) => d,
  formatPhrDateTime: (d: string) => d,
}));

import { createAppointmentRequest, fetchDashboardData } from '../../api/phrApi';

const mockFetchDashboard = fetchDashboardData as ReturnType<typeof vi.fn>;
const mockCreate = createAppointmentRequest as ReturnType<typeof vi.fn>;

const emptyDashboard = {
  consents: [],
  appointments: [],
  labs: [],
  medications: [],
  records: [],
};

describe('AppointmentsPage', () => {
  beforeEach(() => {
    mockFetchDashboard.mockReset();
    mockCreate.mockReset();
    mockFetchDashboard.mockResolvedValue(emptyDashboard);
  });

  it('shows validation error when specialty is empty', async () => {
    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText(/appointments.loading/)).toBeNull());

    // No specialty filled — just click submit
    fireEvent.click(screen.getByText('appointments.submit'));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(mockCreate).not.toHaveBeenCalled();
  });

  it('calls createAppointmentRequest with form values on valid submit', async () => {
    mockCreate.mockResolvedValue({ id: 'appt-new', status: 'REQUESTED' });

    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText(/appointments.loading/)).toBeNull());

    const specialtyInput = screen.getByRole('textbox', { name: /appointments.specialty.label/i });

    fireEvent.change(specialtyInput, { target: { value: 'General Medicine' } });
    // Find date input by label text
    const inputs = document.querySelectorAll('input[type="date"]');
    fireEvent.change(inputs[0]!, { target: { value: '2027-03-15' } });

    fireEvent.click(screen.getByText('appointments.submit'));

    await waitFor(() => expect(mockCreate).toHaveBeenCalledOnce());
    expect(mockCreate).toHaveBeenCalledWith(
      expect.objectContaining({ specialty: 'General Medicine', preferredDate: '2027-03-15' }),
      expect.any(Object),
    );
  });

  it('shows success message after successful submission', async () => {
    mockCreate.mockResolvedValue({ id: 'appt-42', status: 'REQUESTED' });

    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText(/appointments.loading/)).toBeNull());

    const specialtyInput = screen.getByRole('textbox', { name: /appointments.specialty.label/i });
    fireEvent.change(specialtyInput, { target: { value: 'Cardiology' } });
    const inputs = document.querySelectorAll('input[type="date"]');
    fireEvent.change(inputs[0]!, { target: { value: '2027-04-01' } });

    fireEvent.click(screen.getByText('appointments.submit'));

    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
  });

  it('shows error message when API call fails', async () => {
    mockCreate.mockRejectedValue(new Error('Booking failed'));

    render(<AppointmentsPage />);
    await waitFor(() => expect(screen.queryByText(/appointments.loading/)).toBeNull());

    const specialtyInput = screen.getByRole('textbox', { name: /appointments.specialty.label/i });
    fireEvent.change(specialtyInput, { target: { value: 'ENT' } });
    const inputs = document.querySelectorAll('input[type="date"]');
    fireEvent.change(inputs[0]!, { target: { value: '2027-05-01' } });

    fireEvent.click(screen.getByText('appointments.submit'));

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByRole('alert').textContent).toContain('Booking failed');
  });
});
