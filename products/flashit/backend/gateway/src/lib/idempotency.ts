/**
 * Idempotency enforcement for FlashIt mutating API operations.
 *
 * Checks the audit event log for a prior completed operation carrying the
 * same (userId, eventType, idempotencyKey) triple.  When a duplicate is
 * detected the route handler returns the original response payload stored in
 * the audit event's `details.cachedResponse` field rather than re-processing
 * the request.
 *
 * @doc.type module
 * @doc.purpose Enforce at-most-once semantics for create/delete mutations via audit-log deduplication.
 * @doc.layer product
 * @doc.pattern Middleware
 */

import { prisma } from './prisma.js';
import type { AuditEventType } from '../../generated/prisma/index.js';

export interface IdempotencyMatch<T> {
  readonly found: true;
  readonly cachedResponse: T;
}

export interface IdempotencyMiss {
  readonly found: false;
}

export type IdempotencyResult<T> = IdempotencyMatch<T> | IdempotencyMiss;

/**
 * Looks up an earlier completed operation for the given
 * (userId, eventType, idempotencyKey) triple.
 *
 * Returns the cached response payload when a match is found, or
 * `{ found: false }` when the key is new and processing should proceed.
 */
export async function checkIdempotency<T>(
  userId: string,
  eventType: AuditEventType,
  idempotencyKey: string,
): Promise<IdempotencyResult<T>> {
  // details is stored as JSONB.  Prisma's JsonFilter lets us query nested
  // fields without a raw SQL round-trip.
  const existingEvent = await prisma.auditEvent.findFirst({
    where: {
      userId,
      eventType,
      details: {
        path: ['idempotencyKey'],
        equals: idempotencyKey,
      },
    },
    select: {
      details: true,
    },
    orderBy: { createdAt: 'asc' },
  });

  if (!existingEvent) {
    return { found: false };
  }

  const details = existingEvent.details as Record<string, unknown> | null;
  if (!details || typeof details !== 'object') {
    return { found: false };
  }

  const cached = details['cachedResponse'] as T | undefined;
  if (cached === undefined) {
    // The audit event exists but was not created with a cachedResponse
    // (pre-idempotency backfill scenario).  Treat as a duplicate to avoid
    // double processing even if we cannot return the original payload.
    return { found: true, cachedResponse: cached as T };
  }

  return { found: true, cachedResponse: cached };
}
