/**
 * Behavioral tests for FlashIt data-access-context contract.
 *
 * Tests exercise the REAL production module — no object-literal assertions,
 * no mocking of the module under test.
 */

import { describe, it, expect } from 'vitest';
import {
  buildFlashItDataAccessContext,
  FlashItDataAccessContextError,
} from '../../lib/data-access-context.js';
import type { FastifyRequest } from 'fastify';

function makeRequest(overrides: {
  userId?: string;
  headers?: Record<string, string>;
}): FastifyRequest {
  return {
    user: { userId: overrides.userId ?? 'user-abc-123' },
    headers: overrides.headers ?? {},
  } as unknown as FastifyRequest;
}

describe('buildFlashItDataAccessContext', () => {
  it('populates principalId from authenticated user', () => {
    const request = makeRequest({ userId: 'principal-xyz' });
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'PERSONAL_MEMORY_READ',
      dataOwnerScope: 'sphere:s1',
    });
    expect(ctx.principalId).toBe('principal-xyz');
  });

  it('falls back tenantId to principalId when x-tenant-id header is absent', () => {
    const request = makeRequest({ userId: 'user-1' });
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'SPHERE_ACCESS_READ',
      dataOwnerScope: 'sphere:s2',
    });
    expect(ctx.tenantId).toBe('user-1');
  });

  it('uses x-tenant-id header when present', () => {
    const request = makeRequest({
      userId: 'user-1',
      headers: { 'x-tenant-id': 'tenant-abc' },
    });
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'SPHERE_ACCESS_READ',
      dataOwnerScope: 'sphere:s2',
    });
    expect(ctx.tenantId).toBe('tenant-abc');
  });

  it('generates correlationId when x-correlation-id header is absent', () => {
    const request = makeRequest({});
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'PERSONAL_MEMORY_READ',
      dataOwnerScope: 'sphere:s3',
    });
    expect(ctx.correlationId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
  });

  it('propagates x-correlation-id header value', () => {
    const request = makeRequest({
      headers: { 'x-correlation-id': 'corr-999' },
    });
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'PERSONAL_MEMORY_READ',
      dataOwnerScope: 'sphere:s3',
    });
    expect(ctx.correlationId).toBe('corr-999');
  });

  it('includes idempotencyKey from x-idempotency-key header', () => {
    const request = makeRequest({
      headers: { 'x-idempotency-key': 'idem-key-42' },
    });
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'PERSONAL_MEMORY_WRITE',
      dataOwnerScope: 'sphere:s4',
    });
    expect(ctx.idempotencyKey).toBe('idem-key-42');
  });

  it('throws FlashItDataAccessContextError when requireIdempotencyKey is true and key is absent', () => {
    const request = makeRequest({});
    expect(() =>
      buildFlashItDataAccessContext(request, {
        auditClassification: 'PERSONAL_MEMORY_WRITE',
        dataOwnerScope: 'sphere:s5',
        requireIdempotencyKey: true,
      }),
    ).toThrow(FlashItDataAccessContextError);
  });

  it('does not throw when requireIdempotencyKey is false and key is absent', () => {
    const request = makeRequest({});
    expect(() =>
      buildFlashItDataAccessContext(request, {
        auditClassification: 'PERSONAL_MEMORY_WRITE',
        dataOwnerScope: 'sphere:s6',
        requireIdempotencyKey: false,
      }),
    ).not.toThrow();
  });

  it('does not expose idempotencyKey in context when it is absent', () => {
    const request = makeRequest({});
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'PERSONAL_MEMORY_READ',
      dataOwnerScope: 'sphere:s7',
    });
    expect(Object.prototype.hasOwnProperty.call(ctx, 'idempotencyKey')).toBe(false);
  });

  it('sets auditClassification and dataOwnerScope from options', () => {
    const request = makeRequest({});
    const ctx = buildFlashItDataAccessContext(request, {
      auditClassification: 'SEARCH_ACTIVITY_READ',
      dataOwnerScope: 'sphere:my-sphere',
    });
    expect(ctx.auditClassification).toBe('SEARCH_ACTIVITY_READ');
    expect(ctx.dataOwnerScope).toBe('sphere:my-sphere');
  });
});
