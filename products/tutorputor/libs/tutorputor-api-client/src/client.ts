/**
 * @tutorputor/api-client — Base HTTP client
 *
 * Typed fetch wrapper for the TutorPutor API. All route modules build on this.
 *
 * @doc.type module
 * @doc.purpose Base HTTP client with typed errors and auth header injection
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
}

// ---------------------------------------------------------------------------
// Internal request helper
// ---------------------------------------------------------------------------

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

/**
 * Core API request function used by all route modules.
 * Handles auth headers, timeout, error mapping, and one transparent 401 retry.
 */
export async function apiRequest<T>(
  config: TutorPutorClientConfig,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const base = config.baseUrl.replace(/\/+$/, "");
  const url = `${base}${path}`;
  const timeoutMs = config.timeoutMs ?? 30_000;

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

  let response: Response;
  try {
    response = await doRequest(config.getAccessToken());
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      throw new NetworkError(err);
    }
    throw new NetworkError(err);
  }

  // Transparent 401 retry
  if (response.status === 401 && config.onUnauthorized) {
    const newToken = await config.onUnauthorized();
    if (newToken) {
      try {
        response = await doRequest(newToken);
      } catch (err) {
        throw new NetworkError(err);
      }
    }
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
