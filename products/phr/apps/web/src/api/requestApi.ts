import { z } from 'zod';

export const API_BASE_URL: string = import.meta.env.VITE_PHR_API_URL ?? 'http://localhost:8080';

export class PhrApiError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number,
    public readonly resourceType?: string,
    public readonly correlationId?: string,
    public readonly error?: string,
    public readonly code?: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'PhrApiError';
  }
}

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';

export type SessionContext = {
  sessionId?: string;
  correlationId?: string;
  idempotencyKey?: string;
};

type RequestContext = Partial<SessionContext>;

export function toSessionContext(session: SessionContext): SessionContext {
  return {
    sessionId: session.sessionId,
    correlationId: session.correlationId,
    idempotencyKey: session.idempotencyKey,
  };
}

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

  // Only include safe client headers - identity is resolved server-side via session
  if (context.sessionId) {
    headers['Cookie'] = `SESSION=${context.sessionId}`;
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
    retry?: boolean;
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
    retry = true,
  } = options;

  const headers = buildPhrHeaders(context);
  headers.Accept = accept;
  if (contentType && body !== null) {
    headers['Content-Type'] = contentType;
  }

  // Determine if retry is allowed
  const allowRetry = retry && (method === 'GET' || context.idempotencyKey !== undefined);
  const maxRetries = allowRetry ? 3 : 0;
  const retryDelay = 1000; // 1 second base delay

  let lastError: Error | undefined;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const response = await fetch(`${API_BASE_URL}${path}`, {
        method,
        headers,
        body,
        signal,
      });

      if (!response.ok) {
        let errorBody: unknown;
        try {
          const errorText = await response.text();
          errorBody = errorText.length > 0 ? JSON.parse(errorText) : undefined;
        } catch {
          // If parsing fails, errorBody remains undefined
        }

        const backendError = typeof errorBody === 'object' && errorBody !== null ? errorBody as Record<string, unknown> : undefined;
        const responseCorrelationId =
          response.headers.get('X-Correlation-ID')
          ?? response.headers.get('X-Ghatana-Correlation-Id')
          ?? undefined;
        
        throw new PhrApiError(
          `PHR request failed: ${method} ${path} returned ${response.status}`,
          response.status,
          undefined,
          responseCorrelationId,
          typeof backendError?.error === 'string' ? backendError.error : undefined,
          typeof backendError?.code === 'string' ? backendError.code : undefined,
          backendError?.details,
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
    } catch (error) {
      lastError = error as Error;
      
      // Don't retry on abort signal
      if (error instanceof Error && error.name === 'AbortError') {
        throw error;
      }

      // Don't retry on 4xx errors (client errors)
      if (error instanceof PhrApiError && error.statusCode >= 400 && error.statusCode < 500) {
        throw error;
      }

      // Don't retry on schema validation errors — malformed data won't improve on retry
      if (error instanceof Error && error.name === 'ZodError') {
        throw error;
      }

      // If this was the last attempt, throw the error
      if (attempt === maxRetries) {
        throw error;
      }

      // Wait before retrying with exponential backoff
      const delay = retryDelay * Math.pow(2, attempt);
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }

  // This should never be reached, but TypeScript needs it
  throw lastError || new Error('Unknown error in phrFetch');
}
