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

  // P0-3.1: Attach all required headers (mandatory, not conditional)
  // P0-3.2: Generate correlation ID per request
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Correlation-ID': crypto.randomUUID(),
    ...(extraHeaders as Record<string, string>),
  };
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  
  // Mandatory headers - throw if not set
  if (!runtimeContext.tenantId) {
    throw new Error('X-Tenant-ID is required but not set in request context');
  }
  headers['X-Tenant-ID'] = runtimeContext.tenantId;
  
  if (!runtimeContext.principalId) {
    throw new Error('X-Principal-ID is required but not set in request context');
  }
  headers['X-Principal-ID'] = runtimeContext.principalId;
  
  if (!runtimeContext.sessionId) {
    throw new Error('X-Session-ID is required but not set in request context');
  }
  headers['X-Session-ID'] = runtimeContext.sessionId;
  
  // Roles and permissions are optional but should be included if present
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
