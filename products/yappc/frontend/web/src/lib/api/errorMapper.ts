/**
 * Error Mapper Module
 *
 * Centralized error handling logic for the YAPPC API client.
 * Extracts error information from HTTP responses and provides typed error types.
 *
 * @doc.type module
 * @doc.purpose Error handling and mapping
 * @doc.layer product
 * @doc.pattern Error Mapper
 */

import { readErrorResponse } from '@/lib/http';

export interface ApiError {
  readonly status: number;
  readonly message: string;
  /** RFC-7807 type URI, if the server returned one. */
  readonly type?: string;
}

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly type?: string,
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

/**
 * Handles HTTP error responses, extracting error details including RFC-7807 problem details.
 */
export async function handleError(response: Response, context: string): Promise<never> {
  const message = await readErrorResponse(response, `${context} failed (${response.status})`);
  // RFC-7807: attempt to extract `type` from problem+json
  let type: string | undefined;
  try {
    const ct = response.headers.get('content-type') ?? '';
    if (ct.includes('problem+json')) {
      const body = JSON.parse(message) as { type?: unknown };
      if (typeof body.type === 'string') type = body.type;
    }
  } catch {
    // non-parseable, ignore
  }
  throw new ApiRequestError(response.status, message, type);
}
