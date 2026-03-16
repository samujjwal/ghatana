// ---------------------------------------------------------------------------
// Processing lifecycle
// ---------------------------------------------------------------------------

/** Lifecycle status of an asynchronous processing job. */
export type ProcessingStatus =
  | 'queued'
  | 'processing'
  | 'completed'
  | 'failed'
  | 'cancelled';

/** Machine-readable error codes surfaced by audio-video service methods. */
export type ServiceErrorCode =
  | 'INVALID_INPUT'
  | 'UNSUPPORTED_FORMAT'
  | 'LANGUAGE_NOT_SUPPORTED'
  | 'VOICE_NOT_FOUND'
  | 'QUOTA_EXCEEDED'
  | 'SERVICE_UNAVAILABLE'
  | 'TIMEOUT'
  | 'INTERNAL_ERROR'
  | 'UNAUTHORIZED'
  | 'PAYLOAD_TOO_LARGE';

/** A structured error that all audio-video service clients may throw. */
export class AudioVideoServiceError extends Error {
  constructor(
    readonly code: ServiceErrorCode,
    message: string,
    readonly httpStatus?: number,
    readonly traceId?: string,
  ) {
    super(message);
    this.name = 'AudioVideoServiceError';
  }
}

// ---------------------------------------------------------------------------
// Health check
// ---------------------------------------------------------------------------

export type HealthStatus = 'healthy' | 'degraded' | 'unhealthy';

/** Live health snapshot for a single audio-video micro-service. */
export interface ServiceHealth {
  readonly service: string;
  readonly status: HealthStatus;
  readonly version: string;
  readonly uptimeSeconds: number;
  readonly activeRequests: number;
  readonly checkedAt: string; // ISO-8601
  readonly details?: Readonly<Record<string, unknown>>;
}

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

/** A paginated wrapper for list responses. */
export interface PaginatedResponse<T> {
  readonly items: readonly T[];
  readonly total: number;
  readonly page: number;
  readonly pageSize: number;
  readonly hasNextPage: boolean;
}

// ---------------------------------------------------------------------------
// Job tracking (async long-running requests)
// ---------------------------------------------------------------------------

/**
 * Returned immediately for requests that are processed asynchronously.
 * Poll `GET /jobs/{jobId}` to retrieve the outcome.
 */
export interface AsyncJob {
  readonly jobId: string;
  readonly status: ProcessingStatus;
  readonly createdAt: string;
  readonly estimatedCompletionAt?: string;
}

/** Progress update event emitted during a streaming or long-running operation. */
export interface ProgressEvent {
  readonly jobId?: string;
  readonly progressPercent: number;
  readonly message?: string;
  readonly timestampMs: number;
}
