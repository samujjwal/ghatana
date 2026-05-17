/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CapabilityRequest } from '../models/CapabilityRequest';
import type { CapabilityResponse } from '../models/CapabilityResponse';
import type { LoginRequest } from '../models/LoginRequest';
import type { LoginResponse } from '../models/LoginResponse';
import type { LogoutResponse } from '../models/LogoutResponse';
import type { RefreshTokenResponse } from '../models/RefreshTokenResponse';
import type { UserInfo } from '../models/UserInfo';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class AuthService {
    /**
     * Authenticate user and establish session (browser)
     * Authenticates a user and establishes a secure httpOnly cookie session.
     * Use this endpoint for browser/web UI authentication. The session cookie is
     * managed by the server and automatically sent with subsequent requests.
     *
     * @param requestBody
     * @returns LoginResponse Authentication successful, session cookie set
     * @throws ApiError
     */
    public static login(
        requestBody: LoginRequest,
    ): CancelablePromise<LoginResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/login',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Refresh session cookie (browser)
     * Refreshes the current session cookie. The session cookie is automatically
     * sent with this request. No request body is required for cookie-based auth.
     *
     * @returns RefreshTokenResponse Session refreshed, new cookie set
     * @throws ApiError
     */
    public static refreshSession(): CancelablePromise<RefreshTokenResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/refresh',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Invalidate the current session (browser)
     * Invalidates the current session. The session cookie is automatically
     * sent with this request. No request body is required for cookie-based auth.
     *
     * @returns LogoutResponse Session invalidated, cookie cleared
     * @throws ApiError
     */
    public static logout(): CancelablePromise<LogoutResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/logout',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Validate the current session (browser)
     * Validates the current session. The session cookie is automatically
     * sent with this request. Returns user and session information if valid.
     *
     * @returns LoginResponse Session is valid
     * @throws ApiError
     */
    public static validateSession(): CancelablePromise<LoginResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/auth/validate',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Return information about the authenticated user
     * @returns UserInfo Current user info
     * @throws ApiError
     */
    public static currentUser(): CancelablePromise<UserInfo> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/auth/me',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Get user capabilities
     * Returns the capabilities available to the current user based on their role and context
     * @param workspaceId Workspace ID to scope capabilities
     * @param projectId Project ID to scope capabilities
     * @returns CapabilityResponse User capabilities
     * @throws ApiError
     */
    public static getCapabilities(
        workspaceId?: string,
        projectId?: string,
    ): CancelablePromise<CapabilityResponse> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/v1/capabilities',
            query: {
                'workspaceId': workspaceId,
                'projectId': projectId,
            },
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Query user capabilities
     * Query capabilities for a specific context with optional correlation ID
     * @param requestBody
     * @returns CapabilityResponse User capabilities
     * @throws ApiError
     */
    public static queryCapabilities(
        requestBody: CapabilityRequest,
    ): CancelablePromise<CapabilityResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/v1/capabilities',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Update authenticated user auth profile
     * @param requestBody
     * @returns any Profile updated
     * @throws ApiError
     */
    public static updateAuthProfile(
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/auth/profile',
            body: requestBody,
            mediaType: 'application/json',
            errors: {
                401: `Authentication required or token invalid`,
            },
        });
    }
    /**
     * Handle SSO callback exchange
     * @param requestBody
     * @returns any SSO callback accepted
     * @throws ApiError
     */
    public static handleSsoCallback(
        requestBody: {
            code?: string;
            state?: string;
        },
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/auth/sso/callback',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Request downgrade of current rate limit tier
     * @returns any Downgrade accepted
     * @throws ApiError
     */
    public static downgradeRateLimitTier(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/rate-limit/downgrade',
        });
    }
    /**
     * Reset user rate limit counters
     * @param requestBody
     * @returns any Reset response
     * @throws ApiError
     */
    public static resetRateLimitCounters(
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/rate-limit/reset',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * Get current rate limit status for identifier
     * @param identifier
     * @returns any Rate limit status
     * @throws ApiError
     */
    public static getRateLimitStatus(
        identifier: string,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/rate-limit/status/{identifier}',
            path: {
                'identifier': identifier,
            },
        });
    }
    /**
     * List available rate limit tiers
     * @returns any Available tiers
     * @throws ApiError
     */
    public static listRateLimitTiers(): CancelablePromise<Array<Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/rate-limit/tiers',
        });
    }
    /**
     * Request upgrade of rate limit tier
     * @param requestBody
     * @returns any Upgrade request created
     * @throws ApiError
     */
    public static requestRateLimitUpgrade(
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/api/rate-limit/upgrade',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * List rate limit upgrade requests
     * @returns any Upgrade requests
     * @throws ApiError
     */
    public static listRateLimitUpgradeRequests(): CancelablePromise<Array<Record<string, any>>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/rate-limit/upgrade-requests',
        });
    }
    /**
     * Get current user profile
     * @returns any User profile
     * @throws ApiError
     */
    public static getUserProfile(): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/api/user/profile',
        });
    }
    /**
     * Update current user profile
     * @param requestBody
     * @returns any Updated user profile
     * @throws ApiError
     */
    public static updateUserProfile(
        requestBody: Record<string, any>,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'PATCH',
            url: '/api/user/profile',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
