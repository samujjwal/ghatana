/**
 * Behavioral tests for FlashIt idempotency enforcement.
 *
 * The idempotency module queries the AuditEvent table via Prisma.
 * Prisma is mocked at the module boundary so the test exercises the REAL
 * checkIdempotency logic — not a hand-crafted object literal.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock Prisma before importing the module under test
vi.mock('../../prisma.js', () => ({
  prisma: {
    auditEvent: {
      findFirst: vi.fn(),
    },
  },
}));

import { checkIdempotency } from '../../idempotency.js';
import { prisma } from '../../prisma.js';
import type { AuditEventType } from '../../../../generated/prisma/index.js';

const mockAuditEventFindFirst = vi.mocked(prisma.auditEvent.findFirst);

beforeEach(() => {
  vi.clearAllMocks();
});

const EVENT_TYPE: AuditEventType = 'MOMENT_CREATED';

describe('checkIdempotency', () => {
  it('returns found:false when no matching audit event exists', async () => {
    mockAuditEventFindFirst.mockResolvedValueOnce(null);

    const result = await checkIdempotency('user-1', EVENT_TYPE, 'key-new');

    expect(result.found).toBe(false);
    expect(mockAuditEventFindFirst).toHaveBeenCalledOnce();
    expect(mockAuditEventFindFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          userId: 'user-1',
          eventType: EVENT_TYPE,
          details: { path: ['idempotencyKey'], equals: 'key-new' },
        }),
      }),
    );
  });

  it('returns found:true with cachedResponse when duplicate audit event exists', async () => {
    const cachedMoment = { id: 'moment-1', contentText: 'hello' };
    mockAuditEventFindFirst.mockResolvedValueOnce({
      details: {
        idempotencyKey: 'key-dup',
        cachedResponse: { moment: cachedMoment },
      },
    } as never);

    const result = await checkIdempotency<{ moment: { id: string; contentText: string } }>(
      'user-1',
      EVENT_TYPE,
      'key-dup',
    );

    expect(result.found).toBe(true);
    if (result.found) {
      expect(result.cachedResponse).toEqual({ moment: cachedMoment });
    }
  });

  it('returns found:true with undefined cachedResponse for pre-idempotency audit events', async () => {
    // Audit event exists but has no cachedResponse (legacy event without the field)
    mockAuditEventFindFirst.mockResolvedValueOnce({
      details: { idempotencyKey: 'key-old', tenantId: 'tenant-1' },
    } as never);

    const result = await checkIdempotency('user-1', EVENT_TYPE, 'key-old');

    expect(result.found).toBe(true);
    if (result.found) {
      // cachedResponse is undefined — caller must handle gracefully
      expect(result.cachedResponse).toBeUndefined();
    }
  });

  it('returns found:false when audit event details is null', async () => {
    mockAuditEventFindFirst.mockResolvedValueOnce({
      details: null,
    } as never);

    const result = await checkIdempotency('user-1', EVENT_TYPE, 'key-null-details');

    expect(result.found).toBe(false);
  });

  it('passes userId, eventType, and idempotencyKey to the Prisma query', async () => {
    mockAuditEventFindFirst.mockResolvedValueOnce(null);

    await checkIdempotency('user-specific', 'MOMENT_DELETED' as AuditEventType, 'delete-key-1');

    expect(mockAuditEventFindFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          userId: 'user-specific',
          eventType: 'MOMENT_DELETED',
          details: { path: ['idempotencyKey'], equals: 'delete-key-1' },
        },
      }),
    );
  });
});
