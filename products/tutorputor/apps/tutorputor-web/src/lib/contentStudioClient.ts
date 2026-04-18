/**
 * Shared Content Studio API client for TutorPutor web app.
 *
 * Provides a single, consistent fetch wrapper for all Content Studio API calls.
 * Auth headers, base URL, and error handling live here — not in individual hooks.
 */

export const CONTENT_STUDIO_BASE = "/api/content-studio";

export function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("auth_token");
  const tenantId = localStorage.getItem("tenant_id");

  if (!tenantId) {
    throw new Error("Authentication required: No tenant context found");
  }

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Tenant-ID": tenantId,
    "X-Correlation-ID": crypto.randomUUID(),
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
}

export async function contentStudioFetch<T>(
  path: string,
  options?: RequestInit,
): Promise<T> {
  const res = await fetch(`${CONTENT_STUDIO_BASE}${path}`, {
    ...options,
    headers: { ...getAuthHeaders(), ...(options?.headers ?? {}) },
  });
  if (!res.ok) {
    const body = await res.json().catch(async () => ({
      error: await res.text().catch(() => res.statusText),
    }));
    const err = new Error(
      (body as { error?: string }).error ||
        `Content Studio API error ${res.status}: ${res.statusText}`,
    ) as Error & { statusCode: number };
    err.statusCode = res.status;
    throw err;
  }
  return res.json() as Promise<T>;
}

export function buildQueryString(filters?: Record<string, unknown>): string {
  if (!filters) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null) {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}
