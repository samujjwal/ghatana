/**
 * Mobile header builder for PHR API requests.
 *
 * This module provides a single mobile header builder that mirrors the web's
 * buildPhrHeaders function, ensuring consistency across platforms.
 *
 * @doc.type module
 * @doc.purpose Build headers for mobile PHR API requests
 * @doc.layer mobile
 */

import type { MobileSession } from '../types';

export type MobileSessionContext = Partial<
  Pick<MobileSession, 'tenantId' | 'principalId' | 'role' | 'persona' | 'tier'>
> & {
  correlationId?: string;
  idempotencyKey?: string;
  facilityId?: string;
};

function newCorrelationId(): string {
  return crypto.randomUUID();
}

/**
 * Builds headers for mobile PHR API requests.
 *
 * @param context - Session context for the request
 * @returns Headers object with all required PHR headers
 */
export function buildMobileHeaders(context: MobileSessionContext = {}): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-ID': context.correlationId ?? newCorrelationId(),
  };

  if (context.tenantId) {
    headers['X-Tenant-Id'] = context.tenantId;
  }
  if (context.principalId) {
    headers['X-Principal-Id'] = context.principalId;
  }
  if (context.role) {
    headers['X-Role'] = context.role;
  }
  if (context.persona) {
    headers['X-Persona'] = context.persona;
  }
  if (context.tier) {
    headers['X-Tier'] = context.tier;
  }
  if (context.facilityId) {
    headers['X-Facility-Id'] = context.facilityId;
  }
  if (context.idempotencyKey) {
    headers['X-Idempotency-Key'] = context.idempotencyKey;
  }

  return headers;
}

/**
 * Adds idempotency key to a session context.
 *
 * @param context - Session context
 * @returns Session context with idempotency key
 */
export function withMobileIdempotency<T extends MobileSessionContext>(
  context: T
): T & { idempotencyKey: string } {
  return {
    ...context,
    idempotencyKey: context.idempotencyKey ?? newCorrelationId(),
  };
}
