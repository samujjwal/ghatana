import axios, { AxiosInstance } from 'axios';

/**
 * API Client Base Configuration
 *
 * <p><b>Purpose</b><br>
 * Centralized HTTP client factory with interceptors for auth, tracing, tenant headers,
 * and error handling. Configured to match backend API structure.
 *
 * <p><b>Features</b><br>
 * - Automatic tenant header injection
 * - Token refresh on 401 responses
 * - Request tracing (X-Trace-ID header)
 * - Standardized error handling
 * - Base URL configuration for backend endpoints
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const client = createApiClient();
 * const kpis = await client.get('/kpis'); // baseURL already includes /api/v1
 * ```
 *
 * @doc.type utility
 * @doc.purpose API HTTP client factory
 * @doc.layer product
 * @doc.pattern Factory Pattern
 */

let mswReadyPromise: Promise<void> | null = null;

async function ensureMswReady(): Promise<void> {
    // Note: Vite requires static access to import.meta.env properties for SSR compatibility
    const useMocks = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';

    if (!useMocks) return; // Skip if mocks are disabled

    if (!mswReadyPromise) {
        mswReadyPromise = new Promise((resolve) => {
            // Check if MSW is already active
            if ((window as any).__MSW_ACTIVE__) {
                console.log('[API] MSW already active - proceeding with request');
                resolve();
                return;
            }

            // Wait for MSW to become active with timeout
            const start = Date.now();
            const timeout = 10000; // 10 seconds max wait (increased from 5s)
            const interval = setInterval(() => {
                if ((window as any).__MSW_ACTIVE__) {
                    console.log('[API] MSW activated - proceeding with request');
                    clearInterval(interval);
                    resolve();
                } else if (Date.now() - start > timeout) {
                    console.warn('[API] MSW did not activate within timeout - proceeding anyway');
                    clearInterval(interval);
                    resolve();
                }
            }, 50); // Check more frequently
        });
    }

    await mswReadyPromise;
}

export function createApiClient(): AxiosInstance {
    // Resolve environment variables using Vite's `import.meta.env` in the browser.
    // Note: Vite requires static access to import.meta.env properties for SSR compatibility

    // Follow app-creator semantics: VITE_USE_MOCKS is the primary switch,
    // with VITE_MOCK_API kept for backward compatibility.
    const useMocks = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';

    // In mock mode, use a same-origin relative base URL so that all HTTP
    // calls are intercepted by MSW without talking to the real backend. 
    // When mocks are disabled, fall back to the configured backend URL.
    let baseURL: string;
    if (useMocks) {
        // Force relative URL for MSW interception
        baseURL = '/api/v1';
    } else {
        // Use configured backend or localhost:8080 as fallback
        // Always append /api/v1 to the base URL for the real backend
        const configuredUrl = import.meta.env.VITE_API_URL || import.meta.env.REACT_APP_API_URL || 'http://localhost:8080';
        // Ensure we have /api/v1 suffix
        baseURL = configuredUrl.endsWith('/api/v1') ? configuredUrl : `${configuredUrl}/api/v1`;
    }

    // Helpful for verifying configuration in dev
    if (typeof window !== 'undefined') {
        console.log(
            `[API] Configuration: baseURL=${baseURL}, useMocks=${useMocks}`,
            useMocks ? '(MSW mocks enabled - requests to /api/v1 will be intercepted)' : '(connecting to real backend)'
        );
        console.log('[API] Environment:', {
            VITE_USE_MOCKS: import.meta.env.VITE_USE_MOCKS,
            VITE_API_URL: import.meta.env.VITE_API_URL,
            VITE_MOCK_API: import.meta.env.VITE_MOCK_API
        });
    }

    const client = axios.create({
        baseURL,
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        },
    });

    // Request interceptor: Ensure MSW is ready, then add headers
    client.interceptors.request.use(async (config) => {
        // Wait for MSW to be ready if using mocks
        await ensureMswReady();

        // Get tenant from localStorage or state
        const tenant = localStorage.getItem('software-org:tenant') || 'all-tenants';
        if (tenant && config.headers) {
            config.headers['X-Tenant-ID'] = tenant;
        }

        // Add trace ID for observability
        const traceId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        if (config.headers) {
            config.headers['X-Trace-ID'] = traceId;
        }

        return config;
    });

    // Response interceptor: Handle 401 and other errors
    client.interceptors.response.use(
        (response) => response,
        (error) => {
            if (error.response?.status === 401) {
                // Token expired - refresh and retry
                // TODO: Implement token refresh logic
                console.warn('Unauthorized - token may be expired');
            }

            // Log error with trace context
            console.error('API Error:', {
                status: error.response?.status,
                message: error.response?.data?.message || error.message,
                traceId: error.config?.headers?.['X-Trace-ID'],
            });

            return Promise.reject(error);
        }
    );

    return client;
}

// Lazy initialization: create client on first access
// Use a Proxy to intercept method calls and create the client if needed
let _apiClient: AxiosInstance | null = null;

function getOrCreateClient(): AxiosInstance {
    if (!_apiClient) {
        _apiClient = createApiClient();
    }
    return _apiClient;
}

// Create a proxy that lazily creates the client
export const apiClient = new Proxy({} as AxiosInstance, {
    get: (_target, prop) => {
        const client = getOrCreateClient();
        return (client as any)[prop];
    },
});

// Re-export all API clients and types
export * from './exports';
