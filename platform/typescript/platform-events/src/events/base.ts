/**
 * @fileoverview Base event types and branded identifiers for the platform event system.
 */

/** Branded type for correlation IDs to prevent accidental mixing with plain strings. */
export type CorrelationId = string & { readonly brand: unique symbol };

/** Branded type for session IDs. */
export type SessionId = string & { readonly brand: unique symbol };

/** Valid event sources in the platform. */
export type EventSource =
  | 'canvas'
  | 'builder'
  | 'design-system'
  | 'product'
  | 'platform-events';

/** Base interface for all platform events. */
export interface PlatformEvent<T = Record<string, unknown>> {
  readonly name: string;
  readonly correlationId: CorrelationId;
  readonly sessionId: SessionId;
  readonly timestamp: string; // ISO 8601
  readonly source: EventSource;
  readonly actor: 'user' | 'ai' | 'system';
  readonly triggeredBy: 'explicit' | 'implicit';
  readonly payload: T;
}

/** Creates a branded correlation ID from a string. */
export function createCorrelationId(id: string): CorrelationId {
  return id as CorrelationId;
}

/** Creates a branded session ID from a string. */
export function createSessionId(id: string): SessionId {
  return id as SessionId;
}

/** Validates that a string is a valid correlation ID format (UUID v4). */
export function isValidCorrelationId(id: string): boolean {
  const uuidV4Regex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  return uuidV4Regex.test(id);
}

/** Validates that a string is a valid session ID format. */
export function isValidSessionId(id: string): boolean {
  // Session IDs can be UUIDs or structured session identifiers
  const sessionIdRegex = /^[a-zA-Z0-9_-]{8,64}$/;
  return sessionIdRegex.test(id);
}

/** Creates a new platform event with required metadata. */
export function createPlatformEvent<T>(
  name: string,
  payload: T,
  options: {
    correlationId?: CorrelationId;
    sessionId?: SessionId;
    source?: EventSource;
    actor?: 'user' | 'ai' | 'system';
    triggeredBy?: 'explicit' | 'implicit';
  } = {}
): PlatformEvent<T> {
  const timestamp = new Date().toISOString();
  const correlationId = options.correlationId ?? (generateUUID() as CorrelationId);
  const sessionId = options.sessionId ?? (generateSessionId() as SessionId);

  return {
    name,
    correlationId,
    sessionId,
    timestamp,
    source: options.source ?? 'platform-events',
    actor: options.actor ?? 'system',
    triggeredBy: options.triggeredBy ?? 'explicit',
    payload,
  };
}

/** Generates a UUID v4. */
function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Generates a session ID. */
function generateSessionId(): string {
  return `sess_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
}
