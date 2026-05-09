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
  ApiError,
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
  setRequestContext('tenant-default', 'principal-default', 'session-default', [], []);
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
    setRequestContext('tenant-abc', 'user-xyz', 'session-1', [], []);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Tenant-ID')).toBe('tenant-abc');
    expect(getHeader(init, 'X-Principal-ID')).toBe('user-xyz');
  });

  it('sends X-Roles as comma-separated list', async () => {
    setRequestContext('t1', 'u1', 'session-1', ['admin', 'approver'], []);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Roles')).toBe('admin,approver');
  });

  it('sends X-Permissions as comma-separated list', async () => {
    setRequestContext('t1', 'u1', 'session-1', [], ['read:approvals', 'write:approvals']);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Permissions')).toBe('read:approvals,write:approvals');
  });

  it('does NOT send X-Roles or X-Permissions when empty', async () => {
    setRequestContext('t1', 'u1', 'session-1', [], []);
    mockFetch.mockResolvedValue(okResponse());
    await apiRequest('/v1/test');
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(getHeader(init, 'X-Roles')).toBeNull();
    expect(getHeader(init, 'X-Permissions')).toBeNull();
  });

  it('throws when request context is cleared', async () => {
    clearRequestContext();
    mockFetch.mockResolvedValue(okResponse());
    await expect(apiRequest('/v1/test')).rejects.toThrow('X-Tenant-ID is required but not set in request context');
  });

  it('throws ApiError on non-ok response', async () => {
    mockFetch.mockResolvedValue(new Response('Not Found', { status: 404 }));
    await expect(apiRequest('/v1/missing')).rejects.toThrow('API error 404');
  });

  it('parses canonical DMOS error envelope safely', async () => {
    const body = JSON.stringify({
      error: 'LOCKED',
      message: 'Connector is disabled for this workspace.',
      status: 423,
      correlationId: 'corr-locked-1',
      details: { connector: 'google-ads' },
    });
    mockFetch.mockResolvedValue(new Response(body, {
      status: 423,
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-ID': 'corr-header-fallback',
      },
    }));

    await expect(apiRequest('/v1/locked')).rejects.toMatchObject({
      name: 'ApiError',
      status: 423,
      code: 'LOCKED',
      correlationId: 'corr-locked-1',
    });
  });

  it('returns user-safe messages across canonical status matrix', () => {
    const matrix = [
      { status: 400, code: 'BAD_REQUEST', message: 'Invalid field', expectContains: 'Invalid field' },
      { status: 401, code: 'UNAUTHORIZED', message: 'Auth required', expectContains: 'session has expired' },
      { status: 403, code: 'FORBIDDEN', message: 'Denied', expectContains: 'do not have permission' },
      { status: 404, code: 'NOT_FOUND', message: 'Not found', expectContains: 'requested resource was not found' },
      { status: 409, code: 'CONFLICT', message: 'Conflict', expectContains: 'conflicts with the current state' },
      { status: 422, code: 'UNPROCESSABLE_ENTITY', message: 'Policy violation', expectContains: 'Policy violation' },
      { status: 423, code: 'LOCKED', message: 'Feature disabled', expectContains: 'Feature disabled' },
      { status: 429, code: 'RATE_LIMITED', message: 'Slow down', expectContains: 'Too many requests' },
      { status: 500, code: 'INTERNAL_ERROR', message: 'Internal error', expectContains: 'Server error' },
    ] as const;

    for (const sample of matrix) {
      const body = JSON.stringify({
        error: sample.code,
        message: sample.message,
        status: sample.status,
        correlationId: 'corr-matrix',
        details: {},
      });
      const err = new ApiError('fallback', sample.status, 'corr-header', body);
      expect(err.getUserMessage().toLowerCase()).toContain(sample.expectContains.toLowerCase());
    }
  });
});
