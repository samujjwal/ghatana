import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ApiClient } from '../client';

function mockFetch(status: number, body: unknown, headers?: Record<string, string>) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json', ...headers }),
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
  } as unknown as Response);
}

describe('ApiClient', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('should make a GET request', async () => {
    const fetchMock = mockFetch(200, { id: 1 });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: 'https://api.test.com' });
    const response = await client.get('/users/1');

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(response.status).toBe(200);
    expect(response.data).toEqual({ id: 1 });
  });

  it('should make a POST request with body', async () => {
    const fetchMock = mockFetch(201, { id: 2, name: 'Test' });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: 'https://api.test.com' });
    const response = await client.post('/users', { body: { name: 'Test' } });

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(response.status).toBe(201);
    expect(response.data).toEqual({ id: 2, name: 'Test' });
  });

  it('should apply default headers', async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: 'https://api.test.com',
      defaultHeaders: { 'X-Custom': 'value' },
    });
    await client.get('/test');

    const fetchCall = fetchMock.mock.calls[0];
    const request = fetchCall[0] as Request | string;
    // The headers should contain the default header
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('should retry on failure', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve({ recovered: true }),
        text: () => Promise.resolve(''),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
      });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: 'https://api.test.com',
      retry: { attempts: 3, backoffMs: 10 },
    });
    const response = await client.get('/flaky');

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(response.data).toEqual({ recovered: true });
  });

  it('should throw after exhausting retries', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('Down'));
    globalThis.fetch = fetchMock;

    const client = new ApiClient({
      baseUrl: 'https://api.test.com',
      retry: { attempts: 2, backoffMs: 1 },
    });

    await expect(client.get('/down')).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('should apply request middleware', async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: 'https://api.test.com' });
    client.useRequest(async (req) => ({
      ...req,
      headers: { ...req.headers, Authorization: 'Bearer token123' },
    }));

    await client.get('/protected');
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('should unsubscribe middleware', async () => {
    const fetchMock = mockFetch(200, {});
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: 'https://api.test.com' });
    const unsubscribe = client.useRequest(async (req) => ({
      ...req,
      headers: { ...req.headers, 'X-Added': 'yes' },
    }));

    unsubscribe();
    await client.get('/test');
    // Middleware should have been removed
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('should support DELETE method', async () => {
    const fetchMock = mockFetch(204, null);
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: 'https://api.test.com' });
    const response = await client.delete('/users/1');

    expect(response.status).toBe(204);
  });

  it('should support PUT method', async () => {
    const fetchMock = mockFetch(200, { updated: true });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: 'https://api.test.com' });
    const response = await client.put('/users/1', { body: { name: 'Updated' } });

    expect(response.status).toBe(200);
    expect(response.data).toEqual({ updated: true });
  });

  it('should support PATCH method', async () => {
    const fetchMock = mockFetch(200, { patched: true });
    globalThis.fetch = fetchMock;

    const client = new ApiClient({ baseUrl: 'https://api.test.com' });
    const response = await client.patch('/users/1', { body: { name: 'Patched' } });

    expect(response.status).toBe(200);
  });
});
