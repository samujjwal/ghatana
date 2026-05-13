import { describe, expect, it } from 'vitest';
import { validateIdempotencyObservations } from '../index';

const completeObservations = [
  {
    operation: 'flashit_moment_create',
    key: 'idem-1',
    fingerprint: 'fingerprint-a',
    status: 'miss',
    replayed: false,
    expired: false,
    principalId: 'principal-1',
    tenantId: 'tenant-1',
    correlationId: 'corr-1',
  },
  {
    operation: 'flashit_moment_create',
    key: 'idem-1',
    fingerprint: 'fingerprint-a',
    status: 'completed',
    replayed: true,
    expired: false,
    principalId: 'principal-1',
    tenantId: 'tenant-1',
    correlationId: 'corr-1',
  },
  {
    operation: 'flashit_moment_create',
    key: 'idem-expired',
    fingerprint: 'fingerprint-a',
    status: 'expired',
    replayed: false,
    expired: true,
    principalId: 'principal-1',
    tenantId: 'tenant-1',
    correlationId: 'corr-1',
  },
  {
    operation: 'flashit_moment_create',
    key: 'idem-conflict',
    fingerprint: 'fingerprint-b',
    status: 'conflict',
    replayed: false,
    expired: false,
    principalId: 'principal-1',
    tenantId: 'tenant-1',
    correlationId: 'corr-1',
  },
] as const;

describe('idempotency observation validator', () => {
  it('accepts observations that prove miss, replay, expiry, and conflict paths', () => {
    const result = validateIdempotencyObservations(completeObservations, {
      requireCorrelationId: true,
      requireReplayObservation: true,
      requireExpiredObservation: true,
      requireConflictObservation: true,
    });

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.observations).toHaveLength(4);
  });

  it('reports malformed observations without throwing', () => {
    const result = validateIdempotencyObservations([
      {
        operation: '',
        key: '',
        fingerprint: '',
        status: 'completed',
        replayed: false,
        expired: 'nope',
        principalId: '',
        tenantId: '',
      },
    ]);

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('idempotency[0].operation must be a non-empty string');
    expect(result.errors).toContain('idempotency[0].key must be a non-empty string');
    expect(result.errors).toContain('idempotency[0].expired must be boolean');
    expect(result.errors).toContain('idempotency[0].completed observation must set replayed=true');
  });

  it('can require behavioral coverage for replay, expiry, and conflicts', () => {
    const result = validateIdempotencyObservations([completeObservations[0]], {
      requireReplayObservation: true,
      requireExpiredObservation: true,
      requireConflictObservation: true,
    });

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('idempotency observations must include a completed replay');
    expect(result.errors).toContain('idempotency observations must include an expired-key execution');
    expect(result.errors).toContain('idempotency observations must include a same-key fingerprint conflict');
  });
});
