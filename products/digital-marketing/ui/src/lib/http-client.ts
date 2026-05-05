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
  requireIdempotency?: boolean; // P1-4: Flag to require idempotency key for write operations
  idempotencyKey?: string; // P1-022: Optional pre-generated idempotency key for mutation-scoped idempotency
}

/**
 * Generates a UUID v4 idempotency key.
 */
function generateIdempotencyKey(): string {
  return crypto.randomUUID();
}

/**
 * Determines if the request method requires idempotency.
 */
function requiresIdempotency(method: string | undefined): boolean {
  return method === 'POST' || method === 'PUT' || method === 'DELETE' || method === 'PATCH';
}

export async function apiRequest<T>(
  path: string,
  options: FetchOptions = {},
): Promise<T> {
  const { body, headers: extraHeaders, requireIdempotency, idempotencyKey, ...rest } = options;
  const token = getAuthToken();

  // P0-3.1: Attach all required headers (mandatory, not conditional)
  // P0-3.2: Generate correlation ID per request
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Correlation-ID': crypto.randomUUID(),
    ...(extraHeaders as Record<string, string>),
  };

  // P1-022: Use provided idempotencyKey if available, otherwise generate one
  // P1-4: Generate X-Idempotency-Key for write operations
  if (requireIdempotency || requiresIdempotency(rest.method)) {
    headers['X-Idempotency-Key'] = idempotencyKey ?? generateIdempotencyKey();
  }

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
    // P1-030: Extract correlation ID from response headers for error diagnostics
    const correlationId = response.headers.get('X-Correlation-ID') ?? headers['X-Correlation-ID'];
    throw new ApiError(
      `API error ${response.status}: ${text}`,
      response.status,
      correlationId,
      text
    );
  }

  const text = await response.text();
  if (!text) {
    return undefined as unknown as T;
  }
  return JSON.parse(text) as T;
}

/**
 * P1-030: Typed API error with correlation ID for diagnostics.
 * Preserves status code and correlation ID for error handling and user feedback.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly correlationId: string | null;
  readonly body: string;

  constructor(message: string, status: number, correlationId: string | null, body: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.correlationId = correlationId;
    this.body = body;
  }

  /**
   * Returns a user-friendly error message including correlation ID if available.
   */
  getUserMessage(): string {
    if (this.status >= 500) {
      return `Server error. Please try again later.${this.correlationId ? ` (Ref: ${this.correlationId})` : ''}`;
    }
    if (this.status === 403) {
      return 'You do not have permission to perform this action.';
    }
    if (this.status === 409) {
      return 'This action conflicts with the current state. Please refresh and try again.';
    }
    if (this.status === 429) {
      return 'Too many requests. Please wait a moment and try again.';
    }
    return `Request failed: ${this.message}${this.correlationId ? ` (Ref: ${this.correlationId})` : ''}`;
  }
}
