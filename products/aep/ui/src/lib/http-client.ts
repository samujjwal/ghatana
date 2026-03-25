/**
 * Shared AEP HTTP client configuration.
 *
 * Single source of truth for API base URL and authentication.
 * All API modules (pipeline, aep, sse) import from here.
 *
 * @doc.type config
 * @doc.purpose Centralised HTTP client factory with auth
 * @doc.layer frontend
 */
import axios, { type AxiosInstance } from 'axios';

/**
 * API base URL.
 *
 * - Dev:  empty string — Vite proxy forwards `/api` → `localhost:8090` (Java backend)
 * - Prod: set `VITE_AEP_API_URL` to the backend origin for cross-origin
 */
export const API_BASE_URL: string = import.meta.env.VITE_AEP_API_URL ?? '';

/**
 * Returns the current auth token from local storage, if present.
 */
export function getAuthToken(): string | null {
  return localStorage.getItem('aep-token');
}

/**
 * Pre-configured Axios instance shared by all AEP API modules.
 *
 * - Adds `Authorization: Bearer <token>` when a token exists.
 * - 30 s timeout.
 * - JSON content type.
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
});

// Attach auth token to every outgoing request
apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
