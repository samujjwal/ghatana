/**
 * Centralised API configuration for parent-dashboard.
 *
 * Every network call MUST use these helpers instead of hardcoding URLs.
 * At build time the values come from Vite env vars; at runtime they
 * fall back to the values below (safe for local-dev, never production).
 */

const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? '/api';

const GRAPHQL_URL: string =
  import.meta.env.VITE_GRAPHQL_URL ?? '/graphql';

const WS_URL: string =
  import.meta.env.VITE_WS_URL ?? '/ws';

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}

export function graphqlUrl(): string {
  return GRAPHQL_URL;
}

export function wsUrl(): string {
  return WS_URL;
}

export { API_BASE_URL, GRAPHQL_URL, WS_URL };
