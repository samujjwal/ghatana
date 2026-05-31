import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchRecordDetail } from '../recordsApi';

describe('recordsApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses the backend record detail DTO without inferring title or fetching FHIR separately', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> => (
      new Response(JSON.stringify({
        record: {
          id: 'rec-1',
          title: 'Backend authored discharge summary',
          category: 'administrative',
          updatedAt: '2026-05-31T16:00:00Z',
          resourceType: 'Patient',
        },
        fhirJson: '{"resourceType":"Patient","id":"patient-42"}',
        accessAudit: {
          accessedAt: '2026-05-31T16:01:00Z',
          accessedBy: 'patient-42',
          correlationId: 'server-correlation-123',
          requiresAudit: true,
        },
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    ));
    vi.stubGlobal('fetch', fetchMock);

    const detail = await fetchRecordDetail('patient-42', 'rec-1', {
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    });

    expect(detail.record.title).toBe('Backend authored discharge summary');
    expect(detail.record.category).toBe('administrative');
    expect(detail.fhirJson).toBe('{"resourceType":"Patient","id":"patient-42"}');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/v1/records/rec-1?patientId=patient-42');
  });
});
