import { z } from 'zod';

export const API_BASE_URL: string = import.meta.env.VITE_PHR_API_URL ?? 'http://localhost:8080';

export class PhrApiError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number,
    public readonly resourceType?: string,
    public readonly correlationId?: string,
  ) {
    super(message);
    this.name = 'PhrApiError';
  }
}

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';

export type SessionContext = {
  tenantId: string;
  principalId: string;
  role: PhrRole;
  persona?: string;
  tier?: string;
  correlationId?: string;
  idempotencyKey?: string;
};

type RequestContext = Partial<SessionContext>;

function newCorrelationId(): string {
  return crypto.randomUUID();
}

export function withIdempotency<T extends SessionContext>(context: T): T & { idempotencyKey: string } {
  return {
    ...context,
    idempotencyKey: context.idempotencyKey ?? newCorrelationId(),
  };
}

export function buildPhrHeaders(context: RequestContext = {}): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-ID': context.correlationId ?? newCorrelationId(),
  };

  if (context.tenantId) {
    headers['X-Tenant-Id'] = context.tenantId;
  }
  if (context.principalId) {
    headers['X-Principal-Id'] = context.principalId;
  }
  if (context.role) {
    headers['X-Role'] = context.role;
  }
  if (context.persona) {
    headers['X-Persona'] = context.persona;
  }
  if (context.tier) {
    headers['X-Tier'] = context.tier;
  }
  if (context.idempotencyKey) {
    headers['X-Idempotency-Key'] = context.idempotencyKey;
  }

  return headers;
}

export async function phrFetch<T>(
  path: string,
  options: {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
    body?: BodyInit | null;
    context?: RequestContext;
    accept?: string;
    contentType?: string;
    expectedSchema?: z.ZodType<T>;
    signal?: AbortSignal;
  } = {},
): Promise<T> {
  const {
    method = 'GET',
    body = null,
    context = {},
    accept = 'application/json',
    contentType = 'application/json',
    expectedSchema,
    signal,
  } = options;

  const headers = buildPhrHeaders(context);
  headers.Accept = accept;
  if (contentType && body !== null) {
    headers['Content-Type'] = contentType;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body,
    signal,
  });

  if (!response.ok) {
    throw new PhrApiError(
      `PHR request failed: ${method} ${path} returned ${response.status}`,
      response.status,
      undefined,
      headers['X-Correlation-ID'],
    );
  }

  const responseBody = await response.text();
  const contentTypeHeader = response.headers.get('Content-Type') ?? '';
  const data: unknown = responseBody.length === 0
    ? undefined
    : contentTypeHeader.includes('application/json')
      ? JSON.parse(responseBody)
      : responseBody;
  return expectedSchema ? expectedSchema.parse(data) : (data as T);
}
