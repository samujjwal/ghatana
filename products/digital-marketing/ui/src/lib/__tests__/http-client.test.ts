/**
 * http-client unit tests.
 *
 * @doc.type test
 * @doc.purpose Verify auth token and request-context header injection
 * @doc.layer frontend
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  apiRequest,
  clearAuthToken,
  clearRequestContext,
  setAuthToken,
  setRequestContext,
} from '../http-client';

const mockFetch = vi.fn();

beforeEach(() => {
  mockFetch.mockClear();
  vi.stubGlobal('fetch', mockFetch);
  vi.stubGlobal('crypto', {
    randomUUID: () => 'test-correlation-id',
  });
  clearAuthToken();
  clearRequestContext();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function okResponse(body = '{}'): Response {
  return new Response(body, { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function getHeader(init: RequestInit, name: string): string | null {
  const h = init.headers;
  if (!h) return null;
  if (h instanceof Headers) return h.get(name);
  const key = Object.keys(h as Record<string, string>).find(
    (k) => k.toLowerCase() === name.toLowerCase(),
  );
  return key !== undefined ? (h as Record<string, string>)[key] : null;
}

describe('apiRequest — header injection', () => {
  it('sends Content-Type and X-Correlation-ID on every request', async () => {
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'Content-Type')).toBe('application/json');
    expect(getHeader(init, 'X-Correlation-ID')).toBe('test-correlation-id');
  });

  it('sends Authorization header when auth token is set', async () => {
    setAuthToken('my-jwt');
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'Authorization')).toBe('Bearer my-jwt');
  });

  it('does NOT send Authorization when no token', async () => {
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'Authorization')).toBeNull();
  });

  it('sends X-Tenant-ID and X-Principal-ID when context is set', async () => {
    setRequestContext('tenant-abc', 'user-xyz', [], []);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Tenant-ID')).toBe('tenant-abc');
    expect(getHeader(init, 'X-Principal-ID')).toBe('user-xyz');
  });

  it('sends X-Roles as comma-separated list', async () => {
    setRequestContext('t1', 'u1', ['admin', 'approver'], []);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Roles')).toBe('admin,approver');
  });

  it('sends X-Permissions as comma-separated list', async () => {
    setRequestContext('t1', 'u1', [], ['read:approvals', 'write:approvals']);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Permissions')).toBe('read:approvals,write:approvals');
  });

  it('does NOT send X-Roles or X-Permissions when empty', async () => {
    setRequestContext('t1', 'u1', [], []);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Roles')).toBeNull();
    expect(getHeader(init, 'X-Permissions')).toBeNull();
  });

  it('clears context headers after clearRequestContext()', async () => {
    setRequestContext('t1', 'u1', ['admin'], []);
    clearRequestContext();
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Tenant-ID')).toBeNull();
    expect(getHeader(init, 'X-Principal-ID')).toBeNull();
    expect(getHeader(init, 'X-Roles')).toBeNull();
  });

  it('throws ApiError on non-ok response', async () => {
    mockFetch.mockResolvedValue(new Response('Not Found', { status: 404 }));
    await expect(apiRequest('/v1/missing')).rejects.toThrow('API error 404');
  });
});
