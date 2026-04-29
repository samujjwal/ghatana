/**
 * API envelope contracts — canonical HTTP response wrappers.
 *
 * @doc.type module
 * @doc.purpose Canonical API envelope types for HTTP responses
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

/**
 * Standard successful API response envelope.
 */
export interface ApiEnvelope<T = unknown> {
  readonly data: T;
  readonly meta?: ApiMeta;
}

/**
 * Paginated API response envelope.
 */
export interface PagedApiEnvelope<T = unknown> {
  readonly items: T[];
  readonly nextCursor: string | null;
  readonly meta?: ApiMeta;
}

/**
 * Standard error API response envelope.
 */
export interface ApiErrorEnvelope {
  readonly error: {
    readonly code: string;
    readonly message: string;
    readonly details?: unknown;
  };
}

/**
 * Metadata attached to paginated or enriched responses.
 */
export interface ApiMeta {
  readonly total?: number;
  readonly page?: number;
  readonly perPage?: number;
  readonly requestId?: string;
}

/**
 * Constructs a successful API envelope.
 */
export function ok<T>(data: T, meta?: ApiMeta): ApiEnvelope<T> {
  return meta !== undefined ? { data, meta } : { data };
}

/**
 * Constructs an error API envelope.
 */
export function err(code: string, message: string, details?: unknown): ApiErrorEnvelope {
  return { error: { code, message, ...(details !== undefined ? { details } : {}) } };
}
