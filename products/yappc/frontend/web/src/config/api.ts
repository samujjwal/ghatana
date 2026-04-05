/**
 * Centralised API configuration for yappc web frontend.
 *
 * Every network call MUST use these helpers instead of hardcoding URLs.
 * At build time the values come from Vite env vars; at runtime they
 * fall back to relative paths (which work behind a reverse-proxy in
 * production and through Vite proxy in development).
 */

const API_ORIGIN: string =
  import.meta.env.VITE_API_ORIGIN ?? '';

const JAVA_BACKEND_URL: string =
  import.meta.env.VITE_JAVA_BACKEND_URL ?? '/api';

const GRAPHQL_URL: string =
  import.meta.env.VITE_GRAPHQL_URL ?? '/graphql';

const WS_URL: string =
  import.meta.env.VITE_WEBSOCKET_URL ?? '/ws';

export function apiUrl(path: string): string {
  const base = API_ORIGIN || JAVA_BACKEND_URL;
  return `${base}${path.startsWith('/') ? path : `/${path}`}`;
}

export function graphqlUrl(): string {
  return GRAPHQL_URL;
}

export function wsUrl(): string {
  return WS_URL;
}

export { API_ORIGIN, JAVA_BACKEND_URL, GRAPHQL_URL, WS_URL };
