import { afterEach, describe, expect, it, vi } from 'vitest';
import { phrFetch, PhrApiError } from '../requestApi';

describe('phrFetch', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses the backend response correlation ID for safe API errors', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> => (
      new Response(JSON.stringify({ error: 'Denied', code: 'PHR_POLICY_DENIED' }), {
        status: 403,
        headers: {
          'Content-Type': 'application/json',
          'X-Correlation-ID': 'server-correlation-123',
        },
      })
    ));
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('client-correlation-456' as ReturnType<typeof crypto.randomUUID>);

    await expect(phrFetch('/api/v1/records', { retry: false })).rejects.toMatchObject({
      name: 'PhrApiError',
      statusCode: 403,
      correlationId: 'server-correlation-123',
      error: 'Denied',
      code: 'PHR_POLICY_DENIED',
    } satisfies Partial<PhrApiError>);

    const request = fetchMock.mock.calls[0]?.[1];
    expect(request?.headers).toMatchObject({
      'X-Correlation-ID': 'client-correlation-456',
    });
  });
});
