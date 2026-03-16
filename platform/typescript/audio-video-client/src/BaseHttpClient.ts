import type { ServiceErrorCode } from '@ghatana/audio-video-types';
import { AudioVideoServiceError } from '@ghatana/audio-video-types';

// ---------------------------------------------------------------------------
// Internal HTTP helpers
// ---------------------------------------------------------------------------

export interface ClientConfig {
  /** Base URL of the audio-video REST gateway, e.g. `https://api.ghatana.com/audio-video`. */
  baseUrl: string;
  /**
   * Returns the current Bearer token or API key to include in `Authorization`.
   * Called on every request so tokens can be refreshed by the caller.
   */
  getToken: () => string | Promise<string>;
  /** Network timeout in milliseconds (default: 30 000). */
  timeoutMs?: number;
  /** Maximum number of automatic retries on 5xx / network errors (default: 2). */
  maxRetries?: number;
}

interface RequestOptions {
  method?: string;
  headers?: Record<string, string>;
  body?: BodyInit;
  signal?: AbortSignal;
}

const ERROR_CODE_MAP: Record<number, ServiceErrorCode> = {
  400: 'INVALID_INPUT',
  401: 'UNAUTHORIZED',
  413: 'PAYLOAD_TOO_LARGE',
  429: 'QUOTA_EXCEEDED',
  500: 'INTERNAL_ERROR',
  503: 'SERVICE_UNAVAILABLE',
};

/**
 * Thin, fetch-based HTTP client base class used by SttClient, TtsClient, and
 * VisionClient.  Handles auth headers, timeouts, retries, and unified error
 * translation into `AudioVideoServiceError`.
 */
export class BaseHttpClient {
  protected readonly baseUrl: string;
  protected readonly getToken: () => string | Promise<string>;
  private readonly timeoutMs: number;
  private readonly maxRetries: number;

  constructor({ baseUrl, getToken, timeoutMs = 30_000, maxRetries = 2 }: ClientConfig) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.getToken = getToken;
    this.timeoutMs = timeoutMs;
    this.maxRetries = maxRetries;
  }

  protected async fetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const token = await this.getToken();
    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
      ...(options.headers ?? {}),
    };
    if (!(options.body instanceof FormData)) {
      headers['Content-Type'] ??= 'application/json';
    }

    let attempt = 0;
    while (true) {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), this.timeoutMs);
      const signal = options.signal
        ? anySignal([options.signal, controller.signal])
        : controller.signal;

      try {
        const response = await fetch(`${this.baseUrl}${path}`, {
          method: options.method ?? 'GET',
          headers,
          body: options.body,
          signal,
        });
        clearTimeout(timeoutId);

        if (!response.ok) {
          const errorBody = await response.text().catch(() => '');
          let parsed: { message?: string; traceId?: string } = {};
          try {
            parsed = JSON.parse(errorBody) as { message?: string; traceId?: string };
          } catch {
            /* raw text */
          }
          const code: ServiceErrorCode =
            ERROR_CODE_MAP[response.status] ?? 'INTERNAL_ERROR';
          throw new AudioVideoServiceError(
            code,
            parsed.message ?? `HTTP ${response.status}: ${response.statusText}`,
            response.status,
            parsed.traceId,
          );
        }

        // Detect empty bodies (204 No Content)
        const contentType = response.headers.get('content-type') ?? '';
        if (contentType.includes('application/json')) {
          return (await response.json()) as T;
        }
        return (await response.arrayBuffer()) as unknown as T;
      } catch (err) {
        clearTimeout(timeoutId);

        if (err instanceof AudioVideoServiceError) throw err;

        const isNetworkError = !(err instanceof Error) || err.name === 'TypeError';
        const isTimeout = err instanceof Error && err.name === 'AbortError';
        const shouldRetry = (isNetworkError || isTimeout) && attempt < this.maxRetries;

        if (!shouldRetry) {
          if (isTimeout) {
            throw new AudioVideoServiceError('TIMEOUT', `Request timed out after ${this.timeoutMs}ms`);
          }
          throw new AudioVideoServiceError(
            'SERVICE_UNAVAILABLE',
            err instanceof Error ? err.message : 'Network error',
          );
        }

        attempt++;
        const backoffMs = 2 ** attempt * 200;
        await sleep(backoffMs);
      }
    }
  }

  protected async fetchBinary(path: string, options: RequestOptions = {}): Promise<ArrayBuffer> {
    const token = await this.getToken();
    const headers: Record<string, string> = {
      Authorization: `Bearer ${token}`,
      Accept: 'application/octet-stream',
      ...(options.headers ?? {}),
    };

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        method: options.method ?? 'POST',
        headers,
        body: options.body,
        signal: options.signal ?? controller.signal,
      });
      clearTimeout(timeoutId);
      if (!response.ok) {
        const code: ServiceErrorCode = ERROR_CODE_MAP[response.status] ?? 'INTERNAL_ERROR';
        throw new AudioVideoServiceError(code, `HTTP ${response.status}`, response.status);
      }
      return await response.arrayBuffer();
    } catch (err) {
      clearTimeout(timeoutId);
      if (err instanceof AudioVideoServiceError) throw err;
      throw new AudioVideoServiceError('SERVICE_UNAVAILABLE', String(err));
    }
  }
}

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Returns an AbortSignal that aborts when *any* of the given signals abort. */
function anySignal(signals: AbortSignal[]): AbortSignal {
  const controller = new AbortController();
  for (const s of signals) {
    if (s.aborted) {
      controller.abort(s.reason);
      break;
    }
    s.addEventListener('abort', () => controller.abort(s.reason), { once: true });
  }
  return controller.signal;
}
