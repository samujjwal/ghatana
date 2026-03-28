/**
 * DCMAAR Desktop API client.
 *
 * Thin wrapper around the canonical {@link ./api/client} that adds
 * product-level error notifications via {@link showApiError}.
 * Auth token is read automatically from localStorage by the base client.
 *
 * Import this file for service calls within the desktop module.
 */
import canonicalClient from './api/client';
import { showApiError } from './notification';

// Re-exported for backwards compat with files that use `API_BASE_URL`
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

// Add DCMAAR-specific error notification on top of the base interceptors.
// Fires once at module load — the canonical client is a singleton.
canonicalClient.client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.code === 'ECONNREFUSED') {
      showApiError({
        message: 'Backend service unavailable. Please ensure the daemon is running.',
      });
    } else {
      showApiError(error);
    }
    return Promise.reject(error);
  }
);

export default canonicalClient;
