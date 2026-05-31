import { afterEach, describe, expect, it, vi } from 'vitest';
import { bookAppointment, cancelAppointment, rescheduleAppointment } from '../adminApi';

describe('appointmentsApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sends backend appointment contract fields for booking, reschedule, and cancel', async () => {
    const fetchMock = vi
      .fn(async (_input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        const body = typeof init?.body === 'string' ? JSON.parse(init.body) as Record<string, unknown> : {};
        if (String(_input).includes('/reschedule')) {
          expect(body).toMatchObject({ patientId: 'patient-42', scheduledTime: '2027-03-17T10:30:00Z' });
          return new Response(JSON.stringify({ id: 'appt-1', status: 'SCHEDULED' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (String(_input).includes('/cancel')) {
          expect(body).toMatchObject({ patientId: 'patient-42' });
          return new Response(JSON.stringify({ appointmentId: 'appt-1', status: 'CANCELLED' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        expect(body).toMatchObject({
          patientId: 'patient-42',
          providerId: 'provider-1',
          slotId: '2027-03-16T10:30:00Z',
          scheduledTime: '2027-03-16T10:30:00Z',
          appointmentType: 'IN_PERSON',
          reason: 'Follow up',
        });
        return new Response(JSON.stringify({ id: 'appt-1', status: 'SCHEDULED' }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        });
      });
    vi.stubGlobal('fetch', fetchMock);

    const context = { tenantId: 't1', principalId: 'patient-42', role: 'patient' as const };
    await expect(bookAppointment('patient-42', 'provider-1', '2027-03-16T10:30:00Z', 'Follow up', context)).resolves.toMatchObject({ id: 'appt-1' });
    await expect(rescheduleAppointment('appt-1', 'patient-42', '2027-03-17T10:30:00Z', context)).resolves.toMatchObject({ id: 'appt-1' });
    await expect(cancelAppointment('appt-1', 'patient-42', context)).resolves.toEqual({ success: true });
  });
});
