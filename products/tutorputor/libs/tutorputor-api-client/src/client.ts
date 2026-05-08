/**
 * @tutorputor/api-client — Base HTTP client
 *
 * Typed fetch wrapper for the TutorPutor API. All route modules build on this.
 *
 * @doc.type module
 * @doc.purpose Base HTTP client with typed errors, auth header injection, retry logic, and circuit breaking
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { buildAuthHeaders } from "@tutorputor/auth-client/headers";
import { mapHttpError, NetworkError, type ApiError } from "./errors.js";

// ---------------------------------------------------------------------------
// Client configuration
// ---------------------------------------------------------------------------

export interface TutorPutorClientConfig {
  /**
   * Base URL of the TutorPutor API gateway, e.g. `http://localhost:3200`.
   * Trailing slashes are stripped automatically.
   */
  baseUrl: string;

  /**
   * Returns the current access token, or null when the session is unauthenticated.
   * Called on every request so token rotation is transparent.
   */
  getAccessToken: () => string | null;

  /**
   * Optional: called when a 401 is received, allowing the caller to initiate
   * a token refresh flow and retry the request.
   * Return the new access token to retry, or null/undefined to propagate the error.
   */
  onUnauthorized?: () => Promise<string | null | undefined>;

  /**
   * Request timeout in milliseconds. Defaults to 30 000.
   */
  timeoutMs?: number;

  /**
   * Number of retry attempts for retryable errors. Defaults to 3.
   */
  retries?: number;

  /**
   * Delay between retry attempts in milliseconds. Defaults to 1000.
   */
  retryDelayMs?: number;

  /**
   * Enable circuit breaker. Defaults to true.
   */
  enableCircuitBreaker?: boolean;

  /**
   * Circuit breaker threshold. Defaults to 5 failures.
   */
  circuitBreakerThreshold?: number;

  /**
   * Circuit breaker timeout in milliseconds. Defaults to 60000.
   */
  circuitBreakerTimeoutMs?: number;
}

// ---------------------------------------------------------------------------
// Circuit breaker implementation
// ---------------------------------------------------------------------------

class CircuitBreaker {
  private failures = 0;
  private lastFailureTime = 0;
  private state: "CLOSED" | "OPEN" | "HALF_OPEN" = "CLOSED";

  constructor(
    private readonly threshold: number = 5,
    private readonly timeout: number = 60000,
  ) {}

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    if (this.state === "OPEN") {
      if (Date.now() - this.lastFailureTime > this.timeout) {
        this.state = "HALF_OPEN";
      } else {
        throw new Error("Circuit breaker is OPEN");
      }
    }

    try {
      const result = await operation();
      if (this.state === "HALF_OPEN") {
        this.reset();
      }
      return result;
    } catch (error) {
      this.recordFailure();
      throw error;
    }
  }

  private recordFailure(): void {
    this.failures += 1;
    this.lastFailureTime = Date.now();
    if (this.failures >= this.threshold) {
      this.state = "OPEN";
    }
  }

  private reset(): void {
    this.failures = 0;
    this.state = "CLOSED";
  }

  getState(): string {
    return this.state;
  }
}

// ---------------------------------------------------------------------------
// Internal request helper
// ---------------------------------------------------------------------------

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  skipRetry?: boolean;
  skipCircuitBreaker?: boolean;
}

/**
 * Core API request function used by all route modules.
 * Handles auth headers, timeout, error mapping, retry logic, circuit breaking, and one transparent 401 retry.
 */
export async function apiRequest<T>(
  config: TutorPutorClientConfig,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const base = config.baseUrl.replace(/\/+$/, "");
  const url = `${base}${path}`;
  const timeoutMs = config.timeoutMs ?? 30_000;
  const retries = config.retries ?? 3;
  const retryDelayMs = config.retryDelayMs ?? 1000;
  const enableCircuitBreaker = config.enableCircuitBreaker ?? true;

  const circuitBreaker = new CircuitBreaker(
    config.circuitBreakerThreshold,
    config.circuitBreakerTimeoutMs,
  );

  const doRequest = async (accessToken: string | null): Promise<Response> => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const authHeaders = buildAuthHeaders(accessToken, options.headers);
      return await fetch(url, {
        method: options.method ?? "GET",
        headers: authHeaders,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
        signal: options.signal ?? controller.signal,
      });
    } finally {
      clearTimeout(timer);
    }
  };

  const executeWithRetry = async (): Promise<Response> => {
    let lastError: Error | undefined;

    for (let attempt = 1; attempt <= retries; attempt += 1) {
      try {
        const response = await doRequest(config.getAccessToken());

        // Transparent 401 retry
        if (response.status === 401 && config.onUnauthorized) {
          const newToken = await config.onUnauthorized();
          if (newToken) {
            return await doRequest(newToken);
          }
        }

        return response;
      } catch (err) {
        lastError = err instanceof Error ? err : new Error(String(err));

        if (options.skipRetry || attempt === retries) {
          throw lastError;
        }

        // Retry on network errors or 5xx errors
        if (
          lastError instanceof NetworkError ||
          (lastError instanceof Error &&
            lastError.message.includes("AbortError"))
        ) {
          const delay = retryDelayMs * Math.pow(2, attempt - 1);
          await new Promise((resolve) => setTimeout(resolve, delay));
          continue;
        }

        throw lastError;
      }
    }

    throw lastError ?? new Error("Request failed after retries");
  };

  let response: Response;
  if (enableCircuitBreaker && !options.skipCircuitBreaker) {
    response = await circuitBreaker.execute(executeWithRetry);
  } else {
    response = await executeWithRetry();
  }

  if (!response.ok) {
    throw await mapHttpError(response);
  }

  // 204 No Content
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

// ---------------------------------------------------------------------------
// Client factory
// ---------------------------------------------------------------------------

/**
 * Creates a base API request function bound to a specific client config.
 * Route modules accept this bound function to avoid passing config repeatedly.
 */
export type BoundApiRequest = <T>(path: string, options?: RequestOptions) => Promise<T>;

export function createBoundRequest(config: TutorPutorClientConfig): BoundApiRequest {
  return <T>(path: string, options?: RequestOptions) =>
    apiRequest<T>(config, path, options);
}

/**
 * Type guard: checks if an error is a known ApiError.
 */
export { type ApiError };
export { ApiError as ApiErrorClass } from "./errors.js";
