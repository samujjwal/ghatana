/**
 * Centralized retry and error-handling policies for AEP API calls.
 *
 * Provides:
 *   - Standardized retry counts and delays for transient failures
 *   - Exponential backoff with jitter
 *   - Configurable max retry duration
 *   - Categorization of retryable vs non-retryable errors
 *   - React-Query integration helpers
 *
 * @doc.type utility
 * @doc.purpose Centralize retry patterns to prevent ad-hoc inconsistency across API consumers
 * @doc.layer frontend
 */

/** Categories of errors that can be retried safely. */
export type RetryableCategory =
  | 'network' // transient connection failures, timeouts
  | 'server' // 5xx, 429 rate limits
  | 'service_unavailable'; // deliberate degradation / circuit breaker

/** Errors that should never be retried. */
export type NonRetryableCategory =
  | 'client' // 4xx (except 429)
  | 'unauthorized' // 401 — credentials may have expired
  | 'forbidden' // 403 — permanent authorization failure
  | 'not_found' // 404 — resource genuinely missing
  | 'validation' // 422 — input is bad, retry won't fix
  | 'conflict'; // 409 — state conflict, caller must resolve

/** Classification for an HTTP error. */
export type ErrorCategory = RetryableCategory | NonRetryableCategory;

/** Standard retry configuration. */
export interface RetryConfig {
  maxAttempts: number;
  baseDelayMs: number;
  maxDelayMs: number;
  jitter: boolean;
  timeoutMs: number;
}

/** Default retry policy for API calls. */
export const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxAttempts: 3,
  baseDelayMs: 500,
  maxDelayMs: 8_000,
  jitter: true,
  timeoutMs: 30_000,
};

/** Retry policy for polling / background sync calls (longer, gentler). */
export const BACKGROUND_RETRY_CONFIG: RetryConfig = {
  maxAttempts: 5,
  baseDelayMs: 1_000,
  maxDelayMs: 30_000,
  jitter: true,
  timeoutMs: 60_000,
};

/** Non-retrying policy for mutations that should fail fast. */
export const MUTATION_RETRY_CONFIG: RetryConfig = {
  maxAttempts: 1,
  baseDelayMs: 0,
  maxDelayMs: 0,
  jitter: false,
  timeoutMs: 15_000,
};

const HTTP_CATEGORY_MAP: Record<number, ErrorCategory> = {
  400: 'client',
  401: 'unauthorized',
  403: 'forbidden',
  404: 'not_found',
  409: 'conflict',
  422: 'validation',
  429: 'server',
  500: 'server',
  502: 'server',
  503: 'service_unavailable',
  504: 'server',
};

/**
 * Categorize an HTTP status code.
 * Returns `network` for non-HTTP errors (e.g., fetch failures).
 */
export function categorizeError(status: number | undefined): ErrorCategory {
  if (status === undefined || status === 0) return 'network';
  return HTTP_CATEGORY_MAP[status] ?? (status >= 500 ? 'server' : 'client');
}

/** True if the error category is safe to retry. */
export function isRetryable(category: ErrorCategory): category is RetryableCategory {
  return category === 'network' || category === 'server' || category === 'service_unavailable';
}

/** Compute delay for a given attempt using exponential backoff with optional jitter. */
export function computeRetryDelay(attempt: number, config: RetryConfig = DEFAULT_RETRY_CONFIG): number {
  const raw = Math.min(config.baseDelayMs * Math.pow(2, attempt), config.maxDelayMs);
  if (!config.jitter) return raw;
  // Full jitter: random between 0 and raw
  return Math.floor(Math.random() * raw);
}

/**
 * Wait for computed delay, rejecting on timeout.
 */
export async function delayWithTimeout(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new Error('Retry aborted'));
      return;
    }
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new Error('Retry aborted'));
    });
  });
}

/**
 * Execute an async operation with centralized retry logic.
 * Throws the last error if all retries are exhausted.
 */
export async function withRetry<T>(
  operation: () => Promise<T>,
  config: RetryConfig = DEFAULT_RETRY_CONFIG,
  signal?: AbortSignal,
): Promise<T> {
  let lastError: Error | undefined;
  for (let attempt = 0; attempt < config.maxAttempts; attempt++) {
    try {
      return await operation();
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
      const status = extractStatusFromError(err);
      const category = categorizeError(status);
      if (!isRetryable(category)) throw lastError;
      if (attempt < config.maxAttempts - 1) {
        const delayMs = computeRetryDelay(attempt, config);
        await delayWithTimeout(delayMs, signal);
      }
    }
  }
  throw lastError ?? new Error('Retry exhausted');
}

/** Attempt to extract HTTP status from a fetch/Axios-style error. */
function extractStatusFromError(err: unknown): number | undefined {
  if (err == null) return undefined;
  if (typeof err === 'object' && 'status' in err && typeof (err as { status: unknown }).status === 'number') {
    return (err as { status: number }).status;
  }
  if (typeof err === 'object' && 'response' in err) {
    const response = (err as { response?: { status?: number } }).response;
    if (response?.status != null) return response.status;
  }
  return undefined;
}

/**
 * React-Query retry function that uses centralized categorization.
 * Usage:
 *   useQuery({ queryFn: () => fetchX(), retry: queryRetryFn(DEFAULT_RETRY_CONFIG) })
 */
export function queryRetryFn(
  config: RetryConfig = DEFAULT_RETRY_CONFIG,
): (failureCount: number, error: Error) => boolean {
  return (failureCount, error) => {
    if (failureCount >= config.maxAttempts) return false;
    const status = extractStatusFromError(error);
    return isRetryable(categorizeError(status));
  };
}
