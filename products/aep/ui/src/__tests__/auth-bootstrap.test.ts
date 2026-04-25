/**
 * Auth bootstrap double-read regression test.
 *
 * Guards against re-introducing the bug where response.json() is called
 * after response.text() (or vice versa), which throws because the body
 * has already been consumed.
 */
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';

describe('Auth bootstrap — response body double-read guard', () => {
  const originalFetch = global.fetch;

  afterEach(() => {
    global.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it('does not double-read a successful JSON response body', async () => {
    const jsonSpy = vi.fn().mockResolvedValue({ token: 'abc', user: { id: 'u1' } });
    const textSpy = vi.fn().mockResolvedValue('{"token":"abc"}');
    let jsonCalled = false;
    let textCalled = false;

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => {
        if (jsonCalled || textCalled) {
          throw new TypeError('body stream already read');
        }
        jsonCalled = true;
        return jsonSpy();
      },
      text: async () => {
        if (jsonCalled || textCalled) {
          throw new TypeError('body stream already read');
        }
        textCalled = true;
        return textSpy();
      },
    } as unknown as Response);

    const { apiClient } = await import('@/lib/http-client');
    const result = await apiClient.post('/api/v1/session', { code: 'test' });

    // Only one body method should be consumed per response
    expect(result).toBeDefined();
    expect(jsonCalled).toBe(true);
    expect(textCalled).toBe(false);
  });

  it('reads text() for error responses without double-reading', async () => {
    let textCalled = false;

    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => {
        throw new TypeError('body stream already read');
      },
      text: async () => {
        if (textCalled) throw new TypeError('body stream already read');
        textCalled = true;
        return '{"error":"invalid_grant"}';
      },
    } as unknown as Response);

    const { apiClient } = await import('@/lib/http-client');
    await expect(apiClient.post('/api/v1/session', { code: 'bad' })).rejects.toThrow();
    expect(textCalled).toBe(true);
  });
});
