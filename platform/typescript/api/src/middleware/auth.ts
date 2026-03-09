/**
 * Authentication middleware for ApiClient.
 *
 * <p><b>Purpose</b><br>
 * Provides reusable auth middleware that adds Bearer tokens to requests
 * and handles 401 responses.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { ApiClient } from '@ghatana/api';
 * import { createAuthMiddleware } from '@ghatana/api/middleware';
 * 
 * const client = new ApiClient({ baseUrl: '/api' });
 * const auth = createAuthMiddleware({
 *   getToken: () => localStorage.getItem('token'),
 *   onUnauthorized: () => window.location.href = '/login',
 * });
 * 
 * client.useRequest(auth.request);
 * client.useResponse(auth.response);
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Authentication middleware
 * @doc.layer libs
 * @doc.pattern Middleware
 */

import type {
    ApiRequest,
    ApiResponse,
    RequestMiddleware,
    ResponseMiddleware,
} from './types';

/**
 * Auth middleware configuration.
 */
export interface AuthMiddlewareConfig {
    /** Function to get the current auth token */
    getToken: () => string | null | Promise<string | null>;
    /** Callback when a 401 response is received */
    onUnauthorized?: () => void | Promise<void>;
    /** Header name for the token (default: 'Authorization') */
    headerName?: string;
    /** Token prefix (default: 'Bearer') */
    tokenPrefix?: string;
    /** Skip auth for these URL patterns */
    skipPatterns?: RegExp[];
}

/**
 * Auth middleware result.
 */
export interface AuthMiddleware {
    /** Request middleware to add auth header */
    request: RequestMiddleware;
    /** Response middleware to handle 401 */
    response: ResponseMiddleware;
}

/**
 * Create authentication middleware.
 *
 * @param config - Auth configuration
 * @returns Request and response middleware
 */
export function createAuthMiddleware(config: AuthMiddlewareConfig): AuthMiddleware {
    const {
        getToken,
        onUnauthorized,
        headerName = 'Authorization',
        tokenPrefix = 'Bearer',
        skipPatterns = [],
    } = config;

    const shouldSkip = (url: string): boolean => {
        return skipPatterns.some((pattern) => pattern.test(url));
    };

    const request: RequestMiddleware = async (req: ApiRequest): Promise<ApiRequest> => {
        if (shouldSkip(req.url)) {
            return req;
        }

        const token = await Promise.resolve(getToken());
        if (!token) {
            return req;
        }

        return {
            ...req,
            headers: {
                ...req.headers,
                [headerName]: tokenPrefix ? `${tokenPrefix} ${token}` : token,
            },
        };
    };

    const response: ResponseMiddleware = async (
        res: ApiResponse<unknown>,
        _req: ApiRequest
    ): Promise<ApiResponse<unknown>> => {
        if (res.status === 401 && onUnauthorized) {
            await Promise.resolve(onUnauthorized());
        }
        return res;
    };

    return { request, response };
}

/**
 * Create tenant context middleware.
 *
 * @param getTenantId - Function to get current tenant ID
 * @param headerName - Header name (default: 'X-Tenant-ID')
 * @returns Request middleware
 */
export function createTenantMiddleware(
    getTenantId: () => string | null | Promise<string | null>,
    headerName = 'X-Tenant-ID'
): RequestMiddleware {
    return async (req: ApiRequest): Promise<ApiRequest> => {
        const tenantId = await Promise.resolve(getTenantId());
        if (!tenantId) {
            return req;
        }

        return {
            ...req,
            headers: {
                ...req.headers,
                [headerName]: tenantId,
            },
        };
    };
}

/**
 * Create user context middleware.
 *
 * @param getUserId - Function to get current user ID
 * @param headerName - Header name (default: 'X-User-ID')
 * @returns Request middleware
 */
export function createUserMiddleware(
    getUserId: () => string | null | Promise<string | null>,
    headerName = 'X-User-ID'
): RequestMiddleware {
    return async (req: ApiRequest): Promise<ApiRequest> => {
        const userId = await Promise.resolve(getUserId());
        if (!userId) {
            return req;
        }

        return {
            ...req,
            headers: {
                ...req.headers,
                [headerName]: userId,
            },
        };
    };
}
