import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  createAppointmentRequest,
  createConsentGrant,
  exportPatientBundle,
  fetchDashboardData,
  fetchMedicationDetail,
  fetchMedications,
  fetchNotifications,
  fetchRecords,
  logoutSession,
} from '../api/phrApi';

describe('PHR API integration mapping', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('maps backend dashboard summary into the dashboard contract', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(input));
        if (url.pathname !== '/api/v1/dashboard') {
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

    expect(dashboard).toMatchObject({
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      profileSummary: {
        name: 'Aarati Shrestha',
      },
      medications: {
        activeCount: 2,
      },
    });
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

  it('parses native consent grant responses through the schema boundary', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
        const headers = new Headers(init?.headers);
        expect(headers.get('X-Idempotency-Key')).toBeTruthy();
        return new Response(JSON.stringify({
          id: 'grant-1',
          recipient: 'provider-1',
          purpose: 'Care coordination',
          status: 'active',
          expiresAt: '2026-06-30',
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    await expect(createConsentGrant(
      {
        patientId: 'patient-001',
        recipientId: 'provider-1',
        purpose: 'Care coordination',
        scope: { resourceTypes: ['Observation'] },
        expiresAt: '2026-06-30',
      },
      {
        tenantId: 'tenant-health-1',
        principalId: 'patient-001',
        role: 'patient',
      },
    )).resolves.toMatchObject({
      id: 'grant-1',
      recipient: 'provider-1',
      status: 'active',
    });
  });

  it('maps FHIR consent grant responses through the same consent contract', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({
        resourceType: 'Consent',
        id: 'consent-1',
        status: 'active',
        organization: [{ display: 'Kantipur Clinic' }],
        purpose: [{ display: 'Referral' }],
        provision: {
          period: { end: '2026-07-01T00:00:00Z' },
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })),
    );

    await expect(createConsentGrant(
      {
        patientId: 'patient-001',
        recipientId: 'provider-1',
        purpose: 'Referral',
        scope: { resourceTypes: ['DocumentReference'] },
        expiresAt: '2026-07-01',
      },
      {
        tenantId: 'tenant-health-1',
        principalId: 'patient-001',
        role: 'patient',
      },
    )).resolves.toMatchObject({
      id: 'consent-1',
      recipient: 'Kantipur Clinic',
      purpose: 'Referral',
    });
  });

  it('uses contract-backed HIE export responses and empty logout responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(String(input));
        if (url.pathname === '/api/v1/hie/export') {
          return new Response(JSON.stringify({
            requestId: 'hie-1',
            operation: 'EXPORT',
            contractId: 'test-hie-contract',
            status: 'ACCEPTED',
            reasonCode: 'HIE_ACCEPTED',
            correlationId: 'corr-1',
          }), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (url.pathname === '/api/v1/auth/logout') {
          return new Response(null, { status: 204 });
        }
        return new Response('Not found', { status: 404 });
      }),
    );

    await expect(exportPatientBundle({
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      role: 'patient',
    })).resolves.toBe('ACCEPTED:hie-1');
    await expect(logoutSession({
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      role: 'patient',
    })).resolves.toBeUndefined();
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

  it('loads patient records from the backend-owned record list endpoint', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(input));
        expect(url.pathname).toBe('/api/v1/records');
        expect(url.searchParams.get('patientId')).toBe('patient-001');
        expect(url.searchParams.get('category')).toBe('administrative');
        expect(url.searchParams.get('limit')).toBe('50');
        const headers = new Headers(init?.headers);
        expect(headers.get('X-Tenant-Id')).toBe('tenant-health-1');
        expect(headers.get('X-Principal-Id')).toBe('patient-001');
        return new Response(JSON.stringify({
          patientId: 'patient-001',
          items: [{
            id: 'patient-001',
            title: 'Patient profile',
            category: 'administrative',
            updatedAt: '2026-05-28T01:00:00Z',
            resourceType: 'Patient',
            redacted: false,
            provenance: {
              source: 'phr-patient-record-service',
            },
          }],
          count: 1,
          limit: 50,
          offset: 0,
          generatedAt: '2026-05-28T01:00:00Z',
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );

    await expect(fetchRecords('patient-001', {
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      role: 'patient',
    }, {
      category: 'administrative',
    })).resolves.toEqual([
      expect.objectContaining({
        id: 'patient-001',
        resourceType: 'Patient',
        category: 'administrative',
      }),
    ]);
  });

  it('loads medications from clinical medication endpoints', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(String(input));
        if (url.pathname === '/api/v1/clinical/medications') {
          return new Response(JSON.stringify({
            patientId: 'patient-001',
            items: [{
              id: 'rx-1',
              medicationName: 'Metformin',
              dosage: '500mg',
              indication: 'Twice daily',
              status: 'ACTIVE',
              prescribedAt: '2026-05-28T01:00:00Z',
            }],
            count: 1,
          }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        if (url.pathname === '/api/v1/clinical/medications/prescriptions/rx-1') {
          return new Response(JSON.stringify({
            id: 'rx-1',
            medicationName: 'Metformin',
            dosage: '500mg',
            indication: 'Twice daily',
            route: 'oral',
            frequency: 'twice daily',
            prescriberId: 'clinician-1',
            startDate: '2026-05-28T01:00:00Z',
            endDate: '2026-06-28T01:00:00Z',
            adherenceSource: 'not-recorded',
            warnings: ['HIGH: monitor renal function'],
            interactions: ['Metformin interaction check complete'],
            status: 'ACTIVE',
            prescribedAt: '2026-05-28T01:00:00Z',
            refillsRemaining: 0,
          }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        return new Response('Not found', { status: 404 });
      }),
    );

    await expect(fetchMedications('patient-001', {
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      role: 'patient',
    })).resolves.toEqual([
      expect.objectContaining({ id: 'rx-1', medication: 'Metformin', status: 'active' }),
    ]);

    await expect(fetchMedicationDetail('patient-001', 'rx-1', {
      tenantId: 'tenant-health-1',
      principalId: 'patient-001',
      role: 'patient',
    })).resolves.toMatchObject({
      id: 'rx-1',
      medication: 'Metformin',
      refillsRemaining: 0,
      prescribedAt: '2026-05-28T01:00:00Z',
      route: 'oral',
      frequency: 'twice daily',
      prescriberId: 'clinician-1',
      warnings: ['HIGH: monitor renal function'],
      interactions: ['Metformin interaction check complete'],
      adherenceSource: 'not-recorded',
    });
  });
});
