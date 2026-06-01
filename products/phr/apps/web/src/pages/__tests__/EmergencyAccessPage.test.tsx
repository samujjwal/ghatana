/**
 * Tests for EmergencyAccessPage — verifies break-glass request and review workflows.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { EmergencyAccessPage } from '../../pages/EmergencyAccessPage';
import { EmergencyReviewsPage } from '../../pages/EmergencyReviewsPage';

vi.mock('../../api/emergencyApi', () => ({
  requestEmergencyAccess: vi.fn(),
  reviewEmergencyAccess: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params && Object.keys(params).length > 0) {
      return `${key}:${JSON.stringify(params)}`;
    }
    return key;
  },
}));

vi.mock('../../auth/PhrAccessContext', () => ({
  usePhrRequestContext: () => ({
    tenantId: 'tenant-test',
    principalId: 'clinician-42',
    role: 'clinician',
    persona: 'clinician',
    tier: 'clinical',
    facilityId: 'facility-1',
    correlationId: 'corr-emergency-1',
  }),
}));

import { requestEmergencyAccess, reviewEmergencyAccess } from '../../api/emergencyApi';

const mockRequest = requestEmergencyAccess as ReturnType<typeof vi.fn>;
const mockReview = reviewEmergencyAccess as ReturnType<typeof vi.fn>;

const stubEvent = {
  id: 'evt-001',
  patientId: 'patient-1',
  clinicianId: 'clinician-1',
  reason: 'Unconscious patient',
  timestamp: '2025-01-01T00:00:00Z',
  reviewNote: null,
  reviewedAt: null,
  reviewerId: null,
};

const fillEmergencyRequest = (): void => {
  fireEvent.change(screen.getByLabelText('emergency.patientId.label'), { target: { value: '  patient-42  ' } });
  fireEvent.change(screen.getByLabelText('emergency.clinicianId.label'), { target: { value: 'clinician-7' } });
  fireEvent.change(screen.getByLabelText('emergency.reason.label'), { target: { value: 'Unconscious patient in A&E.' } });
};

const submitAndConfirmEmergencyRequest = async (): Promise<void> => {
  fireEvent.click(screen.getByRole('button', { name: /emergency.request/i }));
  await waitFor(() => expect(screen.getByRole('button', { name: /emergency.confirm.submit/i })).toBeTruthy());
  fireEvent.click(screen.getByRole('button', { name: /emergency.confirm.submit/i }));
};

describe('EmergencyAccessPage – request workflow', () => {
  beforeEach(() => {
    mockRequest.mockReset();
    mockReview.mockReset();
  });

  it('renders the break-glass form', () => {
    render(<EmergencyAccessPage />);
    expect(screen.getByRole('button', { name: /emergency.request/i })).toBeTruthy();
    expect(screen.getByLabelText('emergency.patientId.label')).toBeTruthy();
    expect(screen.getByLabelText('emergency.clinicianId.label')).toBeTruthy();
    expect(screen.getByLabelText('emergency.reason.label')).toBeTruthy();
  });

  it('shows a validation error when required fields are empty', async () => {
    render(<EmergencyAccessPage />);
    fireEvent.click(screen.getByRole('button', { name: /emergency.request/i }));
    await waitFor(() => expect(screen.getAllByRole('alert').length).toBeGreaterThan(0));
    expect(mockRequest).not.toHaveBeenCalled();
  });

  it('calls requestEmergencyAccess with trimmed values on valid submit', async () => {
    mockRequest.mockResolvedValue(stubEvent);
    render(<EmergencyAccessPage />);

    fillEmergencyRequest();

    fireEvent.click(screen.getByRole('button', { name: /emergency.request/i }));
    await waitFor(() => expect(screen.getByRole('button', { name: /emergency.confirm.submit/i })).toBeTruthy());
    expect(mockRequest).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /emergency.confirm.submit/i }));

    await waitFor(() => expect(mockRequest).toHaveBeenCalledTimes(1));

    const [payload] = mockRequest.mock.calls[0] as [{ patientId: string; reason: string; clinicianId: string }, unknown];
    expect(payload.patientId).toBe('patient-42');
    expect(payload.clinicianId).toBe('clinician-7');
    expect(payload.reason).toBe('Unconscious patient in A&E.');
    expect(mockRequest.mock.calls[0]?.[1]).toMatchObject({
      tenantId: 'tenant-test',
      principalId: 'clinician-42',
      role: 'clinician',
      persona: 'clinician',
      tier: 'clinical',
      facilityId: 'facility-1',
      correlationId: 'corr-emergency-1',
    });
  });

  it('shows success message after approved request', async () => {
    mockRequest.mockResolvedValue({ ...stubEvent, id: 'evt-999' });
    render(<EmergencyAccessPage />);

    fireEvent.change(screen.getByLabelText('emergency.patientId.label'), { target: { value: 'p1' } });
    fireEvent.change(screen.getByLabelText('emergency.clinicianId.label'), { target: { value: 'c1' } });
    fireEvent.change(screen.getByLabelText('emergency.reason.label'), { target: { value: 'Critical emergency' } });
    await submitAndConfirmEmergencyRequest();

    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
    expect(screen.getByRole('status').textContent).toContain('emergency.success.request');
  });

  it('clears form fields after a successful request', async () => {
    mockRequest.mockResolvedValue(stubEvent);
    render(<EmergencyAccessPage />);

    const patientInput = screen.getByLabelText('emergency.patientId.label') as HTMLInputElement;
    fireEvent.change(patientInput, { target: { value: 'p1' } });
    fireEvent.change(screen.getByLabelText('emergency.clinicianId.label'), { target: { value: 'c1' } });
    fireEvent.change(screen.getByLabelText('emergency.reason.label'), { target: { value: 'Critical emergency' } });
    await submitAndConfirmEmergencyRequest();

    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
    expect(patientInput.value).toBe('');
  });

  it('shows an error message when requestEmergencyAccess rejects', async () => {
    mockRequest.mockRejectedValue(new Error('Backend refused'));
    render(<EmergencyAccessPage />);

    fireEvent.change(screen.getByLabelText('emergency.patientId.label'), { target: { value: 'p1' } });
    fireEvent.change(screen.getByLabelText('emergency.clinicianId.label'), { target: { value: 'c1' } });
    fireEvent.change(screen.getByLabelText('emergency.reason.label'), { target: { value: 'Critical emergency' } });
    await submitAndConfirmEmergencyRequest();

    await waitFor(() => expect(screen.getAllByRole('alert').length).toBeGreaterThan(0));
    const alerts = screen.getAllByRole('alert');
    const combined = alerts.map((a) => a.textContent ?? '').join(' ');
    expect(combined).toContain('Backend refused');
  });
});

describe('EmergencyAccessPage – review workflow', () => {
  beforeEach(() => {
    mockRequest.mockReset();
    mockReview.mockReset();
  });

  it('renders the review form', () => {
    render(<EmergencyReviewsPage />);
    expect(screen.getByRole('button', { name: /emergency.review.submit/i })).toBeTruthy();
    expect(screen.getByLabelText('emergency.reviewEventId.label')).toBeTruthy();
    expect(screen.getByLabelText('emergency.review.note.label')).toBeTruthy();
  });

  it('shows a validation error when review fields are empty', async () => {
    render(<EmergencyReviewsPage />);
    fireEvent.click(screen.getByRole('button', { name: /emergency.review.submit/i }));
    await waitFor(() => expect(screen.getAllByRole('alert').length).toBeGreaterThan(0));
    expect(mockReview).not.toHaveBeenCalled();
  });

  it('calls reviewEmergencyAccess with trimmed values', async () => {
    mockReview.mockResolvedValue({ ...stubEvent, reviewNote: 'LGTM', id: 'evt-review-1' });
    render(<EmergencyReviewsPage />);

    fireEvent.change(screen.getByLabelText('emergency.reviewEventId.label'), { target: { value: '  evt-review-1  ' } });
    fireEvent.change(screen.getByLabelText('emergency.review.note.label'), { target: { value: 'Reviewed: appropriate use' } });
    fireEvent.click(screen.getByRole('button', { name: /emergency.review.submit/i }));

    await waitFor(() => expect(mockReview).toHaveBeenCalledTimes(1));
    const [payload] = mockReview.mock.calls[0] as [{ eventId: string; reviewNote: string }, unknown];
    expect(payload.eventId).toBe('evt-review-1');
    expect(payload.reviewNote).toBe('Reviewed: appropriate use');
    expect(payload).toMatchObject({ reviewerId: 'clinician-42' });
    expect(mockReview.mock.calls[0]?.[1]).toMatchObject({
      tenantId: 'tenant-test',
      principalId: 'clinician-42',
      role: 'clinician',
      persona: 'clinician',
      tier: 'clinical',
      facilityId: 'facility-1',
      correlationId: 'corr-emergency-1',
    });
  });

  it('shows success message after successful review', async () => {
    mockReview.mockResolvedValue({ ...stubEvent, id: 'evt-rev-42' });
    render(<EmergencyReviewsPage />);

    fireEvent.change(screen.getByLabelText('emergency.reviewEventId.label'), { target: { value: 'evt-rev-42' } });
    fireEvent.change(screen.getByLabelText('emergency.review.note.label'), { target: { value: 'Review note text' } });
    fireEvent.click(screen.getByRole('button', { name: /emergency.review.submit/i }));

    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
    expect(screen.getByRole('status').textContent).toContain('emergency.success.review');
  });

  it('shows error message when reviewEmergencyAccess rejects', async () => {
    mockReview.mockRejectedValue(new Error('Review service unavailable'));
    render(<EmergencyReviewsPage />);

    fireEvent.change(screen.getByLabelText('emergency.reviewEventId.label'), { target: { value: 'evt-1' } });
    fireEvent.change(screen.getByLabelText('emergency.review.note.label'), { target: { value: 'Some review note' } });
    fireEvent.click(screen.getByRole('button', { name: /emergency.review.submit/i }));

    await waitFor(() => expect(screen.getAllByRole('alert').length).toBeGreaterThan(0));
    const alerts = screen.getAllByRole('alert');
    const combined = alerts.map((a) => a.textContent ?? '').join(' ');
    expect(combined).toContain('Review service unavailable');
  });
});
