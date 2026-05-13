import { describe, expect, it, vi } from 'vitest';
import {
  DataAccessContextBuilder,
  DataAccessContextError,
  IdempotencyError,
  createDataAccessContext,
  createIdempotencyFingerprint,
  createInMemoryIdempotencyStore,
  resolveTenantForPrincipal,
  runIdempotentMutation,
} from '../index';

describe('DataAccessContext', () => {
  it('builds an immutable Kernel context with audit, owner scope, and idempotency metadata', () => {
    const context = createDataAccessContext('tenant-1', 'principal-1', {
      correlationId: 'corr-1',
      auditClassification: 'PERSONAL_MEMORY_WRITE',
      dataOwnerScope: 'flashit:moment:m1',
      idempotencyKey: 'idem-1',
      metadata: { surface: 'web', retries: 0, highRisk: false },
      requireCorrelationId: true,
      requireAuditClassification: true,
      requireDataOwnerScope: true,
      requireIdempotencyKey: true,
    });

    expect(context.isInitialized()).toBe(true);
    expect(context.requireIdempotencyKey()).toBe('idem-1');
    expect(context.metadata).toEqual({ surface: 'web', retries: 0, highRisk: false });
    expect(Object.isFrozen(context)).toBe(true);
    expect(Object.isFrozen(context.metadata)).toBe(true);
  });

  it('fails closed when required fields are missing', () => {
    expect(() => new DataAccessContextBuilder().setPrincipalId('principal-1').build()).toThrow(
      DataAccessContextError,
    );

    expect(() =>
      createDataAccessContext('tenant-1', 'principal-1', { requireIdempotencyKey: true }),
    ).toThrow(DataAccessContextError);
  });

  it('normalizes blank optional fields away instead of exporting empty strings', () => {
    const context = createDataAccessContext('tenant-1', 'principal-1', {
      correlationId: '   ',
      requestId: '',
    });

    expect('correlationId' in context).toBe(false);
    expect('requestId' in context).toBe(false);
  });

  it('resolves personal tenant access and rejects cross-tenant access by default', () => {
    expect(resolveTenantForPrincipal({ principalId: 'user-1' })).toBe('user-1');
    expect(resolveTenantForPrincipal({ principalId: 'user-1', requestedTenantId: 'user-1' })).toBe(
      'user-1',
    );
    expect(() =>
      resolveTenantForPrincipal({ principalId: 'user-1', requestedTenantId: 'tenant-2' }),
    ).toThrow(DataAccessContextError);
  });
});

describe('idempotency contract', () => {
  it('creates stable request fingerprints independent of object key order', () => {
    expect(createIdempotencyFingerprint([{ b: 2, a: 1 }, 'POST'])).toBe(
      createIdempotencyFingerprint([{ a: 1, b: 2 }, 'POST']),
    );
  });

  it('executes once and replays the cached response for the same key and fingerprint', async () => {
    const execute = vi.fn<() => Promise<{ id: string }>>().mockResolvedValue({ id: 'moment-1' });
    const store = createInMemoryIdempotencyStore<{ id: string }>();
    const context = createDataAccessContext('tenant-1', 'principal-1', {
      correlationId: 'corr-1',
      idempotencyKey: 'idem-1',
      requireIdempotencyKey: true,
    });
    const fingerprint = createIdempotencyFingerprint(['POST', '/moments', { title: 'A' }]);

    const first = await runIdempotentMutation({
      context,
      fingerprint,
      ttlMs: 60_000,
      store,
      execute,
      nowEpochMs: () => 1_000,
    });
    const second = await runIdempotentMutation({
      context,
      fingerprint,
      ttlMs: 60_000,
      store,
      execute,
      nowEpochMs: () => 2_000,
    });

    expect(first.status).toBe('miss');
    expect(second.status).toBe('completed');
    expect(second.status === 'completed' ? second.response : undefined).toEqual({ id: 'moment-1' });
    expect(second.audit).toMatchObject({ replayed: true, expired: false, correlationId: 'corr-1' });
    expect(execute).toHaveBeenCalledTimes(1);
  });

  it('rejects same-key replays with a different request fingerprint', async () => {
    const store = createInMemoryIdempotencyStore<{ id: string }>();
    const context = createDataAccessContext('tenant-1', 'principal-1', {
      idempotencyKey: 'idem-1',
      requireIdempotencyKey: true,
    });

    await runIdempotentMutation({
      context,
      fingerprint: 'fingerprint-a',
      ttlMs: 60_000,
      store,
      execute: async () => ({ id: 'moment-1' }),
      nowEpochMs: () => 1_000,
    });

    await expect(
      runIdempotentMutation({
        context,
        fingerprint: 'fingerprint-b',
        ttlMs: 60_000,
        store,
        execute: async () => ({ id: 'moment-2' }),
        nowEpochMs: () => 2_000,
      }),
    ).rejects.toThrow(IdempotencyError);
  });

  it('expires old keys and executes the mutation again with expiry audit output', async () => {
    const execute = vi
      .fn<() => Promise<{ id: string }>>()
      .mockResolvedValueOnce({ id: 'moment-1' })
      .mockResolvedValueOnce({ id: 'moment-2' });
    const store = createInMemoryIdempotencyStore<{ id: string }>();
    const context = createDataAccessContext('tenant-1', 'principal-1', {
      idempotencyKey: 'idem-1',
      requireIdempotencyKey: true,
    });

    await runIdempotentMutation({
      context,
      fingerprint: 'fingerprint-a',
      ttlMs: 5,
      store,
      execute,
      nowEpochMs: () => 1_000,
    });
    const replayAfterExpiry = await runIdempotentMutation({
      context,
      fingerprint: 'fingerprint-a',
      ttlMs: 5,
      store,
      execute,
      nowEpochMs: () => 2_000,
    });

    expect(replayAfterExpiry.status).toBe('expired');
    expect(replayAfterExpiry.audit).toMatchObject({ replayed: false, expired: true });
    expect(execute).toHaveBeenCalledTimes(2);
  });
});
