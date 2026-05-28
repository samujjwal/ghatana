import { afterEach, describe, expect, it, vi } from 'vitest';
import { createAppointmentRequest, fetchDashboardData, fetchNotifications } from '../api/phrApi';

describe('PHR API integration mapping', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('maps backend dashboard summary into the dashboard contract', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(input));
        if (url.pathname !== '/dashboard') {
          return new Response('Not found', { status: 404 });
        }
        const headers = new Headers(init?.headers);
        expect(headers.get('X-Tenant-Id')).toBe('tenant-health-1');
        expect(headers.get('X-Principal-Id')).toBe('patient-001');
        expect(headers.get('X-Role')).toBe('patient');

        return new Response(JSON.stringify({
          tenantId: 'tenant-health-1',
          principalId: 'patient-001',
          role: 'patient',
          correlationId: 'corr-1',
          profileSummary: {
            name: 'Aarati Shrestha',
            email: 'aarati@example.test',
            providerId: 'provider-1',
            active: true,
          },
          nextAppointment: null,
          medications: {
            activeCount: 2,
            adherenceAlert: false,
          },
          recentObservations: {
            count: 3,
            hasCritical: false,
          },
          activeConditions: {
            count: 1,
            hasChronic: true,
          },
          documents: {
            totalCount: 4,
            pendingOcr: 1,
          },
          accessAlerts: {
            expiringConsents: 0,
            emergencyAccessPending: false,
          },
          generatedAt: '2026-05-28T01:00:00Z',
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    const dashboard = await fetchDashboardData({
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      role: 'patient',
    });

    expect(dashboard.patient).toMatchObject({
      id: 'patient-001',
      name: 'Aarati Shrestha',
      location: 'tenant-health-1',
    });
    expect(dashboard.medications).toHaveLength(2);
    expect(dashboard.records).toEqual([]);
    expect(dashboard.consents).toEqual([]);
    expect(dashboard.appointments).toEqual([]);
  });

  it('adds an idempotency key to mutation requests by default', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
        const headers = new Headers(init?.headers);
        expect(headers.get('X-Idempotency-Key')).toMatch(
          /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
        );
        return new Response(JSON.stringify({
          id: 'appt-1',
          status: 'requested',
          specialty: 'Cardiology',
          preferredDate: '2026-06-01',
          createdAt: '2026-05-28T01:00:00Z',
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    await createAppointmentRequest(
      {
        specialty: 'Cardiology',
        preferredDate: '2026-06-01',
      },
      {
        tenantId: 'tenant-health-1',
        principalId: 'patient-001',
        role: 'patient',
      },
    );
  });

  it('rejects malformed notification payloads at the API boundary', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({
        items: [
          {
            id: 'notification-1',
            type: 'lab_result',
            title: 'Lab update',
            createdAt: '2026-05-28T01:00:00Z',
          },
        ],
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })),
    );

    await expect(fetchNotifications('patient-001', {
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      role: 'patient',
    })).rejects.toThrow();
  });
});
