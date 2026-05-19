import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchDashboardData } from '../api/phrApi';

function bundle(resource: Record<string, unknown>) {
  return {
    resourceType: 'Bundle',
    type: 'searchset',
    entry: [{ resource }],
  };
}

describe('PHR API integration mapping', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('maps FHIR resources into the dashboard contract', async () => {
    const responses = new Map<string, unknown>([
      [
        '/fhir/Patient/current',
        {
          resourceType: 'Patient',
          id: 'patient-001',
          name: [{ given: ['Aarati'], family: 'Shrestha' }],
          birthDate: '1984-01-01',
          address: [{ city: 'Kathmandu' }],
          telecom: [{ value: 'Sushil Shrestha' }],
          extension: [{ url: 'http://example.test/blood-type', valueString: 'O+' }],
        },
      ],
      [
        '/fhir/Observation',
        bundle({
          resourceType: 'Observation',
          id: 'obs-001',
          status: 'final',
          code: { text: 'HbA1c' },
          effectiveDateTime: '2026-05-01T00:00:00Z',
          valueQuantity: { value: 6.9, unit: '%' },
          interpretation: [{ coding: [{ code: 'N' }] }],
        }),
      ],
      [
        '/fhir/MedicationRequest',
        bundle({
          resourceType: 'MedicationRequest',
          id: 'med-001',
          status: 'active',
          medicationCodeableConcept: { text: 'Metformin 500mg' },
          dosageInstruction: [{ text: 'BID' }],
          authoredOn: '2026-04-15',
        }),
      ],
      [
        '/fhir/Consent',
        bundle({
          resourceType: 'Consent',
          id: 'consent-001',
          status: 'active',
          organization: [{ display: 'Nepal HIE' }],
          purpose: [{ display: 'Care coordination' }],
          provision: { period: { end: '2026-12-31' } },
        }),
      ],
      [
        '/fhir/Appointment',
        bundle({
          resourceType: 'Appointment',
          id: 'appt-001',
          status: 'booked',
          start: '2026-05-20T09:00:00Z',
          specialty: [{ text: 'Endocrinology' }],
          participant: [{ actor: { display: 'Dr. Koirala' } }],
          comment: 'Kathmandu Clinic',
        }),
      ],
    ]);

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(String(input));
        const body = responses.get(url.pathname);

        if (!body) {
          return new Response('Not found', { status: 404 });
        }

        return new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'Content-Type': 'application/fhir+json' },
        });
      }),
    );

    const dashboard = await fetchDashboardData();

    expect(dashboard.patient).toMatchObject({
      id: 'patient-001',
      name: 'Aarati Shrestha',
      bloodType: 'O+',
      location: 'Kathmandu',
      emergencyContact: 'Sushil Shrestha',
    });
    expect(dashboard.labs).toEqual([
      expect.objectContaining({ id: 'obs-001', name: 'HbA1c', value: '6.9 %', status: 'normal' }),
    ]);
    expect(dashboard.medications).toEqual([
      expect.objectContaining({ id: 'med-001', medication: 'Metformin', dosage: '500mg', schedule: 'BID' }),
    ]);
    expect(dashboard.consents).toEqual([
      expect.objectContaining({ id: 'consent-001', recipient: 'Nepal HIE', purpose: 'Care coordination' }),
    ]);
    expect(dashboard.appointments).toEqual([
      expect.objectContaining({ id: 'appt-001', provider: 'Dr. Koirala', specialty: 'Endocrinology' }),
    ]);
    expect(dashboard.records).toHaveLength(2);
  });
});
