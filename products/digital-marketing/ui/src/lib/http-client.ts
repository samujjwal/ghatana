/**
 * Lightweight HTTP client utilities for DMOS UI.
 *
 * @doc.type api-client
 * @doc.purpose Shared auth token management and fetch helper
 * @doc.layer frontend
 */

let runtimeAuthToken: string | null = null;

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
    ...(extraHeaders as Record<string, string>),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
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
