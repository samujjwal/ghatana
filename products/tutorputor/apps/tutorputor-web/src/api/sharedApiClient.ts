/**
 * Shared API Client Utilities
 *
 * Provides canonical header generation and error handling for all TutorPutor API clients.
 * This ensures consistent authentication, tenant context, correlation IDs, and error
 * handling across web, admin, and any other frontend applications.
 *
 * @doc.type utility
 * @doc.purpose Shared API client utilities for consistent auth/headers/errors
 * @doc.layer product
 * @doc.pattern Utility
 */

/**
 * Derive tenantId from JWT token (canonical source)
 * This avoids stale-tenant and tenant-leak risks from localStorage.
 */
export function deriveTenantIdFromToken(token: string | null): string | null {
  if (!token) {
    return null;
  }

  try {
    // Simple JWT decode to extract tenantId from payload
    const payload = JSON.parse(atob(token.split('.')[1])) as { tenantId?: string };
    return payload.tenantId || null;
  } catch (e) {
    console.error('[Shared API Client] Failed to decode JWT for tenantId:', e);
    return null;
  }
}

/**
 * Generate standard API headers with auth, tenant, and correlation ID.
 * Throws if tenant context is missing (authentication required).
 */
export function getStandardHeaders(
  token: string | null,
  options?: {
    includeContentType?: boolean;
    additionalHeaders?: Record<string, string>;
  },
): HeadersInit {
  const tenantId = deriveTenantIdFromToken(token);

  if (!tenantId) {
    throw new Error("Authentication required: No tenant context found");
  }

  const headers: HeadersInit = {
    ...options?.additionalHeaders,
  };

  if (options?.includeContentType !== false) {
    headers["Content-Type"] = "application/json";
  }

  headers["X-Tenant-ID"] = tenantId;
  headers["X-Correlation-ID"] = crypto.randomUUID();

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  return headers;
}

/**
 * Canonical API error structure matching OpenAPI specification
 */
export interface ApiErrorEnvelope {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
  traceId?: string;
  timestamp?: string;
  statusCode: number;
}

/**
 * Parse response and throw standardized error if not OK.
 * Handles JSON parsing, error envelope extraction, and correlation ID propagation.
 */
export async function handleResponse<T>(
  response: Response,
): Promise<T> {
  if (!response.ok) {
    let errorBody: Record<string, unknown> = {};
    try {
      errorBody = await response.json();
    } catch {
      // If JSON parsing fails, use status text
    }

    const error = new Error(
      (errorBody?.error as string) ||
        (errorBody?.message as string) ||
        `HTTP ${response.status}: ${response.statusText}`,
    ) as Error & { statusCode: number; requestId?: string };
    error.statusCode = response.status;

    // Propagate the server-assigned requestId (traceId) for UI display
    if (typeof errorBody?.traceId === "string") {
      error.requestId = errorBody.traceId;
    } else if (typeof errorBody?.requestId === "string") {
      error.requestId = errorBody.requestId;
    }

    throw error;
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return response.json();
}

/**
 * Standard request wrapper with consistent headers and error handling.
 * Use this for simple GET/POST/PATCH/DELETE operations.
 */
export async function standardRequest<T>(
  url: string,
  options: {
    method: "GET" | "POST" | "PATCH" | "DELETE" | "PUT";
    token: string | null;
    body?: unknown;
    additionalHeaders?: Record<string, string>;
    includeContentType?: boolean;
  },
): Promise<T> {
  const headers = getStandardHeaders(options.token, {
    includeContentType: options.includeContentType,
    additionalHeaders: options.additionalHeaders,
  });

  const response = await fetch(url, {
    method: options.method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  return handleResponse<T>(response);
}
