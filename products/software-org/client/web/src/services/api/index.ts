/**
 * API Client Base Configuration
 *
 * <p><b>Purpose</b><br>
 * Centralized HTTP client using platform @ghatana/api library with interceptors
 * for auth, tracing, tenant headers, and error handling.
 *
 * <p><b>Features</b><br>
 * - Automatic tenant header injection
 * - Token refresh on 401 responses
 * - Request tracing (X-Trace-ID header)
 * - Standardized error handling
 * - Retry logic with exponential backoff
 * - Mock mode support for development
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { apiClient } from './index';
 * const response = await apiClient.get('/kpis');
 * ```
 *
 * @doc.type utility
 * @doc.purpose API HTTP client using platform @ghatana/api
 * @doc.layer product
 * @doc.pattern Factory Pattern
 */

import { ApiClient } from "@ghatana/api";
import type { ApiRequest, ApiResponse } from "@ghatana/api";

let mswReadyPromise: Promise<void> | null = null;

async function ensureMswReady(): Promise<void> {
  const useMocks =
    import.meta.env.VITE_USE_MOCKS === "true" ||
    import.meta.env.VITE_MOCK_API === "true";

  if (!useMocks) return;

  if (!mswReadyPromise) {
    mswReadyPromise = new Promise((resolve) => {
      if ((window as any).__MSW_ACTIVE__) {
        console.log("[API] MSW already active - proceeding with request");
        resolve();
        return;
      }

      const start = Date.now();
      const timeout = 10000;
      const interval = setInterval(() => {
        if ((window as any).__MSW_ACTIVE__) {
          console.log("[API] MSW activated - proceeding with request");
          clearInterval(interval);
          resolve();
        } else if (Date.now() - start > timeout) {
          console.warn(
            "[API] MSW did not activate within timeout - proceeding anyway",
          );
          clearInterval(interval);
          resolve();
        }
      }, 50);
    });
  }

  await mswReadyPromise;
}

const useMocks =
  import.meta.env.VITE_USE_MOCKS === "true" ||
  import.meta.env.VITE_MOCK_API === "true";

let baseURL: string;
if (useMocks) {
  baseURL = "/api/v1";
} else {
  const configuredUrl =
    import.meta.env.VITE_API_URL ||
    import.meta.env.REACT_APP_API_URL ||
    "http://localhost:8080";
  baseURL = configuredUrl.endsWith("/api/v1")
    ? configuredUrl
    : `${configuredUrl}/api/v1`;
}

if (typeof window !== "undefined") {
  console.log(
    `[API] Configuration: baseURL=${baseURL}, useMocks=${useMocks}`,
    useMocks ? "(MSW mocks enabled)" : "(connecting to real backend)",
  );
}

const platformClient = new ApiClient({
  baseUrl: baseURL,
  defaultHeaders: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
  timeoutMs: 30000,
  retry: {
    attempts: 3,
    backoffMs: 250,
  },
});

platformClient.useRequest(async (request: ApiRequest): Promise<ApiRequest> => {
  await ensureMswReady();

  const headers = { ...(request.headers || {}) };

  const tenant = localStorage.getItem("software-org:tenant") || "all-tenants";
  headers["X-Tenant-ID"] = tenant;

  const traceId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  headers["X-Trace-ID"] = traceId;

  const token = localStorage.getItem("auth_token");
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  return {
    ...request,
    headers,
  };
});

platformClient.useResponse(
  async (
    response: ApiResponse<unknown>,
    request: ApiRequest,
  ): Promise<ApiResponse<unknown>> => {
    if (response.status >= 400) {
      const data = response.data as any;
      const message = data?.message || data?.error || "Request failed";

      console.error("[API Error]", {
        status: response.status,
        message,
        traceId: request.headers?.["X-Trace-ID"],
        url: request.url,
        method: request.method,
      });

      if (response.status === 401) {
        localStorage.removeItem("auth_token");
      }
    }

    return response;
  },
);

/**
 * Axios-compatible wrapper for backward compatibility
 * Extracts data from ApiResponse<T> to match existing API usage pattern
 */
const apiClient = {
  async get<T>(url: string, config?: any): Promise<{ data: T }> {
    const response = await platformClient.request<T>({
      url,
      method: "GET",
      ...config,
    });
    return { data: response.data };
  },
  async post<T>(url: string, data?: any, config?: any): Promise<{ data: T }> {
    const response = await platformClient.request<T>({
      url,
      method: "POST",
      data,
      ...config,
    });
    return { data: response.data };
  },
  async put<T>(url: string, data?: any, config?: any): Promise<{ data: T }> {
    const response = await platformClient.request<T>({
      url,
      method: "PUT",
      data,
      ...config,
    });
    return { data: response.data };
  },
  async patch<T>(url: string, data?: any, config?: any): Promise<{ data: T }> {
    const response = await platformClient.request<T>({
      url,
      method: "PATCH",
      data,
      ...config,
    });
    return { data: response.data };
  },
  async delete<T>(url: string, config?: any): Promise<{ data: T }> {
    const response = await platformClient.request<T>({
      url,
      method: "DELETE",
      ...config,
    });
    return { data: response.data };
  },
};

export { apiClient, platformClient };
export * from "./exports";
