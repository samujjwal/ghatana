/**
 * Shared AEP HTTP client configuration.
 *
 * Single source of truth for API base URL and authentication.
 * All API modules (pipeline, aep, sse) import from here.
 *
 * @doc.type config
 * @doc.purpose Centralised HTTP client factory with auth
 * @doc.layer frontend
 */

export interface HttpRequestConfig {
  params?: Record<string, string | number | boolean | null | undefined>;
  headers?: Record<string, string>;
}

export interface HttpResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

export interface HttpClient {
  get<T>(url: string, config?: HttpRequestConfig): Promise<HttpResponse<T>>;
  post<T>(
    url: string,
    body?: unknown,
    config?: HttpRequestConfig,
  ): Promise<HttpResponse<T>>;
  put<T>(
    url: string,
    body?: unknown,
    config?: HttpRequestConfig,
  ): Promise<HttpResponse<T>>;
  patch<T>(
    url: string,
    body?: unknown,
    config?: HttpRequestConfig,
  ): Promise<HttpResponse<T>>;
  delete<T>(url: string, config?: HttpRequestConfig): Promise<HttpResponse<T>>;
}

export const AUTH_TOKEN_STORAGE_KEY = "aep-token";
export const SESSION_TOKEN_STORAGE_KEY = "aep-session";

/**
 * API base URL.
 *
 * - Dev:  empty string — Vite proxy forwards `/api` → `localhost:8090` (Java backend)
 * - Prod: set `VITE_AEP_API_URL` to the backend origin for cross-origin
 */
export const API_BASE_URL: string = import.meta.env.VITE_AEP_API_URL ?? "";

/**
 * Returns the current auth token from local storage, if present.
 */
/**
 * Reads the current auth token from session storage.
 *
 * NOTE (TASK-I5): We use `sessionStorage` instead of `localStorage` so tokens
 * are cleared when the tab closes and are not persisted across browser sessions.
 * This reduces the attack surface for XSS that exfiltrates tokens and avoids
 * leaking credentials across shared devices.
 *
 * Long-term the platform should move to httpOnly + Secure + SameSite cookies
 * set by the backend, which would make these helpers obsolete.
 */
export function getAuthToken(): string | null {
  return sessionStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
}

export function setAuthToken(token: string): void {
  sessionStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);
}

export function getSessionToken(): string | null {
  return sessionStorage.getItem(SESSION_TOKEN_STORAGE_KEY);
}

export function setSessionToken(token: string): void {
  sessionStorage.setItem(SESSION_TOKEN_STORAGE_KEY, token);
}

export function clearAuthState(): void {
  sessionStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  sessionStorage.removeItem(SESSION_TOKEN_STORAGE_KEY);
  sessionStorage.removeItem(SESSION_EXPIRY_STORAGE_KEY);
}

const SESSION_EXPIRY_STORAGE_KEY = "aep-session-expires-at";

export function getSessionExpiry(): number | null {
  const raw = sessionStorage.getItem(SESSION_EXPIRY_STORAGE_KEY);
  if (!raw) return null;
  const value = Number(raw);
  return Number.isNaN(value) ? null : value;
}

export function setSessionExpiry(timestampMs: number): void {
  sessionStorage.setItem(SESSION_EXPIRY_STORAGE_KEY, String(timestampMs));
}

/**
 * Pre-configured HTTP client shared by all AEP API modules.
 *
 * - Adds `Authorization: Bearer <token>` when a token exists.
 * - 30 s timeout via `AbortSignal.timeout` when available.
 * - JSON content type.
 */
export const apiClient: HttpClient = {
  get<T>(url: string, config?: HttpRequestConfig) {
    return request<T>("GET", url, undefined, config);
  },
  post<T>(url: string, body?: unknown, config?: HttpRequestConfig) {
    return request<T>("POST", url, body, config);
  },
  put<T>(url: string, body?: unknown, config?: HttpRequestConfig) {
    return request<T>("PUT", url, body, config);
  },
  patch<T>(url: string, body?: unknown, config?: HttpRequestConfig) {
    return request<T>("PATCH", url, body, config);
  },
  delete<T>(url: string, config?: HttpRequestConfig) {
    return request<T>("DELETE", url, undefined, config);
  },
};

function buildUrl(path: string, params?: HttpRequestConfig["params"]): string {
  const base =
    API_BASE_URL || globalThis.location?.origin || "http://localhost";
  const url = new URL(path, base);

  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== null && value !== undefined) {
      url.searchParams.set(key, String(value));
    }
  }

  if (API_BASE_URL) {
    return url.toString();
  }

  return `${url.pathname}${url.search}`;
}

async function request<T>(
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE",
  path: string,
  body?: unknown,
  config: HttpRequestConfig = {},
): Promise<HttpResponse<T>> {
  const headers = new Headers({
    "Content-Type": "application/json",
    // F-048: Propagate a per-request correlation ID so every span in the
    // backend can be correlated back to the originating browser request.
    "X-Correlation-ID": typeof crypto !== "undefined" && crypto.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    ...config.headers,
  });
  const token = getAuthToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const sessionToken = getSessionToken();
  if (sessionToken) {
    headers.set("X-AEP-Session", sessionToken);
  }

  const response = await fetch(buildUrl(path, config.params), {
    method,
    headers,
    body:
      body === undefined || body === null ? undefined : JSON.stringify(body),
    signal:
      typeof AbortSignal !== "undefined" && "timeout" in AbortSignal
        ? AbortSignal.timeout(30_000)
        : undefined,
  });

  const contentType = response.headers.get("content-type") ?? "";
  let data: unknown = null;
  if (response.status !== 204) {
    try {
      data = contentType.includes("application/json")
        ? await response.json()
        : await response.text();
    } catch {
      if (contentType.includes("application/json") && !response.ok) {
        try {
          data = await response.text();
        } catch {
          data = null;
        }
      }
    }
  }

  if (!response.ok) {
    const message =
      typeof data === "string" && data
        ? data
        : (typeof data === "object" && data && "message" in data)
          ? String((data as { message: unknown }).message)
          : response.statusText || `HTTP ${response.status}`;
    throw new Error(message);
  }

  return {
    data: data as T,
    status: response.status,
    headers: response.headers,
  };
}
