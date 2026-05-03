/**
 * Lightweight HTTP client utilities for DMOS UI.
 *
 * @doc.type api-client
 * @doc.purpose Shared auth token management and fetch helper
 * @doc.layer frontend
 */

let runtimeAuthToken: string | null = null;

interface RequestContext {
  tenantId: string | null;
  principalId: string | null;
  sessionId: string | null;
  roles: string[];
  permissions: string[];
}

let runtimeContext: RequestContext = {
  tenantId: null,
  principalId: null,
  sessionId: null,
  roles: [],
  permissions: [],
};

export const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

export function getAuthToken(): string | null {
  return runtimeAuthToken;
}

export function setAuthToken(token: string): void {
  runtimeAuthToken = token;
}

export function clearAuthToken(): void {
  runtimeAuthToken = null;
}

export function setRequestContext(
  tenantId: string | null,
  principalId: string | null,
  sessionId: string | null,
  roles: string[],
  permissions: string[],
): void {
  runtimeContext = { tenantId, principalId, sessionId, roles, permissions };
}

export function clearRequestContext(): void {
  runtimeContext = { tenantId: null, principalId: null, sessionId: null, roles: [], permissions: [] };
}

export interface FetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

export async function apiRequest<T>(
  path: string,
  options: FetchOptions = {},
): Promise<T> {
  const { body, headers: extraHeaders, ...rest } = options;
  const token = getAuthToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Correlation-ID': crypto.randomUUID(),
    ...(extraHeaders as Record<string, string>),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (runtimeContext.tenantId) {
    headers['X-Tenant-ID'] = runtimeContext.tenantId;
  }
  if (runtimeContext.principalId) {
    headers['X-Principal-ID'] = runtimeContext.principalId;
  }
  if (runtimeContext.sessionId) {
    headers['X-Session-ID'] = runtimeContext.sessionId;
  }
  if (runtimeContext.roles.length > 0) {
    headers['X-Roles'] = runtimeContext.roles.join(',');
  }
  if (runtimeContext.permissions.length > 0) {
    headers['X-Permissions'] = runtimeContext.permissions.join(',');
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(`API error ${response.status}: ${text}`);
  }

  const text = await response.text();
  if (!text) {
    return undefined as unknown as T;
  }
  return JSON.parse(text) as T;
}
